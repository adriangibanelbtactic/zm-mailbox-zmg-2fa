/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;

public final class RawIndexEditor {

    private final Directory luceneDirectory;

    RawIndexEditor(String path) throws IOException {
        luceneDirectory = LuceneDirectory.open(new File(path));
    }

    public static String Format(String s, int len) {
        StringBuffer toRet = new StringBuffer(len+1);
        int curOff = 0;
        if (s.length() < len) {
            for (curOff = 0; curOff < (len-s.length()); curOff++) {
                toRet.append(" ");
            }
        }

        int sOff = 0;
        for (; curOff < len; curOff++) {
            toRet.append(s.charAt(sOff));
            sOff++;
        }
        toRet.append("  ");
        return toRet.toString();
    }

    public boolean dumpDocument(Document doc, boolean isDeleted) {
        if (isDeleted) {
            System.out.print("DELETED ");
        }
        String subject = doc.get(LuceneFields.L_H_SUBJECT);
        if (subject == null) {
            subject = "MISSING_SUBJECT";
        }
        String blobId = doc.get(LuceneFields.L_MAILBOX_BLOB_ID);
        if (blobId == null) {
            blobId = "MISSING";
        }
        String part = doc.get(LuceneFields.L_PARTNAME);
        if (part == null) {
            part = "NULL_PART";
        }

        String dateStr = doc.get(LuceneFields.L_SORT_DATE);
        if (dateStr == null) {
            dateStr = "";
        } else {
            try {
                dateStr = DateTools.stringToDate(dateStr).toString();
            } catch (ParseException e) {
                assert false;
            }
        }
        String sizeStr = doc.get(LuceneFields.L_SORT_SIZE);
        if (sizeStr == null) {
            sizeStr = "";
        }

        System.out.println(Format(blobId, 10) + Format(dateStr, 30) +
                Format(part, 10) + Format(sizeStr, 10) + "\"" + subject + "\"");

        return true;
    }


    void dumpAll() throws IOException {
        IndexReader reader = IndexReader.open(luceneDirectory);
        try {
            int maxDoc = reader.maxDoc();
            System.out.println("There are "+maxDoc+" documents in this index.");

            for (int i = 0; i < maxDoc; i++) {
                dumpDocument(reader.document(i), reader.isDeleted(i));
            }
        } finally {
            reader.close();
        }
    }

    void run() throws IOException {
        dumpAll();
    }

    public static void main(String[] args) {
        String idxParentDir = args[0];

        try {
            RawIndexEditor editor = new RawIndexEditor(idxParentDir);
            editor.run();
        } catch (IOException e) {
            System.err.println("Caught IOException "+e);
        }

    }

}
