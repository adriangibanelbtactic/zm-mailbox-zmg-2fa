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

package com.zimbra.cs.store.file;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.zimbra.common.soap.Element;
import com.zimbra.common.util.CliUtil;
import com.zimbra.cs.account.soap.SoapProvisioning;
import com.zimbra.soap.JaxbUtil;
import com.zimbra.soap.admin.message.DedupeBlobsRequest;
import com.zimbra.soap.admin.message.DedupeBlobsResponse;
import com.zimbra.soap.admin.type.IntIdAttr;
import com.zimbra.soap.admin.type.VolumeIdAndProgress;

public class BlobDeduperUtil {

    private static final String LO_HELP = "help";
    private static final String LO_VERBOSE = "verbose";
    private static final String LO_VOLUMES = "volumes";

    private Options options;
    private boolean verbose = false;
    private List<Short> volumeIds = new ArrayList<Short>();
    private DedupeBlobsRequest.DedupAction action;

    private BlobDeduperUtil() {
        options = new Options();

        options.addOption(new Option("h", LO_HELP, false, "Display this help message."));
        options.addOption(new Option("v", LO_VERBOSE, false, "Display stack trace on error."));

        Option o = new Option(null, LO_VOLUMES, true, "Specify which volumes to dedupe.  If not specified, dedupe all volumes.");
        o.setArgName("volume-ids");
        options.addOption(o);
    }

    private void usage(String errorMsg) {
        int exitStatus = 0;

        if (errorMsg != null) {
            System.err.println(errorMsg);
            exitStatus = 1;
        }
        HelpFormatter format = new HelpFormatter();
        format.printHelp(new PrintWriter(System.err, true), 80,
            "zmdedupe [options] start/status/stop", null, options, 2, 2,
            "\nThe \"start/stop\" command is required, to avoid unintentionally running a blob dedupe.  " +
            "Id values are separated by commas.");
        System.exit(exitStatus);
    }

    private void parseArgs(String[] args)
    throws ParseException {
        GnuParser parser = new GnuParser();
        CommandLine cl = parser.parse(options, args);

        if (CliUtil.hasOption(cl, LO_HELP)) {
            usage(null);
        }
        // Require the "start" command, so that someone doesn't inadvertently
        // kick of a dedupe.
        if (cl.getArgs().length == 0) {
            usage(null);
        } else if  (cl.getArgs()[0].equals("stop")) {
            action = DedupeBlobsRequest.DedupAction.stop;
            return;
        } else if (cl.getArgs()[0].equals("status")) {
            action = DedupeBlobsRequest.DedupAction.status;
            return;
        } else if (cl.getArgs()[0].equals("start")) {
            action = DedupeBlobsRequest.DedupAction.start;
        } else if (cl.getArgs()[0].equals("reset")) {
            if (CliUtil.confirm("This will remove all the metadata used by dedupe process. Continue?")) {
                action = DedupeBlobsRequest.DedupAction.reset;
            } else {
                System.exit(0);
            }
        } else {
            usage(null);
        }

        verbose = CliUtil.hasOption(cl, LO_VERBOSE);

        String volumeList = CliUtil.getOptionValue(cl, LO_VOLUMES);
        if (volumeList != null) {
            for (String id : volumeList.split(",")) {
                try {
                    volumeIds.add(Short.parseShort(id));
                } catch (NumberFormatException e) {
                    usage("Invalid volume id: " + id);
                }
            }
        }
    }

    private void run() throws Exception {
        CliUtil.toolSetup();
        SoapProvisioning prov = SoapProvisioning.getAdminInstance();
        prov.soapZimbraAdminAuthenticate();
        DedupeBlobsRequest request = new DedupeBlobsRequest(action);
        for (short volumeId : volumeIds) {
            request.addVolume(new IntIdAttr(volumeId));
        }
        Element respElem = prov.invoke(JaxbUtil.jaxbToElement(request));
        DedupeBlobsResponse response = JaxbUtil.elementToJaxb(respElem);
        if (action == DedupeBlobsRequest.DedupAction.start) {
            System.out.println("Dedupe scheduled. Run \"zmdedupe status\" to check the status.");
        } else {
            System.out.println("Status = " + response.getStatus().name());
            System.out.println("Total links created = " + response.getTotalCount());
            System.out.println("Total size saved = " + response.getTotalSize());
            VolumeIdAndProgress[] volumeBlobsProgress = response.getVolumeBlobsProgress();
            if (volumeBlobsProgress != null && volumeBlobsProgress.length > 0) {
                System.out.printf("%32s : %10s - %s\n", "Groups populated in volume blobs", "volumeId", "groups/total_groups");
                for (VolumeIdAndProgress idAndProgress : volumeBlobsProgress) {
                    System.out.printf("%32s   %10s - %s\n", "", idAndProgress.getVolumeId(), idAndProgress.getProgress());
                }
            }
            VolumeIdAndProgress[] blobDigestsProgress = response.getBlobDigestsProgress();
            if (blobDigestsProgress != null && blobDigestsProgress.length > 0) {
                System.out.printf("%32s : %10s - %s\n", "Digests Processed", "volumeId", "digests/total_digests");
                for (VolumeIdAndProgress idAndProgress : blobDigestsProgress) {
                    System.out.printf("%32s   %10s - %s\n", "", idAndProgress.getVolumeId(), idAndProgress.getProgress());
                }
            }
        }
    }

    public static void main(String[] args) {
        BlobDeduperUtil app = new BlobDeduperUtil();

        try {
            app.parseArgs(args);
        } catch (ParseException e) {
            app.usage(e.getMessage());
        }

        try {
            app.run();
        } catch (Exception e) {
            if (app.verbose) {
                e.printStackTrace(new PrintWriter(System.err, true));
            } else {
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.toString();
                }
                System.err.println(msg);
            }
            System.exit(1);
        }
    }
}
