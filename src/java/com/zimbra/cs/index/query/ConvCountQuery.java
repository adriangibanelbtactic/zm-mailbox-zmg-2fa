/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2011, 2012, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.index.query;

import com.google.common.base.Strings;
import com.zimbra.cs.index.DBQueryOperation;
import com.zimbra.cs.index.QueryOperation;
import com.zimbra.cs.mailbox.Mailbox;

/**
 * Query by conversation count.
 *
 * @author tim
 * @author ysasaki
 */
public final class ConvCountQuery extends Query {
    private int lowestCount;
    private boolean lowerEq;
    private int highestCount;
    private boolean higherEq;

    private ConvCountQuery(int lowestCount, boolean lowerEq, int highestCount, boolean higherEq) {
        this.lowestCount = lowestCount;
        this.lowerEq = lowerEq;
        this.highestCount = highestCount;
        this.higherEq = higherEq;
    }

    @Override
    public void dump(StringBuilder out) {
        out.append("ConvCount:");
        out.append(lowerEq ? ">=" : ">");
        out.append(lowestCount);
        out.append(' ');
        out.append(higherEq? "<=" : "<");
        out.append(highestCount);
    }
    
    @Override
    void sanitizedDump(StringBuilder out) {
        out.append("ConvCount:");
        out.append(lowerEq ? ">=" : ">");
        out.append("$NUM");
        out.append(' ');
        out.append(higherEq? "<=" : "<");
        out.append("$NUM");
    }

    @Override
    public boolean hasTextOperation() {
        return false;
    }

    @Override
    public QueryOperation compile(Mailbox mbox, boolean bool) {
        DBQueryOperation op = new DBQueryOperation();
        op.addConvCountRange(lowestCount, lowerEq, highestCount, higherEq, evalBool(bool));
        return op;
    }

    public static Query create(String term) {
        if (term.charAt(0) == '<') {
            boolean eq = false;
            if (term.charAt(1) == '=') {
                eq = true;
                term = term.substring(2);
            } else {
                term = term.substring(1);
            }
            int num = Integer.parseInt(term);
            return new ConvCountQuery(-1, false, num, eq);
        } else if (term.charAt(0) == '>') {
            boolean eq = false;
            if (term.charAt(1) == '=') {
                eq = true;
                term = term.substring(2);
            } else {
                term = term.substring(1);
            }
            int num = Integer.parseInt(term);
            return new ConvCountQuery(num, eq, -1, false);
        } else {
            int num = Integer.parseInt(term);
            return new ConvCountQuery(num, true, num, true);
        }
    }
}
