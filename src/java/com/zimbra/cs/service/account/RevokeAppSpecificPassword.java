package com.zimbra.cs.service.account;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.auth.twofactor.TwoFactorManager;
import com.zimbra.soap.ZimbraSoapContext;
import com.zimbra.soap.account.message.RevokeAppSpecificPasswordResponse;

public class RevokeAppSpecificPassword extends AccountDocumentHandler {

	@Override
	public Element handle(Element request, Map<String, Object> context)
			throws ServiceException {
		ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Account account = getRequestedAccount(zsc);
        RevokeAppSpecificPasswordResponse response = new RevokeAppSpecificPasswordResponse();
        TwoFactorManager manager = new TwoFactorManager(account);
        String appName = request.getAttribute(AccountConstants.A_APP_NAME);
        manager.revokeAppSpecificPassword(appName);
        return zsc.jaxbToElement(response);
	}
}