/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2012, 2013, 2014 Zimbra, Inc.
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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.index.LuceneQueryOperation;
import com.zimbra.cs.index.NoTermQueryOperation;
import com.zimbra.cs.index.QueryOperation;
import com.zimbra.cs.index.analysis.AddrCharTokenizer;
import com.zimbra.cs.index.analysis.ContactTokenFilter;
import com.zimbra.cs.index.analysis.HalfwidthKanaVoicedMappingFilter;
import com.zimbra.cs.mailbox.Mailbox;

/**
 * Special text query to search contacts.
 *
 * @author ysasaki
 */
public final class ContactQuery extends Query {
    private final List<String> tokens = new ArrayList<String>();

    public ContactQuery(String text) {
        TokenStream stream = new ContactTokenFilter(new AddrCharTokenizer(new HalfwidthKanaVoicedMappingFilter(new StringReader(text))));
        CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
        try {
            stream.reset();
            while (stream.incrementToken()) {
                tokens.add(CharMatcher.is('*').trimTrailingFrom(termAttr)); // remove trailing wildcard characters
            }
            stream.end();
            stream.close();
        } catch (IOException e) { // should never happen
            ZimbraLog.search.error("Failed to tokenize text=%s", text);
        }
    }

    @Override
    public boolean hasTextOperation() {
        return true;
    }

    @Override
    public QueryOperation compile(Mailbox mbox, boolean bool) throws ServiceException {
        switch (tokens.size()) {
            case 0:
                return new NoTermQueryOperation();
            case 1: {
                LuceneQueryOperation op = new LuceneQueryOperation();
                PrefixQuery query = new PrefixQuery(new Term(LuceneFields.L_CONTACT_DATA, tokens.get(0)));
                op.addClause("contact:" +  tokens.get(0), query, evalBool(bool));
                return op;
            }
            default: {
                LuceneQueryOperation op = new LuceneQueryOperation();
                LuceneQueryOperation.LazyMultiPhraseQuery query = new LuceneQueryOperation.LazyMultiPhraseQuery();
                for (String token : tokens) {
                    query.expand(new Term(LuceneFields.L_CONTACT_DATA, token)); // expand later
                }
                op.addClause("contact:\"" + Joiner.on(' ').join(tokens) + "\"", query, evalBool(bool));
                return op;
            }
        }
    }

    @Override
    void dump(StringBuilder out) {
        out.append("CONTACT:");
        Joiner.on(',').appendTo(out, tokens);
    }
    
    @Override
    void sanitizedDump(StringBuilder out) {
        out.append("CONTACT:");
        out.append(Strings.repeat("$TEXT,", tokens.size()));
        if (out.charAt(out.length()-1) == ',') {
            out.deleteCharAt(out.length()-1);
        }
    }

}
