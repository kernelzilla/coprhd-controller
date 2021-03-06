/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.powerpath.PowermtRemoveCommand;
import com.iwave.ext.linux.command.powerpath.PowermtSaveCommand;
import com.iwave.ext.linux.command.powerpath.PowermtSetModeStandbyCommand;
import com.iwave.ext.linux.model.PowerPathDevice;

public class RemovePowerPathDevice extends LinuxExecutionTask<Void> {

	private final PowerPathDevice device;
	
	public RemovePowerPathDevice(PowerPathDevice device) {
		this.device = device;
	}
	
	public void execute() throws Exception {
		executeCommand(new PowermtSetModeStandbyCommand(device));
		executeCommand(new PowermtRemoveCommand(device));
		executeCommand(new PowermtSaveCommand());
	      setDetail("RemovePowerPathDevice.detail.formatted", device.getDeviceName(), device.getDeviceName());
	}
	
}
