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
package com.zimbra.cs.octosync.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.octosync.PatchManifest;
import com.zimbra.cs.octosync.store.BlobStore.StoredBlob;
import com.zimbra.cs.store.IncomingBlob;

/**
 * Encapsulates functionality for storing and retrieving patches and their manifests.
 *
 * @author grzes
 */
public class PatchStore
{
    private BlobStore blobStore;

    /**
     * Represents incoming patch.
     */
    public class IncomingPatch
    {
        private IncomingBlob patchBlob;
        private String accountId;
        private PatchManifest manifest;

        /**
         * Gets the account id.
         *
         * @return the account id
         */
        public String getAccountId()
        {
            return accountId;
        }

        /**
         * Instantiates a new incoming patch.
         *
         * @param blob the blob
         * @param accountId the account id
         */
        public IncomingPatch(IncomingBlob blob, String accountId)
        {
            this.patchBlob = blob;
            this.accountId = accountId;
            this.manifest = new PatchManifest();
        }

        public PatchManifest getManifest()
        {
            return manifest;
        }

        public OutputStream getOutputStream() throws IOException
        {
            return patchBlob.getAppendingOutputStream();
        }

        public InputStream getInputStream() throws IOException
        {
            return patchBlob.getInputStream();
        }
    }

    /**
     * Represents a stored patch.
     */
    public class StoredPatch
    {

        /** The patch blob. */
        private StoredBlob patchBlob;

        /** The manifest blob. */
        private StoredBlob manifestBlob;

        /** The account id. */
        private String accountId;

        /**
         * Gets the account id.
         *
         * @return the account id
         */
        public String getAccountId()
        {
            return accountId;
        }

        private StoredPatch(StoredBlob patchBlob, StoredBlob manifestBlob, String accountId)
        {
            this.patchBlob = patchBlob;
            this.manifestBlob = manifestBlob;
            this.accountId = accountId;
        }

        /**
         * Gets the input stream.
         *
         * @return the input stream
         * @throws IOException Signals that an I/O exception has occurred.
         */
        public InputStream getInputStream() throws IOException
        {
            return patchBlob.getInputStream();
        }

        /**
         * Gets the manifest input stream.
         *
         * @return the manifest input stream
         * @throws IOException Signals that an I/O exception has occurred.
         */
        public InputStream getManifestInputStream() throws IOException
        {
            return manifestBlob.getInputStream();
        }

        /**
         * Returns the patch size (uncompressed)
         *
         * @return patch size
         */
        public long getPatchSize()
        {
            return patchBlob.getSize();
        }

        /**
         * Returns the patch manifest size (uncompressed)
         *
         * @return manifest size
         */
        public long getManifestSize()
        {
            return manifestBlob.getSize();
        }
    }


    /**
     * Instantiates a new patch store.
     *
     * @param blobStore the blob store
     */
    public PatchStore(BlobStore blobStore)
    {
        this.blobStore = blobStore;
    }

    /**
     * Creates the incoming patch.
     *
     * @param accountId The if of the account to associate the patch with.
     *
     * @return IncomingPatch instance.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ServiceException the service exception
     */
    public IncomingPatch createIncomingPatch(String accountId) throws IOException, ServiceException
    {
        IncomingBlob ib = blobStore.createIncoming(null);

        IncomingPatch ip = new IncomingPatch(ib, accountId);
        ib.setContext(ip);

        return ip;
    }

    /**
     * Retrieves the incoming patch.
     *
     * @param resumeId The resume id of the patch.
     *
     * @return IncomingPatch
     */
    public IncomingPatch getIncomingPatch(String resumeId)
    {
        return (IncomingPatch)blobStore.getIncoming(resumeId).getContext();
    }

    /**
     * Accepts patch. Turns an IncomingPatch into a StoredPatch.
     *
     * @param ip The IncomingPatch instance to accept.
     * @param fileId The id of the file the patch creates.
     * @param version the version The version number of the file the patch creates.
     * @param manifest The patch manifest.
     *
     * @return StoredPatch instance.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ServiceException the service exception
     */
    public StoredPatch acceptPatch(IncomingPatch ip, int fileId, int version)
        throws IOException, ServiceException
    {
        if (version > 1) {
            deletePatch(ip.getAccountId(), fileId);
        }

        IncomingBlob manifestBlob = blobStore.createIncoming(null);

        try {
            ip.getManifest().writeTo(manifestBlob.getAppendingOutputStream());
        } catch (IOException e) {
            blobStore.deleteIncoming(manifestBlob);
            throw e;
        }

        // ok, we got manifest written to a separate incoming blob here, now let's store both

        StoredBlob psb = blobStore.store(ip.patchBlob, getStoredPatchId(ip.getAccountId(), fileId, false), version);

        StoredBlob msb = null;

        try {
            msb = blobStore.store(manifestBlob, getStoredPatchId(ip.getAccountId(), fileId, true), version);
        } catch (IOException e) {
            blobStore.delete(psb, version);
            throw e;
        }

        StoredPatch sp = new StoredPatch(psb, msb, ip.getAccountId());
        psb.setContext(sp);

        return sp;
    }
    /**
     * Reject patch.
     *
     * @param ip the ip
     */
    public void rejectPatch(IncomingPatch ip)
    {
        blobStore.deleteIncoming(ip.patchBlob);
    }

    /**
     * Lookup patch.
     *
     * @param accountId the account id
     * @param fileId the file id
     * @param version the version
     * @return the stored patch
     */
    public StoredPatch lookupPatch(String accountId, int fileId, int version)
    {
        StoredBlob psb = blobStore.get(getStoredPatchId(accountId, fileId, false), version);

        if (psb == null) {
            return null;
        }

        return (StoredPatch)psb.getContext();
    }

    /**
     * Delete patch.
     *
     * @param accountId the account id
     * @param fileId the file id
     */
    public void deletePatch(String accountId, int fileId)
    {
        try {
            StoredBlob psb = blobStore.get(getStoredPatchId(accountId, fileId, false));
            blobStore.delete(psb);
        } finally {
            StoredBlob msb = blobStore.get(getStoredPatchId(accountId, fileId, true));
            blobStore.delete(msb);
        }
    }

    private String getStoredPatchId(String accountId, int fileId, boolean manifest)
    {
        return accountId + ':' + fileId + (manifest ? 'M' : 'P');
    }

}
