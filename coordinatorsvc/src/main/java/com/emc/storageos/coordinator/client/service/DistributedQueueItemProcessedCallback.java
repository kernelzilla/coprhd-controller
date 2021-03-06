/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service;

/**
 * QueueItemProcessedCallback
 */
public interface DistributedQueueItemProcessedCallback {

    /**
     * Removes an item from the associated distributed queue.
     * This method must be called by DistributedQueueConsumer's after successfully consuming an item.
     *
     * @throws Exception
     */
    public void itemProcessed() throws Exception;
}