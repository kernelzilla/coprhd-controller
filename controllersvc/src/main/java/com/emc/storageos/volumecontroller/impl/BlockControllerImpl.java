/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.exceptions.RetryableDatabaseException;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.monitoring.MonitoringJob;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobUtil;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionScanJob;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.MonitorTaskCompleter;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.ScanTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class BlockControllerImpl extends AbstractDiscoveredSystemController implements BlockController {
    private static final Logger _log = LoggerFactory.getLogger(BlockControllerImpl.class);
    private Set<BlockController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;
    private DataCollectionJobUtil _util;

    public void setDeviceImpl(Set<BlockController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setUtil(DataCollectionJobUtil util) {
        _util = util;
    }


    /**
     * Dummy implementation that just returns the first controller that implements this device
     *
     * @param device
     * @return
     * @throws ControllerException
     */
    protected Controller lookupDeviceController(DiscoveredSystemObject device)
            throws ControllerException {
        if (device == null) {
            throw ClientControllerException.fatals.unableToLookupStorageDeviceIsNull();
        }
        BlockController bc = _deviceImpl.iterator().next();
        if (bc == null) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("BlockController");
        }
        return bc;
    }

    /**
     * Dummy implementation that just returns the controller.
     *
     * @return
     * @throws ControllerException
     */
    protected Controller lookupDeviceController() throws ControllerException {
        BlockController bc = _deviceImpl.iterator().next();
        if (bc == null) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("BlockController");
        }
        return bc;
    }

    /**
     * Puts the operation in the zkQueue so it can Dispatch'd to a Device Controller
     *
     * @param methodName
     * @param args
     * @throws ControllerException
     */
    private void blockRMI(String methodName, Object... args) throws InternalException {
        queueTask(_dbClient, StorageSystem.class, _dispatcher, methodName, args);
    }

    public void createVolumes(URI storage, URI pool, List<URI> volumes, VirtualPoolCapabilityValuesWrapper capabilities,String opId)
        throws InternalException {
        blockRMI("createVolumes", storage, pool, volumes,capabilities,opId);
    }
    
    public void modifyVolumes(URI storage, URI pool, List<URI> volumes, String opId) throws InternalException {
        blockRMI("modifyVolumes", storage, pool, volumes, opId);
    }

    public void createMetaVolume(URI storage, URI pool, URI volume, VirtualPoolCapabilityValuesWrapper capabilities,String opId)
        throws InternalException {
        blockRMI("createMetaVolume", storage, pool, volume, capabilities, opId);
    }

    @Override
    public void rollBackCreateMetaVolume(URI systemURI, URI volumeURI, String createStepId, String opId) throws InternalException
    {
        blockRMI("rollBackCreateMetaVolume", systemURI, volumeURI, createStepId, opId);
    }

    @Override
    public void createMetaVolumes(URI storage, URI pool, List<URI> volumes, VirtualPoolCapabilityValuesWrapper capabilities, String opId)
            throws InternalException {
        blockRMI("createMetaVolumes", storage, pool, volumes, capabilities, opId);
    }

    @Override
    public void rollBackCreateVolumes(URI systemURI, List<URI> volumeURIs, String opId) throws InternalException
    {
        blockRMI("rollBackCreateVolume", systemURI, volumeURIs, opId);
    }
    
    @Override
    public void expandBlockVolume(URI storage, URI pool, URI volume, Long size, String opId)
            throws InternalException {
        blockRMI("expandBlockVolume", storage, pool, volume, size, opId);
    }

    @Override
    public void expandVolume(URI storage, URI pool, URI volume, Long size, String opId)
            throws InternalException {
        blockRMI("expandVolume", storage, pool, volume, size, opId);
    }

    @Override
    public void rollBackExpandVolume(URI systemURI, URI volumeURI, String expandStepId, String opId) throws InternalException
    {
        blockRMI("rollBackExpandVolume", systemURI, volumeURI, expandStepId, opId);
    }

    @Override
    public void deleteVolumes(URI storage, List<URI> volumes, String opId)
        throws InternalException {
        blockRMI("deleteVolumes", storage, volumes, opId);
    }

    @Override
    public void createSnapshot(URI storage, List<URI> snapshotList, Boolean createInactive, String opId)
            throws InternalException {
        blockRMI("createSnapshot", storage, snapshotList, createInactive, opId);
    }

    @Override
    public void activateSnapshot(URI storage, List<URI> snapshotList, String opId)
            throws InternalException {
        try {
        // Direct RMI call to expedite this call without any potential distribute-Q delay
        StorageSystem storageSystem =
                _dbClient.queryObject(StorageSystem.class, storage);
            Controller controller = lookupDeviceController(storageSystem);

            BlockController blkcontroller = (BlockController)controller;
            blkcontroller.activateSnapshot(storage, snapshotList, opId);
        } catch (RetryableDatabaseException e) {
            if (e.getServiceCode() == ServiceCode.DBSVC_CONNECTION_ERROR) {
                // netflix curator ConnectionException is not serializable
                // and thus should not be sent back to rmi client.
                _log.error("Failed to queue task due to dbsvc disconnected. Error: {}", e.getMessage());
                e.printStackTrace();
                throw DatabaseException.retryables.connectionFailed();
    }
            throw e;
        }

    }

    @Override
    public void deleteSnapshot(URI storage, URI snapshot, String opId)
            throws InternalException {
        blockRMI("deleteSnapshot", storage, snapshot, opId);
    }

    @Override
    public void restoreVolume(URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String opId)
            throws InternalException {
        blockRMI("restoreVolume", storage, pool, volume, snapshot, updateOpStatus, opId);
    }

    @Override
    public void resyncFullCopy(URI storage, List<URI> clone, Boolean updateOpStatus, String opId)
            throws InternalException {
        blockRMI("resyncFullCopy", storage, clone, updateOpStatus, opId);
    }
    
    @Override
    public void connectStorage(URI storage) throws InternalException {
        blockRMI("connectStorage", storage);
    }

    @Override
    public void disconnectStorage(URI storage) throws InternalException {
        blockRMI("disconnectStorage", storage);
    }

    @Override
    public void createMirror(URI storage, URI mirror, Boolean createInactive, String opId) throws InternalException {
        blockRMI("createMirror", storage, mirror, createInactive, opId);
    }

    @Override
    public void attachNativeContinuousCopies(URI storage, URI sourceVolume, String opId) throws InternalException {
        blockRMI("attachNativeContinuousCopies", storage, sourceVolume, opId);
    }

    @Override
    public void detachNativeContinuousCopies(URI storage, List<URI> mirrors, List<URI> promotees,
                                             String opId) throws InternalException {
        blockRMI("detachNativeContinuousCopies", storage, mirrors, promotees, opId);
    }

    @Override
    public void pauseNativeContinuousCopies(URI storage, List<URI> mirrors, Boolean sync,
                                            String opId) throws InternalException {
        try {
        // Direct RMI call to expedite this call without any potential distribute-Q delay
        StorageSystem storageSystem =
                _dbClient.queryObject(StorageSystem.class, storage);
            Controller controller = lookupDeviceController(storageSystem);
            BlockController blkcontroller = (BlockController)controller;
            blkcontroller.pauseNativeContinuousCopies(storage, mirrors, sync, opId);
        } catch (RetryableDatabaseException e) {
            if (e.getServiceCode() == ServiceCode.DBSVC_CONNECTION_ERROR) {
                // netflix curator ConnectionException is not serializable
                // and thus should not be sent back to rmi client.
                _log.error("Failed to queue task due to dbsvc disconnected. Error: {}", e.getMessage());
                e.printStackTrace();
                throw DatabaseException.retryables.connectionFailed();
    }
            throw e;
        }
    }

    @Override
    public void resumeNativeContinuousCopies(URI storage, List<URI> mirrors, String opId) throws InternalException {
        blockRMI("resumeNativeContinuousCopies", storage, mirrors, opId);
    }

    @Override
    public void detachMirror(URI storage, URI mirror, String opId) throws InternalException {
        blockRMI("detachMirror", storage, mirror, opId);
    }

    @Override
    public void deleteMirror(URI storage, URI mirror, String opId) throws InternalException {
        blockRMI("deleteMirror", storage, mirror, opId);
    }

    @Override
    public void deactivateMirror(URI storage, URI mirror, String opId) throws InternalException {
        blockRMI("deactivateMirror", storage, mirror, opId);
    }

    @Override
    public void createFullCopy(URI storage, List<URI> fullCopyVolumes, Boolean createInactive, String opId)
            throws InternalException {
        blockRMI("createFullCopy", storage, fullCopyVolumes, createInactive, opId);
    }

	@Override
	public void deleteConsistencyGroup(URI storage, URI consistencyGroup, Boolean markInactive, String opId) throws InternalException {
		blockRMI("deleteConsistencyGroup", storage, consistencyGroup, markInactive, opId);
	}
	
	@Override
	public void createConsistencyGroup(URI storage, URI consistencyGroup, String opId) throws InternalException {
		blockRMI("createConsistencyGroup", storage, consistencyGroup, opId);
	}

    @Override
    public void activateFullCopy(URI storage, List<URI> fullCopy, String opId) {
        try {
        // Direct RMI call to expedite this call without any potential distribute-Q delay
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            Controller controller = lookupDeviceController(storageSystem);
            BlockController blkcontroller = (BlockController)controller;
            blkcontroller.activateFullCopy(storage, fullCopy, opId);
        } catch (RetryableDatabaseException e) {
            if (e.getServiceCode() == ServiceCode.DBSVC_CONNECTION_ERROR) {
                // netflix curator ConnectionException is not serializable
                // and thus should not be sent back to rmi client.
                _log.error("Failed to queue task due to dbsvc disconnected. Error: {}", e.getMessage());
                e.printStackTrace();
                throw DatabaseException.retryables.connectionFailed();
    }
            throw e;
        }

    }

    @Override
    public void detachFullCopy(URI storage, List<URI> fullCopy, String opId) {
        blockRMI("detachFullCopy", storage, fullCopy, opId);
    }

    @Override
    public Integer checkSyncProgress(URI storage, URI source, URI target, String opId) {
        try {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            Controller controller = lookupDeviceController(storageSystem);
            BlockController blkcontroller = (BlockController)controller;
            return blkcontroller.checkSyncProgress(storage, source, target, opId);
        } catch (RetryableDatabaseException e) {
            if (e.getServiceCode() == ServiceCode.DBSVC_CONNECTION_ERROR) {
                // netflix curator ConnectionException is not serializable
                // and thus should not be sent back to rmi client.
                _log.error("Failed to queue task due to dbsvc disconnected. Error: {}", e.getMessage());
                e.printStackTrace();
                throw DatabaseException.retryables.connectionFailed();
    }
            throw e;
        }
    }

    /**
     * {@inheritDoc}}
     */
    @Override
    public void discoverStorageSystem(AsyncTask[] tasks)
            throws ControllerException {
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, Lock.DISCOVER_COLLECTION_LOCK, ControllerServiceImpl.DISCOVERY);
        } catch (Exception e) {
            _log.error(
                    "Problem in discoverStorageSystem due to {} ",
                     e.getMessage());
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }
    }
    /**
     * {@inheritDoc}}
     */
    @Override
    public void scanStorageProviders(AsyncTask[] tasks)
            throws ControllerException {
        try {
            DataCollectionScanJob job = new DataCollectionScanJob();
            for( AsyncTask task : tasks) {
                job.addCompleter(new ScanTaskCompleter(task));
            }
            ControllerServiceImpl.enqueueDataCollectionJob(job);
        } catch (Exception e) {
            throw ClientControllerException.fatals.unableToScanSMISProviders(tasks, "BlockController", e);
        }
    }

    /**
     * {@inheritDoc}}
     */
    @Override
    public void addStorageSystem(URI storage, URI[] providers, boolean primaryProvider, String opId)
            throws InternalException {
        blockRMI("addStorageSystem", storage, providers, primaryProvider, opId);
    }


    /**
     * {@inheritDoc}}
     */
    @Override
    public void startMonitoring(AsyncTask task, Type deviceType)
            throws ControllerException {
        try {
            MonitoringJob job = new MonitoringJob();
            job.setCompleter(new MonitorTaskCompleter(task));
            job.setDeviceType(deviceType);
            ControllerServiceImpl.enqueueMonitoringJob(job);
        } catch (Exception e) {
            throw ClientControllerException.fatals.unableToMonitorSMISProvider(task, deviceType.toString(), e);
        }
    }

	@Override
	public void noActionRollBackStep(URI deviceURI, String opID) {
		_log.info("Running empty Roll back step for storage system {}", deviceURI);
		WorkflowStepCompleter.stepSucceded(opID);

	}
	
    @Override
    public void updateConsistencyGroup(URI storage, URI consistencyGroup,
                                       List<URI> addVolumesList,
                                       List<URI> removeVolumesList, String task) {
        blockRMI("updateConsistencyGroup", storage, consistencyGroup, addVolumesList,
                removeVolumesList, task);
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber, String interfaceType) {
        // Making a direct call to get connection status.
        Controller controller = lookupDeviceController();
        BlockController blkcontroller = (BlockController) controller;
        return blkcontroller.validateStorageProviderConnection(ipAddress, portNumber, interfaceType);
    }

    @Override
    public void restoreFromFullCopy(URI storage, List<URI> clones,
            Boolean updateOpStatus, String opId) throws InternalException {
        blockRMI("restoreFromFullCopy", storage, clones, updateOpStatus, opId);
        
    }

}
