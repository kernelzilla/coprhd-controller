/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

public class UnManagedDiscoveredObject extends DataObject{
    // Unique Bourne identifier.
    private String _nativeGuid;
    
    @AlternateId("StandAloneObjectsAltIdIdnex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }
    
    public static enum SupportedProvisioningType {
	    THIN("TRUE"),
	    THICK("FALSE");
	    
	    private String _provisioningType;
	    
	    SupportedProvisioningType(String provisioningType) {
	    	_provisioningType = provisioningType;
	    }
	    
	    public String getProvisioningTypeValue() {
	        return 	_provisioningType;
	    }
	    
	    public static String getProvisioningType(String isThinlyProvisioned) {
	    	for (SupportedProvisioningType provisioningType : values()) {
	    		if (provisioningType.getProvisioningTypeValue().equalsIgnoreCase(isThinlyProvisioned))
	    			return provisioningType.toString();
	    	}
	    	return null;
	    }
    }
}
