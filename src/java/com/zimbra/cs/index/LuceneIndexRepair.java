/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2011, 2013, 2014 Zimbra, Inc.
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
import java.util.Collections;
import java.util.Date;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ChecksumIndexInput;
import org.apache.lucene.store.ChecksumIndexOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BitVector;

/**
 * Try to fix a corrupted Lucene index data.
 * <p>
 * Once corruption is detected, re-indexing is the only way to recover
 * correctness. But, since re-indexing is very expensive, we do our best to
 * repair the index data with compromising some level of correctness.
 * <p>
 * Make sure there is absolutely no IndexReader or IndexWriter opening the index
 * data during the repair.
 *
 * @author ysasaki
 */
final class LuceneIndexRepair {
    // need to sync with SegmentInfos#CURRENT_FORMAT
    private static final int FORMAT = SegmentInfos.FORMAT_3_1;
    // need to sync with IndexFileNames#SEGMENTS_GEN
    private static final String SEGMENTS_GEN = "segments.gen";
    // need to sync with IndexFileNames#SEGMENTS
    private static final String SEGMENTS = "segments";
    // need to sync with IndexFileNames#DELETES_EXTENSION
    private static final String DELETES_EXTENSION = "del";

    private final Directory directory;
    private int repaired = 0;

    /**
     * Constructs a new {@link LuceneIndexRepair}.
     *
     * @param dir index data to repair
     */
    LuceneIndexRepair(Directory dir) {
        directory = dir;
    }

