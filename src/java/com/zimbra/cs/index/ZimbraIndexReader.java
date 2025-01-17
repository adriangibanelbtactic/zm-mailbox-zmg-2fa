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
package com.zimbra.cs.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.Enumeration;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;

/**
 * Modeled on a subset of {@link org.apache.lucene.index.IndexReader}
 */
public interface ZimbraIndexReader extends Closeable, Cloneable {
    /**
     * Returns the number of documents in this index.
     */
    public int numDocs();

    /**
     * Number of documents marked for deletion but not yet fully removed from the index
     * @return number of deleted documents for this index
     */
    public int numDeletedDocs();

    /**
     * Returns an enumeration of the String representations for values of terms with {@code field} 
     * positioned to start at the first term with a value greater than {@code firstTermValue}.
     * The enumeration is ordered by String.compareTo().
     */
    public TermFieldEnumeration getTermsForField(String field, String firstTermValue) throws IOException;

    public interface TermFieldEnumeration extends Enumeration<BrowseTerm>, Closeable {
    }
}
