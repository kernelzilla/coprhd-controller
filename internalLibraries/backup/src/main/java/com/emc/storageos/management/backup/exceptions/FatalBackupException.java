/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.backup.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class FatalBackupException extends BackupException {
    private static final long serialVersionUID = 6522238518062841437L;

    protected FatalBackupException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public FatalBackupException(final ServiceCode code, final String pattern,
            final Object[] parameters) {
        super(code, null, pattern, parameters);
    }

    @Deprecated
    public FatalBackupException(final ServiceCode code, final Throwable cause,
            final String pattern, final Object[] parameters) {
        super(code, cause, pattern, parameters);
    }
}