    /**
     * Repair the index data.
     *
     * @return number of repairs conducted, or 0 if nothing was repaired
     * @throws IOException error on accessing the index data
     */
    int repair() throws IOException {
        String segsFilename = SegmentInfos.getCurrentSegmentFileName(directory);
        long gen = SegmentInfos.generationFromSegmentsFileName(segsFilename);
        String nextSegsFilename = getSegmentsFilename(++gen);

        ChecksumIndexInput input = new ChecksumIndexInput(
                directory.openInput(segsFilename));
        try {
            ChecksumIndexOutput output = new ChecksumIndexOutput(
                    directory.createOutput(nextSegsFilename));
            try {
                convert(input, output);
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }

        if (repaired == 0) {
            directory.deleteFile(nextSegsFilename);
            return repaired;
        }

        directory.sync(Collections.singleton(nextSegsFilename));
        try {
            commit(gen);
        } catch (IOException e) {
            directory.deleteFile(nextSegsFilename);
            throw e;
        }

        String backupFilename = "REPAIR_" +
            DateTools.dateToString(new Date(), DateTools.Resolution.SECOND) +
            "." + segsFilename;
        rename(segsFilename, backupFilename);
        return repaired;
    }

    private void commit(long gen) throws IOException {
        IndexOutput output = directory.createOutput(SEGMENTS_GEN);
        try {
            output.writeInt(SegmentInfos.FORMAT_LOCKLESS);
            output.writeLong(gen);
            output.writeLong(gen);
        } finally {
            output.close();
        }
        directory.sync(Collections.singleton(SEGMENTS_GEN));
    }

    private void convert(ChecksumIndexInput input, ChecksumIndexOutput output) throws IOException {
        int format = input.readInt();
        if (format < 0) {
            if (format < FORMAT) {
                throw new CorruptIndexException("Unknown format version: " + format);
            } else {
                output.writeInt(format);
                long version = input.readLong();
                version++; // increment
                output.writeLong(version);
                output.writeInt(input.readInt()); // counter
            }
        } else { // file is in old format without explicit format info
            output.writeInt(format);
        }

        int num = input.readInt();
        output.writeInt(num);
        for (int i = 0; i < num; i++) {
            if (format <= SegmentInfos.FORMAT_3_1) {
                output.writeString(input.readString()); // version
            }
            String name = input.readString();
            output.writeString(name);
            int count = input.readInt();
            output.writeInt(count);
            long delGen = -1;
            if (format <= SegmentInfos.FORMAT_LOCKLESS) {
                delGen = input.readLong();
                output.writeLong(delGen);
                if (format <= SegmentInfos.FORMAT_SHARED_DOC_STORE) {
                    int docStoreOffset = input.readInt();
                    output.writeInt(docStoreOffset);
                    if (docStoreOffset != -1) {
                        output.writeString(input.readString()); // docStoreSegment
                        output.writeByte(input.readByte()); // docStoreIsCompoundFile
                    }
                }
            }
            if (format <= SegmentInfos.FORMAT_SINGLE_NORM_FILE) {
                 output.writeByte(input.readByte()); // hasSingleNormFile
            }
            int numNormGen = input.readInt();
            output.writeInt(numNormGen);
            if (numNormGen > 0) {
                for (int j = 0; j < numNormGen; j++) {
                    output.writeLong(input.readLong()); // normGen
                }
            }
            output.writeByte(input.readByte()); // isCompoundFile
            if (format <= SegmentInfos.FORMAT_DEL_COUNT) {
                int delCount = input.readInt();
                if (delCount <= count && delCount == getDelCount(name, delGen)) {
                    output.writeInt(delCount);
                } else { // del count mismatch
                    // https://issues.apache.org/jira/browse/LUCENE-1474
                    repaired++;
                    output.writeInt(-1); // clear
                }
            }
            if (format <= SegmentInfos.FORMAT_HAS_PROX) {
                output.writeByte(input.readByte()); // hasProx
            }
            if (format <= SegmentInfos.FORMAT_DIAGNOSTICS) {
                output.writeStringStringMap(input.readStringStringMap()); // diagnostics
            }
            if (format <= SegmentInfos.FORMAT_HAS_VECTORS) {
                output.writeByte(input.readByte()); // hasVectors
            }
        }

        if (format >= 0){ // in old format the version number may be at the end of the file
            if (input.getFilePointer() < input.length()) {
                output.writeLong(input.readLong()); // version
            }
        }

        if (format <= SegmentInfos.FORMAT_USER_DATA) {
            if (format <= SegmentInfos.FORMAT_DIAGNOSTICS) {
                output.writeStringStringMap(input.readStringStringMap()); // user data
            } else {
                output.writeByte(input.readByte());
                output.writeString(input.readString()); // user data
            }
        }

        if (format <= SegmentInfos.FORMAT_CHECKSUM) {
            final long checksumNow = input.getChecksum();
            final long checksumThen = input.readLong();
            if (checksumNow != checksumThen) {
                throw new CorruptIndexException("checksum mismatch in segments file");
            }
        }

        output.finishCommit();
    }

    private int getDelCount(String name, long gen) throws IOException {
        if (gen < 0) { // no del file
            return 0;
        } else if (gen == 0) { // no gen
            String filename = name + "." + DELETES_EXTENSION;
            if (directory.fileExists(filename)) {
                BitVector del = new BitVector(directory, filename);
                return del.count();
            } else {
                return 0;
            }
        } else {
            String filename = name + "_" + Long.toString(gen, Character.MAX_RADIX) +
                "." + DELETES_EXTENSION;
            if (directory.fileExists(filename)) {
                BitVector del = new BitVector(directory, filename);
                return del.count();
            } else {
                return 0;
            }
        }
    }

    private String getSegmentsFilename(long gen) {
        return SEGMENTS + "_" + Long.toString(gen, Character.MAX_RADIX);
    }

    private void rename(String from, String to) {
        File dir;
        if (directory instanceof LuceneDirectory) {
            dir = ((LuceneDirectory) directory).getDirectory();
        } else if (directory instanceof FSDirectory) {
            dir = ((FSDirectory) directory).getDirectory();
        } else {
            return;
        }
        File renameFrom = new File(dir, from);
        File renameTo = new File(dir, to);
        if (renameTo.exists()) {
            renameTo.delete();
        }
        renameFrom.renameTo(renameTo);
    }

}
