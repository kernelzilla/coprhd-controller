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
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_cluster")
public class XtremIOCluster {
	
	@SerializedName("name")
	@JsonProperty(value="name")
	private String name;

	public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @SerializedName("href")
	@JsonProperty(value="href")
	private String href;
	
	public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String toString() {
		return new Gson().toJson(this).toString();
	}
}
