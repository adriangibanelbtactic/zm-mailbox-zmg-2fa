/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2008, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.index;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Task;
import com.zimbra.cs.mailbox.calendar.Invite;

public final class TaskHit extends CalendarItemHit {

    TaskHit(ZimbraQueryResultsImpl results, Mailbox mbx, int id, Task task, Object sortValue) {
        super(results, mbx, id, task, sortValue);
    }

    public long getDueTime() throws ServiceException {
        Task task = (Task)getCalendarItem();
        return task.getEndTime();
    }

    public int getCompletionPercentage() throws ServiceException {
        Task task = (Task)getCalendarItem();
        Invite inv = task.getDefaultInviteOrNull();
        if (inv != null) {
            String compPerc = inv.getPercentComplete();
            if (compPerc != null) {
                try {
                    int toRet = Integer.parseInt(compPerc);
                    return toRet;
                } catch (Exception e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    static enum Status {
        // these names correspond to the iCal data values.  Don't change!
        // The number corresponds to sort-precidence when sorting by completion status
        NEED(0),
        INPR(1),
        WAITING(2),
        DEFERRED(3),
        COMP(4),
        ;

        Status(int sortVal) { mSortVal = sortVal; }
        public int getSortVal() { return mSortVal; }
        private int mSortVal;
    }

    public Status getStatus() throws ServiceException {
        Task task = (Task)getCalendarItem();
        Invite inv = task.getDefaultInviteOrNull();
        if (inv != null) {
            String status = inv.getStatus();
            try {
                Status s = Status.valueOf(status.toUpperCase());
                return s;
            } catch (IllegalArgumentException e) {
                ZimbraLog.index.debug("Unknown Task Status value: "+status.toUpperCase());
            }
        }
        return Status.DEFERRED;
    }

    @Override
    public Object getSortField(SortBy sort) throws ServiceException {
        switch (sort) {
            case TASK_DUE_ASC:
            case TASK_DUE_DESC:
                return getDueTime();
            case TASK_STATUS_ASC:
            case TASK_STATUS_DESC:
                return getStatus();
            case TASK_PERCENT_COMPLETE_ASC:
            case TASK_PERCENT_COMPLETE_DESC:
                return getCompletionPercentage();
            default:
                return super.getSortField(sort);
        }
    }

    @Override
    int compareTo(SortBy sort, ZimbraHit other) throws ServiceException {
        switch (sort) {
            case TASK_DUE_ASC:
                return compareByDueDate(true, this, other);
            case TASK_DUE_DESC:
                return compareByDueDate(false, this, other);
            case TASK_STATUS_ASC:
                return compareByStatus(true, this, other);
            case TASK_STATUS_DESC:
                return compareByStatus(false, this, other);
            case TASK_PERCENT_COMPLETE_ASC:
                return compareByCompletionPercent(true, this, other);
            case TASK_PERCENT_COMPLETE_DESC:
                return compareByCompletionPercent(false, this, other);
            default:
                return super.compareTo(sort, other);
        }
    }

    private static long getDueTime(ZimbraHit zh) throws ServiceException {
        if (zh instanceof ProxiedHit)
            return ((ProxiedHit)zh).getElement().getAttributeLong(MailConstants.A_TASK_DUE_DATE);
        else
            return ((TaskHit)zh).getDueTime();
    }

    private static Status getStatus(ZimbraHit zh) throws ServiceException {
        if (zh instanceof ProxiedHit) {
            String s = ((ProxiedHit)zh).getElement().getAttribute(MailConstants.A_CAL_STATUS);
            return Status.valueOf(s);
        } else {
            return ((TaskHit)zh).getStatus();
        }
    }

    static int getCompletionPercentage(ZimbraHit zh) throws ServiceException {
        if (zh instanceof ProxiedHit)
            return (int)(((ProxiedHit)zh).getElement().getAttributeLong(MailConstants.A_TASK_PERCENT_COMPLETE));
        else
            return ((TaskHit)zh).getCompletionPercentage();
    }

    static final int compareByDueDate(boolean ascending, ZimbraHit lhs, ZimbraHit rhs) {
        int retVal = 0;
        try {
            long left = getDueTime(lhs);
            long right = getDueTime(rhs);
            long result = right - left;
            if (result > 0)
                retVal = 1;
            else if (result < 0)
                retVal = -1;
            else
                retVal = 0;
        } catch (ServiceException e) {
            ZimbraLog.index.info("Caught ServiceException trying to compare TaskHit %s to TaskHit %s",
                lhs, rhs, e);
        }
        if (ascending)
            return -1 * retVal;
        else
            return retVal;
    }

    static final int compareByStatus(boolean ascending, ZimbraHit lhs, ZimbraHit rhs) {
        int retVal = 0;
        try {
            Status left = getStatus(lhs);
            Status right = getStatus(rhs);
            int result = right.getSortVal() - left.getSortVal();
            if (result > 0)
                retVal = 1;
            else if (result < 0)
                retVal = -1;
            else
                retVal = 0;
        } catch (ServiceException e) {
            ZimbraLog.index.info("Caught ServiceException trying to compare TaskHit %s to TaskHit %s",
                lhs, rhs, e);
        }
        if (ascending)
            return -1 * retVal;
        else
            return retVal;
    }

    static final int compareByCompletionPercent(boolean ascending, ZimbraHit lhs, ZimbraHit rhs) {
        int retVal = 0;
        try {
            int left = getCompletionPercentage(lhs);
            int right = getCompletionPercentage(rhs);
            int result = right - left;
            if (result > 0)
                retVal = 1;
            else if (result < 0)
                retVal = -1;
            else
                retVal = 0;
        } catch (ServiceException e) {
            ZimbraLog.index.info("Caught ServiceException trying to compare TaskHit %s to TaskHit %s",
                lhs, rhs, e);
        }
        if (ascending)
            return -1 * retVal;
        else
            return retVal;
    }
}
