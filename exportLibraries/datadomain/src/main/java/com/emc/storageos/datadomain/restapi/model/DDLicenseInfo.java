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
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by zeldib on 2/10/14.
 */
public class DDLicenseInfo {

    @SerializedName("license_key")
    @JsonProperty(value="license_key")
    public String licenseKey;

    public String feature;

    public String toString() {
        return new Gson().toJson(this).toString();
    }


}
