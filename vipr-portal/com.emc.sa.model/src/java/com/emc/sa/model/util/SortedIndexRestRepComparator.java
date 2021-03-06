/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.util.Comparator;

import com.emc.storageos.db.client.model.uimodels.SortedIndexDataObject;
import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.collections.comparators.NullComparator;

import com.emc.vipr.model.catalog.SortedIndexRestRep;

public class SortedIndexRestRepComparator implements Comparator<SortedIndexRestRep> {

    private static final BeanComparator COMPARATOR = new BeanComparator(SortedIndexDataObject.SORTED_INDEX_PROPERTY_NAME, new NullComparator());
    
    @Override
    public int compare(SortedIndexRestRep o1, SortedIndexRestRep o2) {
        return COMPARATOR.compare(o1, o2);
    }

}
