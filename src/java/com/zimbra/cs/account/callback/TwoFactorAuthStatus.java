package com.zimbra.cs.account.callback;

import java.util.Map;

import com.zimbra.common.account.ProvisioningConstants;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Cos;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;

public class TwoFactorAuthStatus extends AttributeCallback {

    @Override
    public void preModify(CallbackContext context, String attrName, Object attrValue, Map attrsToModify, Entry entry) throws ServiceException {
        boolean setting = ((String) attrValue).equalsIgnoreCase("true");
        if (entry instanceof Account) {
            Account account = (Account) entry;
            if (attrName.equals(Provisioning.A_zimbraFeatureTwoFactorAuthRequired) && !is2faAvailable(account, attrsToModify)
                    && setting) {
                throw ServiceException.FAILURE("cannot make two-factor auth required because is not available on this account", null);
            } else if (attrName.equals(Provisioning.A_zimbraFeatureTwoFactorAuthAvailable) && is2faRequired(account, attrsToModify)
                    && !setting) {
                throw ServiceException.FAILURE("cannot make two-factor auth unavailable because it is currently required on the account", null);
            } else if (attrName.equals(Provisioning.A_zimbraTwoFactorAuthEnabled) && !is2faAvailable(account, attrsToModify)
                    && setting) {
                throw ServiceException.FAILURE("cannot enable two-factor auth because it is not available on this account", null);
            }
        } else if (entry instanceof Cos) {
            Cos cos = (Cos) entry;
            if (attrName.equals(Provisioning.A_zimbraFeatureTwoFactorAuthRequired) && !is2faAvailable(cos, attrsToModify)
                    && setting) {
                throw ServiceException.FAILURE("cannot make two-factor auth required because it is not available on this COS", null);
            } else if (attrName.equals(Provisioning.A_zimbraFeatureTwoFactorAuthAvailable) && is2faRequired(cos, attrsToModify)
                    && !setting) {
                throw ServiceException.FAILURE("cannot make two-factor auth unavailable because it is currently required on the COS", null);
            } else if (attrName.equals(Provisioning.A_zimbraFeatureTwoFactorAuthAvailable) && setting) {
                cos.unsetTwoFactorAuthLastReset();
            }
        }
    }

    private boolean setting2faAttr(Map attrs, String attr, String value) {
        String attrValue = (String) attrs.get(Provisioning.A_zimbraFeatureTwoFactorAuthAvailable);
        return attrValue != null ? attrValue.equals(value) : false;
    }

    private boolean is2faAvailable(Account account, Map attrs) {
        boolean alreadyAvailable = account.isFeatureTwoFactorAuthAvailable();
        boolean settingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthAvailable, ProvisioningConstants.TRUE);
        boolean unsettingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthAvailable, ProvisioningConstants.FALSE);
        return alreadyAvailable ? !unsettingNow : settingNow;
    }

    private boolean is2faAvailable(Cos cos, Map attrs) {
        boolean alreadyAvailable = cos.isFeatureTwoFactorAuthAvailable();
        boolean settingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthAvailable, ProvisioningConstants.TRUE);
        boolean unsettingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthAvailable, ProvisioningConstants.FALSE);
        return alreadyAvailable ? !unsettingNow : settingNow;
    }

    private boolean is2faRequired(Account account, Map attrs) {
        boolean alreadyRequired = account.isFeatureTwoFactorAuthRequired();
        boolean settingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthRequired, ProvisioningConstants.TRUE);
        boolean unsettingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthRequired, ProvisioningConstants.FALSE);
        return alreadyRequired ? !unsettingNow : settingNow;
    }

    private boolean is2faRequired(Cos cos, Map attrs) {
        boolean alreadyRequired = cos.isFeatureTwoFactorAuthRequired();
        boolean settingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthRequired, ProvisioningConstants.TRUE);
        boolean unsettingNow = setting2faAttr(attrs, Provisioning.A_zimbraFeatureTwoFactorAuthRequired, ProvisioningConstants.FALSE);
        return alreadyRequired ? !unsettingNow : settingNow;
    }

    @Override
    public void postModify(CallbackContext context, String attrName, Entry entry) {}

}
