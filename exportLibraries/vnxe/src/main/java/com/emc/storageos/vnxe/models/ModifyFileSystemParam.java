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

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class ModifyFileSystemParam extends CreateFileSystemParam{
	private List<NfsShareModifyParam> nfsShareModify;
	private List<NfsShareDeleteParam> nfsShareDelete;
	private List<CifsShareModifyParam> cifsShareModify;
	private List<CifsShareDeleteParam> cifsShareDelete;
	
	public List<NfsShareModifyParam> getNfsShareModify() {
		return nfsShareModify;
	}
	public void setNfsShareModify(List<NfsShareModifyParam> nfsShareModify) {
		this.nfsShareModify = nfsShareModify;
	}
	public List<NfsShareDeleteParam> getNfsShareDelete() {
		return nfsShareDelete;
	}
	public void setNfsShareDelete(List<NfsShareDeleteParam> nfsShareDelete) {
		this.nfsShareDelete = nfsShareDelete;
	}
	public List<CifsShareModifyParam> getCifsShareModify() {
		return cifsShareModify;
	}
	public void setCifsShareModify(List<CifsShareModifyParam> cifsShareModify) {
		this.cifsShareModify = cifsShareModify;
	}
	public List<CifsShareDeleteParam> getCifsShareDelete() {
		return cifsShareDelete;
	}
	public void setCifsShareDelete(List<CifsShareDeleteParam> cifsShareDelete) {
		this.cifsShareDelete = cifsShareDelete;
	}
	
	

}
