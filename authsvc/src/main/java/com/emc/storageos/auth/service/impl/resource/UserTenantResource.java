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
package com.emc.storageos.auth.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.auth.AuthenticationManager;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.resource.UserInfoPage.UserTenant;
import com.emc.storageos.security.resource.UserInfoPage.UserTenantList;

/**
 * Internal resource to query a user's tenancy
 */
@Path("/internal/userTenant")
public class UserTenantResource {

    @Autowired
    protected AuthenticationManager _authManager;
    
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public Response getUserTenant(@QueryParam("username") String username) {
        if( username==null || username.isEmpty() ) {
            Response.status(Status.BAD_REQUEST).entity("Query parameter username is required").build();
        } 
        Map<URI, UserMapping> userTenants = _authManager.getUserTenants(username);
        if( null != userTenants ) {
            UserTenantList userTenantList = new UserTenantList();
            userTenantList._userTenantList = new ArrayList<UserTenant>();
            for(Entry<URI, UserMapping> userTenantEntry :userTenants.entrySet() ) {
                UserTenant userTenant = new UserTenant();
                userTenant._id = userTenantEntry.getKey();
                userTenant._userMapping = userTenantEntry.getValue();                
                userTenantList._userTenantList.add(userTenant);
            }
            return Response.ok(userTenantList).build();
        }
        return Response.status(Status.BAD_REQUEST).entity(String.format("Invalid username %1s", username)).build();
    }
}
