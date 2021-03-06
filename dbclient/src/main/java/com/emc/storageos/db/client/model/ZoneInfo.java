/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import java.util.Arrays;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Zone Info to be used in UnManagedExportMask to store the zoning
 * information.
 */
@XmlRootElement(name = "zone_info")
public class ZoneInfo extends AbstractSerializableNestedObject {

    private static final String ZONE_NAME = "zoneName";
    private static final String INITIATOR_WWN = "initiatorWwn";
    private static final String INITIATOR_ID = "initiatorId";
    private static final String PORT_WWN = "portWwn";
    private static final String PORT_ID = "portId";
    private static final String NETWORK_ID = "networkId";
    private static final String NETWORK_WWN = "networkWwn";
    private static final String FABRIC_ID = "fabricId";
    private static final String NETWORK_SYSTEM_ID = "networkSystemId";
    
    public ZoneInfo() {
    }

    @XmlElement(name = "zone_name")
    public String getZoneName() {
        return getStringField(ZONE_NAME);
    }

    public void setZoneName(String zoneName) {
        setField(ZONE_NAME, zoneName == null ? "" : zoneName);
    }

    @XmlElement(name = "initiator_wwn")
    public String getInitiatorWwn() {
        return getStringField(INITIATOR_WWN);
    }

    public void setInitiatorWwn(String wwn) {
        setField(INITIATOR_WWN, wwn == null ? "" : wwn);
    }

    @XmlElement(name = "initiator_id")
    public String getInitiatorId() {
        return getStringField(INITIATOR_ID);
    }

    public void setInitiatorId(String id) {
        setField(INITIATOR_ID, id == null ? "" : id);
    }

    @XmlElement(name = "port_wwn")
    public String getPortWwn() {
        return getStringField(PORT_WWN);
    }

    public void setPortWwn(String wwn) {
        setField(PORT_WWN, wwn == null ? "" : wwn);
    }

    @XmlElement(name = "port_id")
    public String getPortId() {
        return getStringField(PORT_ID);
    }

    public void setPortId(String id) {
        setField(PORT_ID, id == null ? "" : id);
    }

    @XmlElement(name = "network_wwn")
    public String getNetworkWwn() {
        return getStringField(NETWORK_WWN);
    }

    public void setNetworkWwn(String wwn) {
        setField(NETWORK_WWN, wwn == null ? "" : wwn);
    }

    @XmlElement(name = "network_id")
    public String getNetworkId() {
        return getStringField(NETWORK_ID);
    }

    public void setNetworkId(String id) {
        setField(NETWORK_ID, id == null ? "" : id);
    }

    @XmlElement(name = "fabric_id")
    public String getFabricId() {
        return getStringField(FABRIC_ID);
    }

    public void setFabricId(String id) {
        setField(FABRIC_ID, id == null ? "" : id);
    }

    @XmlElement(name = "network_system_id")
    public String getNetworkSystemId() {
        return getStringField(NETWORK_SYSTEM_ID);
    }

    public void setNetworkSystemId(String id) {
        setField(NETWORK_SYSTEM_ID, id == null ? "" : id);
    }

    public String getZoneReferenceKey() {
        return FCZoneReference.makeEndpointsKey(Arrays.asList(new String[] {getInitiatorWwn(), getPortWwn()}));
    }
}
