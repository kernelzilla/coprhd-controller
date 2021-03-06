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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.HostLun;

public class HostLunRequests extends KHRequests<HostLun> {
    
    private static final Logger _logger = LoggerFactory.getLogger(HostLunRequests.class);
    private static String URL = "/api/types/hostLUN/instances";
    public static String ID_SEQUENCE_LUN = "prod";
    public static String ID_SEQUENCE_SNAP = "snap";
    
    
    public HostLunRequests(KHClient client) {
        super(client);
        _url = URL;
    }
    
    public HostLun getHostLun(String lunId, String hostId, String idCharSequence) {
        _logger.info("Finding hostLun for lunId: {}, hostId: {}", lunId, hostId);
        
        StringBuilder queryFilter = new StringBuilder(VNXeConstants.LUN_FILTER);
    	queryFilter.append(lunId);
    	setFilter(queryFilter.toString());
    	
        HostLun result = null;
        List<HostLun> hostLuns = getDataForObjects(HostLun.class);
        for (HostLun hostLun : hostLuns) {
            String lunHostId = hostLun.getHost().getId();
            if (hostId.equals(lunHostId) && hostLun.getId().contains(idCharSequence)) {
                result = hostLun;
                _logger.info("Found hostLun");
                break;
            }
        }
        
        return result;
    }
    
    public List<HostLun> getByLunId(String lunId) {
        _logger.info("Finding hostLun for lunId: {}, hostId: {}", lunId);
        setFilter(VNXeConstants.LUN_FILTER + lunId);
        return getDataForObjects(HostLun.class);
    }
    
}
