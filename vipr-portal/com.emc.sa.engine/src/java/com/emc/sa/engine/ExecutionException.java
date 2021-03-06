/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

public class ExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExecutionException(Throwable cause) {
        super(cause);
    }
}
