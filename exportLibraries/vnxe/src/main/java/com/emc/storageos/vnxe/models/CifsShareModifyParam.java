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

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class CifsShareModifyParam {
	private VNXeBase cifsShare;
	private CifsShareParam cifsShareParameters;
	public VNXeBase getCifsShare() {
		return cifsShare;
	}
	public void setCifsShare(VNXeBase cifsShare) {
		this.cifsShare = cifsShare;
	}
	public CifsShareParam getCifsShareParameters() {
		return cifsShareParameters;
	}
	public void setCifsShareParameters(CifsShareParam cifsShareParameters) {
		this.cifsShareParameters = cifsShareParameters;
	}
	
	

}
