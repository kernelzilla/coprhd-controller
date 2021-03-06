/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.runners.Parameterized.Parameters;

public abstract class AbstractBundleTest {

    @Parameters
    public static Collection<Object[]> data() {
        final List<Object[]> list = new ArrayList<Object[]>();
        for (final Class<?> clazz : Documenter.getMessageBundleClasses()) {
            list.add(new Object[] { clazz });
        }
        return list;
    }

    protected final Class<?> baseClass;

    public AbstractBundleTest(final Class<?> baseClass) {
        super();
        this.baseClass = baseClass;
    }
}
