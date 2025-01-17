/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2012, 2013, 2014 Zimbra, Inc.
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

package com.zimbra.cs.imap;

import java.util.List;

import com.zimbra.cs.imap.AppendMessage.Part;

public class AppendCommand extends ImapCommand {
    private ImapPath path;
    private List<AppendMessage> appends;

    /**
     * @param path
     * @param appends
     */
    public AppendCommand(ImapPath path, List<AppendMessage> appends) {
        super();
        this.path = path;
        this.appends = appends;
    }

    @Override
    protected boolean hasSameParams(ImapCommand command) {
        if (this.equals(command)) {
            return true;
        }
        AppendCommand other = (AppendCommand) command;
        if (path != null && path.equals(other.path)) {
            if (appends != null) {
                if (other.appends == null || appends.size() != other.appends.size()) {
                    return false;
                }

                // both have appends, both same size
                for (int i = 0; i < appends.size(); i++) {
                    AppendMessage myMsg = appends.get(i);
                    AppendMessage otherMsg = other.appends.get(i);
                    if ((myMsg.getDate() != null && !myMsg.getDate().equals(otherMsg.getDate()))
                            || (myMsg.getDate() == null && otherMsg.getDate() != null)) {
                        return false;
                        // date mismatch
                    } else if ((myMsg.getPersistentFlagNames() != null && !myMsg.getPersistentFlagNames().equals(
                            otherMsg.getPersistentFlagNames()))
                            || (myMsg.getPersistentFlagNames() == null && otherMsg.getPersistentFlagNames() != null)) {
                        return false;
                        // flag name mismatch
                    }
                    List<Part> myParts = myMsg.getParts();
                    List<Part> otherParts = otherMsg.getParts();
                    if ((myParts == null && otherParts != null) || (myParts != null && otherParts == null)
                            || (myParts.size() != otherParts.size())) {
                        return false;
                    }
                    for (int j = 0; j < myParts.size(); j++) {
                        Part myPart = myParts.get(j);
                        Part otherPart = otherParts.get(j);

                        if ((myPart.getLiteral() != null && otherPart.getLiteral() != null && myPart.getLiteral()
                                .size() != otherPart.getLiteral().size())
                                || (myPart.getLiteral() == null && otherPart.getLiteral() != null)
                                || (myPart.getLiteral() != null && otherPart.getLiteral() == null)) {
                            return false;
                            // just checking literal size here; can't check blob
                            // content since it is streamed and not kept in heap
                            // this is good enough for now; if a client is
                            // flooding with bunches of same-length blobs we'll
                            // block them
                        }
                        if (myPart.getUrl() != null && !myPart.getUrl().equals(otherPart.getUrl())
                                || (myPart.getUrl() == null && otherPart.getUrl() != null)) {
                            return false;
                        }
                    }
                }
                return true; // all appends have same size, date, flags; URL
                             // same or Literals with same size
            } else {
                return other.appends != null;
            }
        } else {
            return false;
        }
    }

}
