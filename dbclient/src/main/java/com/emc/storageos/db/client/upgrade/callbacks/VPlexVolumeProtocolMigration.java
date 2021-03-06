/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Migration handler to set protocol for VPLEX volumes to FC.
 */
public class VPlexVolumeProtocolMigration extends BaseCustomMigrationCallback {

    @Override
    public void process() {
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);
        Iterator<Volume> volumesIter = dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumesIter.hasNext()) {
            Volume volume = volumesIter.next();
            URI systemURI = volume.getStorageController();
            if (!NullColumnValueGetter.isNullURI(systemURI)) {
                StorageSystem system = dbClient.queryObject(StorageSystem.class, systemURI);
                if ((system != null) &&
                    (DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType()))) {
                    // This is a VPLEX volume. If not already set,
                    // set the protocols to FC.
                    StringSet protocols = volume.getProtocol();
                    if (protocols == null) {
                        protocols = new StringSet();
                        protocols.add(StorageProtocol.Block.FC.name());
                        volume.setProtocol(protocols);
                        dbClient.persistObject(volume);
                    }
                }
            }
        }
    }
}
