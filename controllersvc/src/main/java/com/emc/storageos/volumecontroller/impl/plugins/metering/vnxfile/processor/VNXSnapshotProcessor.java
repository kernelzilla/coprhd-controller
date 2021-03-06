/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;
import com.emc.nas.vnxfile.xmlapi.Checkpoint;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;

/**
 * SnapshotProcessor is responsible to process the result received from XML API
 * Server during snapshot stream processing.
 */
public class VNXSnapshotProcessor extends VNXFileProcessor {

    /**
     * Logger instance.
     */
    private final Logger _logger = LoggerFactory
            .getLogger(VNXSnapshotProcessor.class);
    
    @SuppressWarnings("unchecked")
	@Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.info("processing snapshot response" + resultObj);
        final PostMethod result = (PostMethod) resultObj;
		try {
			ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
					.getResponseBodyAsStream());
			Status status = null;
			if (null != responsePacket.getPacketFault()) {
				status = responsePacket.getPacketFault();
				processErrorStatus(status, keyMap);
			} else {
				List<Object> snapshotList = getQueryResponse(responsePacket);
				Iterator<Object> snapshotItr = snapshotList.iterator();
				if (snapshotItr.hasNext()) {
					status = (Status) snapshotItr.next();
					if (status.getMaxSeverity() == Severity.OK) {
						final List<Stat> statList = (List<Stat>) keyMap
		                        .get(VNXFileConstants.STATS);
						Iterator<Stat> statsIterator = statList.iterator();
						while (statsIterator.hasNext()) {
							Stat stat = (Stat) statsIterator.next();
							fetchSnapShotDetails(stat, snapshotList);
						}
					} else {
					    processErrorStatus(status, keyMap);
					}
				}
			}
			
		} catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the snapshot response due to {}",
                    ex.getMessage());
        } finally {
            result.releaseConnection();
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {

    }
    
	/**
     * fetches the snapshot details from the checkpoint list.
     * 
     * @param stat
     *            : Stat object.
     * @param ckptList
     *            : List of checkpoints.
     */
    private void fetchSnapShotDetails(final Stat stat, final List<Object> snapshotList) {
        int snapCount = 0;
        long snapCapacity = 0;
        Checkpoint checkPoint = null;
        //first element is stat object and remaining elem are snapshot's
        Iterator<Object> snapshotItr = snapshotList.iterator();
        snapshotItr.next();
        //processing snapshot list
        while (snapshotItr.hasNext()) {
        	checkPoint = (Checkpoint) snapshotItr.next();
        	if (fetchNativeId(stat.getNativeGuid()).equals(checkPoint.getCheckpointOf())) {
        		snapCount++;
                snapCapacity += (Long.valueOf(checkPoint.getFileSystemSize()) * 1024);
        	}
        }
        stat.setSnapshotCount(snapCount);
        stat.setSnapshotCapacity(snapCapacity);
    }

}
