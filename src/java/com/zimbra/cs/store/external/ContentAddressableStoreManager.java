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
package com.zimbra.cs.store.external;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.store.Blob;
import com.zimbra.cs.store.StagedBlob;

/**
 * Abstract framework for StoreManager implementations which require content hash or other content-based locator
 * The base implementation here handles the more common cases where blob is cached locally by storeIncoming and then pushed to remote store during stage operation
 */
public abstract class ContentAddressableStoreManager extends ExternalStoreManager {

    @Override
    public String writeStreamToStore(InputStream in, long actualSize,
                    Mailbox mbox) throws IOException, ServiceException {
        //the override of stage below should never allow this code to be reached
        throw ServiceException.FAILURE("anonymous write is not permitted, something went wrong", null);
    }

    /**
     * Generate content hash for the blob using the hash algorithm from the remote store
     * @param blob - Blob which has been constructed locally
     * @return byte[] representing the blob content
     * @throws ServiceException
     * @throws IOException
     */
    public abstract byte[] getHash(Blob blob) throws ServiceException, IOException;

    /**
     * Generate a locator String based on the content of blob
     * @param blob - Blob which has been constructed locally
     * @return String representing the blob content, e.g. hex encoded hash
     * @throws ServiceException
     * @throws IOException
     */
    protected abstract String getLocator(Blob blob) throws ServiceException, IOException;

    /**
     * Return the locator string for the content hash by hex encoding or other similar encoding required by the store
     * @param hash: byte[] containing the content hash
     * @return the locator String
     */
    public abstract String getLocator(byte[] hash);

    /**
     * Write data to blob store using previously generated blob locator
     * @param in: InputStream containing data to be written
     * @param actualSize: size of data in stream, or -1 if size is unknown. To be used by implementor for optimization where possible
     * @param locator string for the blob as returned by getLocator()
     * @param mbox: Mailbox which contains the blob. Can optionally be used by store for partitioning
     * @throws IOException
     * @throws ServiceException
     */
    protected abstract void writeStreamToStore(InputStream in, long actualSize, Mailbox mbox, String locator) throws IOException, ServiceException;

    @Override
    public StagedBlob stage(Blob blob, Mailbox mbox) throws IOException, ServiceException {
        if (supports(StoreFeature.RESUMABLE_UPLOAD) && blob instanceof ExternalUploadedBlob && blob.getRawSize() > 0) {
            ZimbraLog.store.debug("blob already uploaded, just need to commit");
            String locator = ((ExternalResumableUpload) this).finishUpload((ExternalUploadedBlob) blob);
            ZimbraLog.store.debug("staged to locator %s", locator);
            localCache.put(locator, getContent(blob));
            return new ExternalStagedBlob(mbox, blob.getDigest(), blob.getRawSize(), locator);
        } else {
            InputStream is = getContent(blob);
            String locator = getLocator(blob);
            try {
                StagedBlob staged = stage(is, blob.getRawSize(), mbox, locator);
                if (staged != null) {
                    ZimbraLog.store.debug("staged to locator %s", staged.getLocator());
                    localCache.put(staged.getLocator(), getContent(blob));
                }
                return staged;
            } finally {
                ByteUtil.closeStream(is);
            }
        }
    }

    @Override
    public StagedBlob stage(InputStream in, long actualSize, Mailbox mbox) throws ServiceException, IOException {
        Blob blob = storeIncoming(in);
        try {
            return stage(blob, mbox);
        } finally {
            quietDelete(blob);
        }
    }

    protected StagedBlob stage(InputStream in, long actualSize, Mailbox mbox, String locator) throws ServiceException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw ServiceException.FAILURE("SHA-256 digest not found", e);
        }
        ByteUtil.PositionInputStream pin = new ByteUtil.PositionInputStream(new DigestInputStream(in, digest));

        try {
            writeStreamToStore(pin, actualSize, mbox, locator);
            return new ExternalStagedBlob(mbox, ByteUtil.encodeFSSafeBase64(digest.digest()), pin.getPosition(), locator);
        } catch (IOException e) {
            throw ServiceException.FAILURE("unable to stage blob", e);
        }
    }
}
