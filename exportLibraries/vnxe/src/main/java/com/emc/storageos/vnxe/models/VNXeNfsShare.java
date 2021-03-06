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

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.vnxe.models.NfsShareParam.NFSShareDefaultAccessEnum;

@JsonIgnoreProperties(ignoreUnknown=true)
public class VNXeNfsShare extends VNXeBase{
	private String name;
	private VNXeBase parentFilesystem;
	private VNXeBase parentFilesystemSnap;
	private String creationTime;
	private boolean isReadOnly;
	private List<VNXeBase> readWriteHosts;
	private List<VNXeBase> noAccessHosts;
	private List<VNXeBase> rootAccessHosts;
	private List<VNXeBase> readOnlyHosts;
	private NFSShareDefaultAccessEnum defaultAccess;
	private String path;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public VNXeBase getParentFilesystem() {
		return parentFilesystem;
	}
	public void setParentFilesystem(VNXeBase parentFilesystem) {
		this.parentFilesystem = parentFilesystem;
	}
	public VNXeBase getParentFilesystemSnap() {
		return parentFilesystemSnap;
	}
	public void setParentFilesystemSnap(VNXeBase parentFilesystemSnap) {
		this.parentFilesystemSnap = parentFilesystemSnap;
	}
	public String getCreationTime() {
		return creationTime;
	}
	public void setCreationTime(String creationTime) {
		this.creationTime = creationTime;
	}
	public boolean getIsReadOnly() {
		return isReadOnly;
	}
	public void setIsReadOnly(boolean isReadOnly) {
		this.isReadOnly = isReadOnly;
	}
	public List<VNXeBase> getReadWriteHosts() {
		return readWriteHosts;
	}
	public void setReadWriteHosts(List<VNXeBase> readWriteHosts) {
		this.readWriteHosts = readWriteHosts;
	}
	public List<VNXeBase> getNoAccessHosts() {
		return noAccessHosts;
	}
	public void setNoAccessHosts(List<VNXeBase> noAccessHosts) {
		this.noAccessHosts = noAccessHosts;
	}
	public List<VNXeBase> getRootAccessHosts() {
		return rootAccessHosts;
	}
	public void setRootAccessHosts(List<VNXeBase> rootAccessHosts) {
		this.rootAccessHosts = rootAccessHosts;
	}
	public List<VNXeBase> getReadOnlyHosts() {
		return readOnlyHosts;
	}
	public void setReadOnlyHosts(List<VNXeBase> readOnlyHosts) {
		this.readOnlyHosts = readOnlyHosts;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public NFSShareDefaultAccessEnum getDefaultAccess() {
		return defaultAccess;
	}
	public void setDefaultAccess(NFSShareDefaultAccessEnum defaultAccess) {
		this.defaultAccess = defaultAccess;
	}
	
	
}
