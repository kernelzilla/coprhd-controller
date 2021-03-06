/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.FileSystemSnapCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeFileSystemSnap;
import com.emc.storageos.vnxe.models.VNXeSnapRestoreParam;
import com.sun.jersey.core.util.MultivaluedMapImpl;


public class FileSystemSnapRequests  extends KHRequests<VNXeFileSystemSnap>{
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemSnapRequests.class);
    private static final String URL = "/api/types/filesystemSnap/instances";
    private static final String URL_INSTANCE = "/api/instances/filesystemSnap/";
    private static final String URL_RESTORE="/action/restore";
    public FileSystemSnapRequests(KHClient client) {
        super(client);
        _url = URL;
    } 
    
    /**
     * create file system snap in async mode
     * @param param: FileSystemSnapCreateParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createFileSystemSnap(FileSystemSnapCreateParam param) throws VNXeException {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        
        return postRequestAsync(param);
    }
    
    /**
     * Get snapshot details by its name
     * @param name
     * @return
     */
    public VNXeFileSystemSnap getByName(String name) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER,VNXeConstants.NAME_FILTER+name);
        setQueryParameters(queryParams);
        VNXeFileSystemSnap result = null;
        List<VNXeFileSystemSnap> snapList = getDataForObjects(VNXeFileSystemSnap.class);
        //it should just return 1
        if (snapList!= null && snapList.size()>0) {
            result =snapList.get(0);
        } else {
            _logger.info("No file system snapshot found using the name: " +name);
        }
        return result;
    }

    /**
     * Delete file system snap
     * @param snapId
     * @return
     * @throws VNXeException
     */
    public VNXeCommandJob deleteFileSystemSnap(String snapId) throws VNXeException {
        _url = URL_INSTANCE + snapId;
        setQueryParameters(null);
        if (getDataForOneObject(VNXeFileSystemSnap.class) != null) {
            return deleteRequestAsync (null);
        } else {
            throw VNXeException.exceptions.vnxeCommandFailed(String.format("No filesystem snap %s found",
                    snapId));
        }
        
        
    }
           
    /**
     * Get the specific file system snap's details
     * @return
     */
    public VNXeFileSystemSnap getFileSystemSnap(String snapId) throws VNXeException{
    	_url = URL_INSTANCE + snapId;
        setQueryParameters(null);
        return getDataForOneObject(VNXeFileSystemSnap.class);

    }
    
    /**
     * Restore FS snapshot
     * @param snapId snapshot VNXe Id
     * @param restoreParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob restoreFileSystemSnap(String snapId, VNXeSnapRestoreParam restoreParam)
            throws VNXeException {

        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_RESTORE);
        _url = urlBuilder.toString();

        return postRequestAsync (restoreParam);

    
    }
    
    /**
     * Get a file system's snaps by its storageResource id.
     * @param fsId file system Id
     * @return list of VNXeFileSystemSnap
     */
    public List<VNXeFileSystemSnap> getFileSystemSnaps(String resourceId) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.STORAGE_RESOURCE_FILTER+resourceId);
        setQueryParameters(queryParams);
        return getDataForObjects(VNXeFileSystemSnap.class);
    }

}
