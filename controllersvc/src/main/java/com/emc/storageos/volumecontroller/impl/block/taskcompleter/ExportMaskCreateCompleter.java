/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportMaskCreateCompleter extends ExportMaskInitiatorCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskCreateCompleter.class);

    private List<URI> _initiatorURIs;
    private Map<URI, Integer> _volumeMap;

    public ExportMaskCreateCompleter(URI egUri, URI emUri, List<URI> initiators,
                                     Map<URI, Integer> volumes, String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _initiatorURIs = new ArrayList<URI>();
        _initiatorURIs.addAll(initiators);
        _volumeMap = new HashMap<URI, Integer>();
        _volumeMap.putAll(volumes);
    }

    public void removeInitiators(List<URI> initiators) {
        _initiatorURIs.removeAll(initiators);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
                         ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            if (exportMask != null && status == Operation.Status.ready) {
                List<Initiator> initiators = dbClient.queryObject(Initiator.class,
                        _initiatorURIs);
                exportMask.addInitiators(initiators);
                
                StorageSystem system = dbClient.queryObject(StorageSystem.class, exportMask.getStorageDevice());

                // Add only the initiators that we created and added.  Currently only VMAX will fill in these initiators.
    			if (getUserAddedInitiatorURIs() != null && !getUserAddedInitiatorURIs().isEmpty()) { 
    				exportMask.addToUserCreatedInitiators(dbClient.queryObject(Initiator.class, getUserAddedInitiatorURIs()));
                } else if (!Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {
    				// Fall-back, add all initiators.  Currently only VMAX cherry-picks specific initiators that
    				// are user-added based on the initiators that it found on the array.  Other arrays need to 
    				// fall back to the original way of tracking user added initiators, which is to add all of them.
                	exportMask.addToUserCreatedInitiators(initiators);
                }
                
                exportMask.addVolumes(_volumeMap);
                if (Type.xtremio.toString().equalsIgnoreCase(system.getSystemType())) {
                    // XtremIO case, wwn's are updated only during export.
                    // Clean up the existing volumes in export Mask which will have dummy wwns after provisioning.
                    // The code below this method addToUserCreatedVolumes adds back the volumes with right wwns.
                    _log.info("Cleaning existing xtremio volumes with dummy wwns");
                    exportMask.getUserAddedVolumes().clear();
                }
                
                for (URI boURI : _volumeMap.keySet()) {
                    BlockObject blockObject = BlockObject.fetch(dbClient, boURI);
                    exportMask.addToUserCreatedVolumes(blockObject);
                }
                dbClient.updateAndReindexObject(exportMask);
                exportGroup.addExportMask(exportMask.getId());
                dbClient.updateAndReindexObject(exportGroup);
            }

            _log.info(String.format(
                    "Done ExportMaskCreate - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));


        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskCreate - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);

        } finally {
            super.complete(dbClient, status,coded);
        }
    }

}
