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

public class SnapScheduleParam {
    private VNXeBase snapSchedule;
    private boolean isSnapSchedulePaused;
    
    public VNXeBase getSnapSchedule() {
        return snapSchedule;
    }
    public void setSnapSchedule(VNXeBase snapSchedule) {
        this.snapSchedule = snapSchedule;
    }
    public boolean getIsSnapSchedulePaused() {
        return isSnapSchedulePaused;
    }
    public void setIsSnapSchedulePaused(boolean isSnapSchedulePaused) {
        this.isSnapSchedulePaused = isSnapSchedulePaused;
    }
    
    

}
