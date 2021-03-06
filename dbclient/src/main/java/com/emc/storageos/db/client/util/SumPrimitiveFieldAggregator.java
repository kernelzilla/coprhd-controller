/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class SumPrimitiveFieldAggregator implements DbAggregatorItf{

    private String[] _fields;
    private Map<String,DoubleValue> _valueMap = new HashMap<String,DoubleValue>();

    private DataObjectType  _doType;
    private long n_records;


    public String[] getAggregatedFields() {
        return _fields;
    }
    /**
     * @return the aggregated value.
     */
    public double getAggregate(String field) {
        return _valueMap.get(field).value;
    }

    public long get_NRecords() {
        return n_records;
    }

    public SumPrimitiveFieldAggregator(Class<? extends DataObject> clazz, String[] fields){
        _doType = TypeMap.getDoType(clazz);
        _fields = java.util.Arrays.copyOf(fields,fields.length);
        for (String field : fields)  {
            _valueMap.put(field,new DoubleValue());
        }
        n_records = 0;
    }

    @Override
    public void aggregate(Row<String, CompositeColumnName> row){

        if(row.getColumns().size() == 0) {
            return;
        }

        Iterator<Column<CompositeColumnName>> columnIter = row.getColumns().iterator();
        String prevColumnName = "";
        Object prevValue = null;
        DoubleValue prevDValue = null;
        while(columnIter.hasNext()) {
            Column<CompositeColumnName> column = columnIter.next();
            String curName = column.getName().getOne();
            if( !_valueMap.containsKey(curName) ){
                if( prevValue != null ) {
                    prevDValue = _valueMap.get(prevColumnName);
                    prevDValue.value += getDouble(prevValue);
                }
                prevValue = null;
                prevDValue = null;
                prevColumnName = curName;
            }
            else {
                 if( !prevColumnName.equals(curName) && prevValue != null  ) {
                    prevDValue.value += getDouble(prevValue);
                }
                ColumnField  field = _doType.getColumnField(curName);
                Object curValue = ColumnValue.getPrimitiveColumnValue(column,field.getPropertyDescriptor());
                prevValue = curValue;
                prevDValue = _valueMap.get(curName);
                prevColumnName = curName;
            }
        }
        if( prevValue != null ) {
            prevDValue.value += getDouble(prevValue);
        }

        n_records ++;
    }

    protected double getDouble(Object value){
        double  val = 0;
        if( value instanceof Integer )  {
            val = (double)((Integer) value).intValue();
        } else if (value instanceof Long ) {
            val = (double)((Long) value).longValue();
        }else if (value instanceof Byte ) {
            val = (double) ((Byte)value).byteValue();
        } else if (value instanceof Short ) {
            val = (double)((Short)value).shortValue();
        } else if (value instanceof Float ) {
            val = (double)((Float) value).floatValue();
        } else if (value instanceof Double ) {
            val = (Double) value;
        }  else {
            throw new UnsupportedOperationException();
        }
        return val;
    }

    private static class DoubleValue {
        public double value;

        public DoubleValue(){
            value = 0.0;
        }

    }
}



