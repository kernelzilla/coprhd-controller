/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service.impl;

import com.emc.storageos.coordinator.client.model.*;
import com.emc.storageos.coordinator.client.service.*;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.services.util.Strings;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.model.sys.ClusterInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.utils.EnsurePath;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static com.emc.storageos.coordinator.client.model.Constants.*;
import static com.emc.storageos.coordinator.client.model.PropertyInfoExt.TARGET_INFO;
import static com.emc.storageos.coordinator.client.model.PropertyInfoExt.*;
import static com.emc.storageos.coordinator.mapper.PropertyInfoMapper.decodeFromString;

/**
 * Default coordinator client implementation
 */
public class CoordinatorClientImpl implements CoordinatorClient {
    private static final String CONN_POOL_NAME = "ConnectionStateWorkerPool";
    private static final String NODE_POOL_NAME = "NodeChangeWorkerPool";
    private static final Logger log = LoggerFactory.getLogger(CoordinatorClientImpl.class);

    private final ConcurrentMap<String, Object> _proxyCache = new ConcurrentHashMap<String, Object>();

    private ZkConnection _zkConnection;

    private int nodeCount = 0;

    private String sysSvcName;
    private String sysSvcVersion;

    // connection state notifier
    private final Set<ConnectionStateListener> _listener = new CopyOnWriteArraySet<ConnectionStateListener>();
    private final ExecutorService _connectionStateWorker = new NamedThreadPoolExecutor(CONN_POOL_NAME, 1);
    private final ExecutorService nodeChangeWorker = new NamedThreadPoolExecutor(NODE_POOL_NAME, 1);

    private DbVersionInfo dbVersionInfo;
    
    private static Properties defaultProperties;
    private static Properties ovfProperties;

    private CoordinatorClientInetAddressMap inetAddressLookupMap;

    private NodeCacheWatcher nodeWatcher = new NodeCacheWatcher();

    /**
     * Set ZK cluster connection. Connection must be built but not connected when this method is
     * called
     * 
     * @param zkConnection
     */
    public void setZkConnection(ZkConnection zkConnection) {
        _zkConnection = zkConnection;
    }

    public ZkConnection getZkConnection() {
        return _zkConnection;
    }

    public void setNodeCount(int count) {
        nodeCount = count;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setSysSvcName(String name) {
        sysSvcName = name;
        log.info("sysSvcName={}", name);

    }

    public String getSysSvcName() {
        return sysSvcName;
    }

    public void setSysSvcVersion(String version) {
        sysSvcVersion = version;
        log.info("sysSvcVersion={}", version);
    }

    public String getSysSvcVersion() {
        return sysSvcVersion;
    }

    @Override
    public void setDbVersionInfo(DbVersionInfo info) {
        dbVersionInfo = info;
    }
    
    public static void setDefaultProperties(Properties defaults) {
        defaultProperties = defaults;
    }

    public static void setOvfProperties(Properties ovfProps) {
        ovfProperties = ovfProps;
    }
    

    @Override
    public void start() throws IOException {
        if (_zkConnection.curator().isStarted()) {
            return;
        }

        _zkConnection.curator().getConnectionStateListenable()
                .addListener(new org.apache.curator.framework.state.ConnectionStateListener() {
                    @Override
                    public void stateChanged(CuratorFramework client, final ConnectionState newState) {
                        _connectionStateWorker.submit(new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                Iterator<ConnectionStateListener> it = _listener.iterator();
                                while (it.hasNext()) {
                                    ConnectionStateListener listener = it.next();
                                    try {
                                        switch (newState) {
                                            case RECONNECTED:
                                            case CONNECTED: {
                                                listener.connectionStateChanged(ConnectionStateListener.State.CONNECTED);
                                                break;
                                            }
                                            case LOST:
                                            case SUSPENDED: {
                                                listener.connectionStateChanged(ConnectionStateListener.State.DISCONNECTED);
                                                break;
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("Connection listener threw", e);
                                    }
                                }
                                return null;
                            }
                        });
                    }
                });
        _zkConnection.connect();
        try {
            EnsurePath path = new EnsurePath(ZkPath.SERVICE.toString());
            path.ensure(_zkConnection.curator().getZookeeperClient());
        } catch (Exception e) {
            // if startup fails, shut down our thread pool so whoever called us will exit cleanly if
            // desired
            if ((_connectionStateWorker != null) && (!_connectionStateWorker.isShutdown())) {
                _connectionStateWorker.shutdownNow();
            }
            if (nodeChangeWorker != null && !nodeChangeWorker.isShutdown()) {
                nodeChangeWorker.shutdownNow();
            }
            throw CoordinatorException.fatals.errorConnectingCoordinatorService(e);
        }

        // writing local node to zk
        initInetAddressEntry();
    }

    @Override
    public void stop() {
        if (_zkConnection.curator().isStarted()) {
            _zkConnection.disconnect();
        }
    }

    /**
     * Verify if this is in zk and it's the same as current node; If not, save to the map and update
     * zk
     * 
     */
    private void initInetAddressEntry() {
        DualInetAddress address = inetAddressLookupMap.getDualInetAddress();
        if (!inetAddressLookupMap.isControllerNode()) {
            // this is a data node
            if (!verifyPublishedDualInetAddress(inetAddressLookupMap.getNodeName())) {
                // publish
                setNodeDualInetAddressInfo(inetAddressLookupMap.getNodeName(), address.toString());
            }
        }
        // if the data node map does not have it yet, save it to the map
        if (inetAddressLookupMap.get(inetAddressLookupMap.getNodeName()) == null
                || (!inetAddressLookupMap.get(inetAddressLookupMap.getNodeName()).equals(
                        inetAddressLookupMap.getDualInetAddress()))) {
            inetAddressLookupMap.put(inetAddressLookupMap.getNodeName(), address);
        }
    }

    /**
     * Returns true is found published DualInetAddress for this node, and it matches with current
     * configured
     * 
     * @param nodeId
     * @return
     */
    private boolean verifyPublishedDualInetAddress(String nodeId) {
        DualInetAddress dualAddress = null;
        Configuration config = queryConfiguration(Constants.NODE_DUALINETADDR_CONFIG, nodeId);
        if (config != null) {
            dualAddress = parseInetAddressConfig(config);
        }
        if ((dualAddress != null) && dualAddress.equals(inetAddressLookupMap.getDualInetAddress())) {
            return true;
        }
        return false;
    }

    /**
     * Try to look up the node in zk configuration - this is called ONLY if map does not have it.
     * 
     * @param nodeId
     *            the node in the lookup, any node
     * @return DualInetAddress of the node
     */
    public DualInetAddress loadInetAddressFromCoordinator(String nodeId) {
        DualInetAddress dualAddress = null;
        // grab the lock
        InterProcessLock lock = null;
        try {
            lock = getLock(NODE_DUALINETADDR_CONFIG + nodeId);
            lock.acquire();
            Configuration config = queryConfiguration(Constants.NODE_DUALINETADDR_CONFIG, nodeId);
            if (config != null) {
                dualAddress = parseInetAddressConfig(config);
            }
        } catch (Exception e) {
            log.warn("Unexpected exception during loadInetAddressFromCoordinator()", e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    log.warn("Unexpected exception unlocking loadInetAddressFromCoordinator()", e);
                }
            }
        }

        // Add it to the map
        if (dualAddress != null) {
            inetAddressLookupMap.put(nodeId, dualAddress);
        }

        return dualAddress;
    }

    /**
     * Parses the Configuration and read into a DualInetAddress.
     * 
     * @param config
     *            - the configuratino in zk
     * @return - a DualInetAddress if ound, null otherwise
     */
    public DualInetAddress parseInetAddressConfig(Configuration config) {
        String addresses = config.getConfig(Constants.CONFIG_DUAL_INETADDRESSES);
        if (addresses.trim().length() > 0) {
            String[] inetAddresses = addresses.split(",");
            try {
                if (inetAddresses.length > 1) {
                    String ip4 = (inetAddresses[0] == null) ? null : inetAddresses[0];
                    String ip6 = (inetAddresses[1] == null) ? null : inetAddresses[1];
                    return DualInetAddress.fromAddresses(ip4, ip6);
                } else {
                    return DualInetAddress.fromAddress(inetAddresses[0]);
                }
            } catch (UnknownHostException ex) {
                log.warn("Exception reading InetAddressConfig from coordinator: ", ex);
                return null;
            }
        }
        return null;
    }

    /**
     * Set node info to zk so that it can be available for lookup in coordinatorclient.
     * 
     * @param nodeId  the node_id to be persisted
     * @param addresses A string of ip addresses(v4/v6) with ',' as separator
     */
    public void setNodeDualInetAddressInfo(String nodeId, String addresses) {
        // grab a lock and verifyPublishedDualInetAddress first
        InterProcessLock lock = null;
        try {
            lock = getLock(Constants.NODE_DUALINETADDR_CONFIG + nodeId);
            lock.acquire();
            if (!verifyPublishedDualInetAddress(nodeId)) {
                ConfigurationImpl cfg = new ConfigurationImpl();
                cfg.setId(nodeId);
                cfg.setKind(Constants.NODE_DUALINETADDR_CONFIG);
                cfg.setConfig(Constants.CONFIG_DUAL_INETADDRESSES, addresses);
                persistServiceConfiguration(cfg);
            }
        } catch (Exception e) {
            log.warn("Unexpected exception during setNodeDualInetAddressInfo()", e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    log.warn("Unexpected exception unlocking in setNodeDualInetAddressInfo()", e);
                }
            }
        }
    }

    @Override
    public boolean isConnected() {
        return _zkConnection.curator().getZookeeperClient().isConnected();
    }

    @Override
    public void persistServiceConfiguration(Configuration... configs) throws CoordinatorException {
        try {
            for (Configuration config : configs) {
                String configParentPath = String.format("%1$s/%2$s", ZkPath.CONFIG,
                        config.getKind());

                EnsurePath path = new EnsurePath(configParentPath);
                path.ensure(_zkConnection.curator().getZookeeperClient());

                String servicePath = String.format("%1$s/%2$s", configParentPath, config.getId());
                Stat stat = _zkConnection.curator().checkExists().forPath(servicePath);
                if (stat != null) {
                    _zkConnection.curator().setData().forPath(servicePath, config.serialize());
                } else {
                    _zkConnection.curator().create().forPath(servicePath, config.serialize());
                }
            }
        } catch (final Exception e) {
            throw CoordinatorException.fatals.unableToPersistTheConfiguration(e);
        }
    }

    @Override
    public void removeServiceConfiguration(Configuration... configs) throws CoordinatorException {
        for (int i = 0; i < configs.length; i++) {
            Configuration config = configs[i];
            String servicePath = String.format("%1$s/%2$s/%3$s", ZkPath.CONFIG, config.getKind(),
                    config.getId());
            try {
                _zkConnection.curator().delete().forPath(servicePath);
            } catch (KeeperException.NoNodeException ignore) {
                // Ignore exception, don't re-throw
                log.debug("Caught exception but ignoring it: " + ignore);
            } catch (Exception e) {
                throw CoordinatorException.fatals.unableToRemoveConfiguration(config.getId(), e);
            }
        }
    }

    @Override
    public List<Configuration> queryAllConfiguration(String kind) throws CoordinatorException {
        String serviceParentPath = String.format("%1$s/%2$s", ZkPath.CONFIG, kind);
        List<String> configPaths;
        try {
            configPaths = _zkConnection.curator().getChildren().forPath(serviceParentPath);
        } catch (KeeperException.NoNodeException ignore) {
            // Ignore exception, don't re-throw
            log.debug("Caught exception but ignoring it: " + ignore);
            return Arrays.asList(new Configuration[0]);
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToListAllConfigurationForKind(kind, e);
        }
        List<Configuration> configs = new ArrayList<Configuration>(configPaths.size());
        for (String configPath : configPaths) {
            Configuration config = queryConfiguration(kind, configPath);
            if (config != null) {
                configs.add(config);
            }
        }
        return configs;
    }

    @Override
    public Configuration queryConfiguration(String kind, String id) throws CoordinatorException {
        String servicePath = String.format("%1$s/%2$s/%3$s", ZkPath.CONFIG, kind, id);
        try {
            byte[] data = _zkConnection.curator().getData().forPath(servicePath);
            return ConfigurationImpl.parse(data);
        } catch (KeeperException.NoNodeException ignore) {
            // Ignore exception, don't re-throw
            log.debug("Caught exception but ignoring it: " + ignore);
            return null;
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToFindConfigurationForKind(kind, id, e);
        }
    }

    @Override
    public void setConnectionListener(ConnectionStateListener listener) {
        _listener.add(listener);
    }

    @Override
    public <T> T locateService(Class<T> clazz, String name, String version, String tag,
            String endpointKey) throws CoordinatorException {
        String key = String.format("%1$s:%2$s:%3$s:%4$s", name, version, tag, endpointKey);
        Object proxy = _proxyCache.get(key);
        if (proxy == null) {
            List<Service> services = locateAllServices(name, version, tag, endpointKey);
            if (services == null || services.isEmpty()) {
                throw CoordinatorException.retryables.unableToLocateService(name, version, tag,
                        endpointKey);
            }
            Service service = services.get(0);
            URI endpoint = service.getEndpoint(endpointKey);
            if (endpoint == null) {
                throw CoordinatorException.retryables.unableToLocateServiceNoEndpoint(name,
                        version, tag, endpointKey);
            }

            // check local host IPv6/IPv4
            endpoint = getInetAddessLookupMap().expandURI(endpoint);

            if (endpoint.getScheme().equals("rmi")) {
                RmiInvocationHandler handler = new RmiInvocationHandler();
                handler.setName(name);
                handler.setVersion(version);
                handler.setTag(tag);
                handler.setEndpointKey(endpointKey);
                handler.setEndpointInterface(clazz);
                handler.setCoordinator(this);
                proxy = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz },
                        handler);
                _proxyCache.putIfAbsent(key, proxy);
            } else {
                throw CoordinatorException.retryables.unsupportedEndPointSchema(endpoint
                        .getScheme());
            }
        }
        return clazz.cast(proxy);
    }

    /**
     * Helper to retrieve zk service node children
     * Note that it could return an empty list if there's no ZNode under the specified path
     * 
     * @param serviceRoot
     *            path under /service
     * @return child node names under /service/<serviceRoot>
     * @throws CoordinatorException
     */
    private List<String> lookupServicePath(String serviceRoot) throws CoordinatorException {
        List<String> services = null;
        String fullPath = null;
        try {
            fullPath = String.format("%1$s/%2$s", ZkPath.SERVICE.toString(), serviceRoot);
            services = _zkConnection.curator().getChildren().forPath(fullPath);
        } catch (KeeperException.NoNodeException e) {
            throw CoordinatorException.retryables.cannotFindNode(fullPath, e);
        } catch (Exception e) {
            throw CoordinatorException.retryables.errorWhileFindingNode(fullPath, e);
        }

        if (services == null)
            return new ArrayList<>();

        return services;
    }

    /**
     * Convenience method for retrieving zk node data for a given service matching id at
     * /service/<serviceRoot>/<id>
     * 
     * @param serviceRoot
     *            service path (includes name and version)
     * @param id
     *            service UUID
     * @return zk node content if node exists. null if no node with given id / path exists
     */
    private byte[] getServiceData(String serviceRoot, String id) {
        byte[] data = null;
        try {
            data = _zkConnection
                    .curator()
                    .getData()
                    .forPath(
                            String.format("%1$s/%2$s/%3$s", ZkPath.SERVICE.toString(), serviceRoot,
                                    id));
            return data;
        } catch (Exception e) {
            log.warn("e=", e);
            }
        return data;
    }

    @Override
    public List<Service> locateAllServices(String name, String version, String tag,
            String endpointKey) throws CoordinatorException {
        String serviceRoot = String.format("%1$s/%2$s", name, version);
        List<String> servicePaths = lookupServicePath(serviceRoot);

        if (servicePaths.size() == 0) {
            throw CoordinatorException.retryables.cannotLocateService(String.format("%1$s/%2$s",
                    ZkPath.SERVICE.toString(), serviceRoot));
        }
        // poor man's load balancing
        Collections.shuffle(servicePaths);

        List<Service> filtered = new ArrayList<Service>(servicePaths.size());
        for (int i = 0; i < servicePaths.size(); i++) {
            String spath = servicePaths.get(i);
            byte[] data = getServiceData(serviceRoot, spath);
            if (data == null) {
                continue;
            }
            Service service = ServiceImpl.parse(data);
            if (tag != null && !service.isTagged(tag)) {
                continue;
            }
            if (endpointKey != null && service.getEndpoint(endpointKey) == null) {
                continue;
            }

            if (endpointKey == null) {
                // default endpoint
                ((ServiceImpl) service).setEndpoint(getInetAddessLookupMap().expandURI(
                        service.getEndpoint()));
            } else {
                // swap the ip for the entry with the endpointkey in the map
                ((ServiceImpl) service).setEndpoint(endpointKey, getInetAddessLookupMap()
                        .expandURI(service.getEndpoint(endpointKey)));
            }
            log.debug("locateAllServices->service endpoint: " + service.getEndpoint());
            filtered.add(service);
        }
        return Collections.unmodifiableList(filtered);
    }

    @Override
    public List<Service> locateAllSvcsAllVers(String name) throws CoordinatorException {
        List<String> svcVerPaths = lookupServicePath(name);
        List<Service> allActiveSvcs = new ArrayList<>();
        for (String version : svcVerPaths) {
            log.debug("locateAllSvcsAllVers->service version: {}", version);
            String serviceRoot = String.format("%1$s/%2$s", name, version);
            List<String> servicePaths = lookupServicePath(serviceRoot);

            for (String spath : servicePaths) {
                byte[] data = getServiceData(serviceRoot, spath);
                if (data == null)
                    continue;
                Service service = ServiceImpl.parse(data);
                allActiveSvcs.add(service);
            }
        }
        return Collections.unmodifiableList(allActiveSvcs);
    }

    @Override
   public <T> DistributedQueue<T> getQueue(String name, DistributedQueueConsumer<T> consumer,
            QueueSerializer<T> serializer, int maxThreads, int maxItem) throws CoordinatorException {
        DistributedQueue<T> queue = new DistributedQueueImpl<T>(_zkConnection, consumer,
                serializer, name, maxThreads, maxItem);
        queue.start();
        return queue;
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name, DistributedQueueConsumer<T> consumer,
            QueueSerializer<T> serializer, int maxThreads) throws CoordinatorException {
        DistributedQueue<T> queue = new DistributedQueueImpl<T>(_zkConnection, consumer,
                serializer, name, maxThreads);
        queue.start();
        return queue;
    }

    @Override
    public WorkPool getWorkPool(String name, WorkPool.WorkAssignmentListener listener)
            throws CoordinatorException {
        WorkPool pool = new WorkPoolImpl(_zkConnection, listener, String.format("%1$s/%2$s",
                ZkPath.WORKPOOL.toString(), name));
        try {
            pool.start();
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToGetWorkPool(name, e);
        }
        return pool;
    }

    @Override
    public DistributedSemaphore getSemaphore(String name, int maxPermits)
            throws CoordinatorException {
        DistributedSemaphore semaphore = new DistributedSemaphoreImpl(_zkConnection, String.format(
                "%1$s/%2$s", ZkPath.SEMAPHORE.toString(), name), maxPermits);
        semaphore.start();
        return semaphore;
    }

    @Override
    public InterProcessLock getLock(String name) throws CoordinatorException {
        EnsurePath path = new EnsurePath(ZkPath.MUTEX.toString());
        try {
            path.ensure(_zkConnection.curator().getZookeeperClient());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw CoordinatorException.fatals.unableToGetLock(name, e);
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToGetLock(name, e);
        }
        String lockPath = ZKPaths.makePath(ZkPath.MUTEX.toString(), name);
        return new InterProcessMutex(_zkConnection.curator(), lockPath);
    }

    @Override
    public InterProcessReadWriteLock getReadWriteLock(String name) throws CoordinatorException {
        EnsurePath path = new EnsurePath(ZkPath.MUTEX.toString());
        try {
            path.ensure(_zkConnection.curator().getZookeeperClient());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw CoordinatorException.fatals.unableToGetLock(name, e);
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToGetLock(name, e);
        }
        String lockPath = ZKPaths.makePath(ZkPath.MUTEX.toString(), name);
        return new InterProcessReadWriteLock(_zkConnection.curator(), lockPath);
    }

    @Override
    public InterProcessSemaphoreMutex getSemaphoreLock(String name) throws CoordinatorException {
        EnsurePath path = new EnsurePath(ZkPath.MUTEX.toString());
        try {
            path.ensure(_zkConnection.curator().getZookeeperClient());
        } catch (Exception e) {
            throw new RetryableCoordinatorException(ServiceCode.COORDINATOR_SVC_NOT_FOUND, e,
                   "Unable to get lock {0}. Caused by: {1}", new Object[] { name, e.getMessage() });
        }
        String lockPath = ZKPaths.makePath(ZkPath.MUTEX.toString(), name);
        return new InterProcessSemaphoreMutex(_zkConnection.curator(), lockPath);
    }

    @Override
    public DistributedPersistentLock getPersistentLock(String lockName) throws CoordinatorException {
        DistributedPersistentLock lock = new DistributedPersistentLockImpl(_zkConnection,
                ZkPath.PERSISTENTLOCK.toString(), lockName);
        try {
            lock.start();
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToGetPersistentLock(lockName, e);
        }
        return lock;
    }

    @Override
    public LeaderLatch getLeaderLatch(String latchPath) {
        LeaderLatch leaderLatch = new LeaderLatch(_zkConnection.curator(), latchPath);
        return leaderLatch;
    }
  
   /**
     * Get property
     *
     * This method gets target properties from coordinator service as a string
     * and merges it with the defaults and the ovf properties
     * Syssvc is responsible for publishing the target property information into coordinator
     *
     * @return property object
     * @throws CoordinatorException
     */
    @Override
    public PropertyInfo getPropertyInfo() throws CoordinatorException {
        PropertyInfo info = new PropertyInfo();
        Map<String, String> defaults = new HashMap<String,String>((Map)defaultProperties);
        final Configuration config = queryConfiguration(TARGET_PROPERTY, TARGET_PROPERTY_ID);
        if ( null == config || null == config.getConfig(TARGET_INFO) ) {
            log.debug("getPropertyInfo(): no properties saved in coordinator returning defaults");
            info.setProperties(defaults);
        } else {
            final String infoStr = config.getConfig(TARGET_INFO);
            try {
                log.debug("getPropertyInfo(): properties saved in coordinator=" + Strings.repr(infoStr));
                info.setProperties(mergeProps(defaults, decodeFromString(infoStr).getProperties()));               
            } catch (final Exception e) {
                throw CoordinatorException.fatals.unableToDecodeDataFromCoordinator(e);
            }
        }
        // add the ovf properties
        info.getProperties().putAll((Map)ovfProperties);
        return info;
    }
    /**
     * Merge properties
     * @param defaultProps
     * @param overrideProps
     * @return map containing key, value pair
     */
    public static Map<String, String> mergeProps(Map<String, String> defaultProps, Map<String, String> overrideProps) {
        Map<String, String> mergedProps = new HashMap<String, String>(defaultProps);
        for (Map.Entry<String, String> entry : overrideProps.entrySet()) {
                mergedProps.put(entry.getKey(), entry.getValue());
        }
        return mergedProps;
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath)
            throws CoordinatorException {
        DistributedDataManagerImpl dataMgr = new DistributedDataManagerImpl(_zkConnection, basePath);
        return dataMgr;
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath, long maxNodes) throws CoordinatorException {
        DistributedDataManagerImpl dataMgr = new DistributedDataManagerImpl(_zkConnection, basePath, maxNodes);
        return dataMgr;
    }

    @Override
    public DistributedDataManager getWorkflowDataManager() throws CoordinatorException {
        WorkflowDataManagerImpl dataMgr = new WorkflowDataManagerImpl(_zkConnection);
        return dataMgr;
    }

    /**
     * Validate that the product is licensed for the particular license type (Controller or Object).
     * The product is still considered licensed when the license has expired or storage capacity has
     * been exceeded.
     * 
     * @param licenseType
     * @return boolean
     */
    @Override
    public boolean isStorageProductLicensed(LicenseType licenseType) {
        if (PlatformUtils.isOssBuild()) {
            return true;
        }
        return (getLicenseInfo(licenseType) != null);
    }

    /**
     * get License Info from coordinator for the specified license type
     * 
     * @param licenseType
     * @return LicenseInfo
     */
    private LicenseInfo getLicenseInfo(LicenseType licenseType) {
        final Configuration config = queryConfiguration(LicenseInfo.LICENSE_INFO_TARGET_PROPERTY,
                TARGET_PROPERTY_ID);
        if (config == null || config.getConfig(TARGET_INFO) == null) {
            return null;
        }

        final String infoStr = config.getConfig(TARGET_INFO);

        try {
            List<LicenseInfo> licenseInfoList = LicenseInfo.decodeLicenses(infoStr);
            for (LicenseInfo licenseInfo : licenseInfoList) {
                if (licenseType.equals(licenseInfo.getLicenseType())) {
                    log.debug("getLicenseInfo: " + licenseInfo);
                    return licenseInfo;
                }
            }
        } catch (final Exception e) {
            throw CoordinatorException.fatals.unableToDecodeLicense(e);
        }
        log.warn("getLicenseInfo: null");
        return null;
    }

    @Override
    public LeaderSelector getLeaderSelector(String leaderPath, LeaderSelectorListener listener)
            throws CoordinatorException {
        StringBuilder leaderFullPath = new StringBuilder(ZkPath.LEADER.toString());
        leaderFullPath.append("/");
        leaderFullPath.append(leaderPath);

        return new LeaderSelector(_zkConnection.curator(), leaderFullPath.toString(), listener);
    }

    /**
     * Get target info
     * 
     * @param clazz
     * @param <T>
     * @return
     * @throws Exception
     */
    @Override
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz)
            throws Exception {
        final T info = clazz.newInstance();
        final CoordinatorClassInfo coordinatorInfo = info.getCoordinatorClassInfo();
        String id = coordinatorInfo.id;
        String kind = coordinatorInfo.kind;

        return getTargetInfo(clazz, id, kind);
    }

    /**
     * Get target info
     * 
     * @param clazz
     * @param id
     * @param kind
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz, String id,
            String kind) throws Exception {
        final T info = clazz.newInstance();
        final Configuration config = queryConfiguration(kind, id);
        if (config != null && config.getConfig(TARGET_INFO) != null) {
            final String infoStr = config.getConfig(TARGET_INFO);
            log.debug("getTargetInfo({}): info={}", clazz.getName(), Strings.repr(infoStr));

            final T decodeInfo = info.decodeFromString(infoStr);
            log.debug("getTargetInfo({}): info={}", clazz.getName(), decodeInfo);

            return decodeInfo;
        }
        return null;
    }

    /**
     * Get node info from session scope.
     * 
     * @param svc
     * @param clazz
     * @param <T>
     * @return
     * @throws Exception
     */
    private <T extends CoordinatorSerializable> T getNodeSessionScopeInfo(Service svc,
            final Class<T> clazz) throws Exception {
        final T info = clazz.newInstance();
        String attr = info.getCoordinatorClassInfo().attribute;

        final String infoStr = svc.getAttribute(attr);
        final String prefix = svc.getId() + ":" + clazz.getName();
        log.debug("getNodeSessionScopeInfo({}): info={}", prefix, Strings.repr(infoStr));

        final T decodeInfo = info.decodeFromString(infoStr);
        log.debug("getNodeSessionScopeInfo({}): info={}", prefix, decodeInfo);

        return decodeInfo;
    }

    public <T extends CoordinatorSerializable> T getNodeInfo(Service service, String nodeId,
            Class<T> clazz) throws Exception {
        List<Service> svcs = locateAllServices(service.getName(), service.getVersion(),
                (String) null, null);
        for (Service svc : svcs) {
            if (svc.getId().equals(nodeId)) {
                final T state = getNodeSessionScopeInfo(svc, clazz);
                log.debug("getNodeSessionScopeInfo(): node={}: {}", nodeId, state);
                return state;
            }
        }
        return null;
    }

    @Override
    public <T extends CoordinatorSerializable> T queryRuntimeState(String key, Class<T> clazz) throws CoordinatorException {
        String path = ZkPath.STATE + "/" + key;

        try {
            byte[] data = _zkConnection.curator().getData().forPath(path);

            CoordinatorSerializable state = clazz.newInstance();
            return (T)state.decodeFromString(new String(data, "UTF-8"));
        } catch (KeeperException.NoNodeException ignore) {
            // Ignore exception, don't re-throw
            log.debug("Caught exception but ignoring it: " + ignore);
            return null;
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToFindTheState(key, e);
        }
    }

    @Override
    public <T extends CoordinatorSerializable> void persistRuntimeState(String key, T state) throws CoordinatorException {
        String path = ZkPath.STATE + "/" + key;

        try {
            int lastSlash = path.lastIndexOf('/');
            String parentPath = path.substring(0, lastSlash);
            EnsurePath ensurePath = new EnsurePath(parentPath);
            ensurePath.ensure(_zkConnection.curator().getZookeeperClient());
        } catch (Exception e) {
            log.error(String.format("Failed to ensure path to key: %s", path), e);
        }

        try {
            byte[] data = state.encodeAsString().getBytes("UTF-8");

            for (boolean exist = _zkConnection.curator().checkExists().forPath(path) != null; ; exist = !exist) {
                try {
                    if (exist) {
                        _zkConnection.curator().setData().forPath(path, data);
                    } else {
                        _zkConnection.curator().create().forPath(path, data);
                    }
                    break;
                } catch (KeeperException ex) {
                    if (exist && ex.code() == KeeperException.Code.NONODE
                            || !exist && ex.code() == KeeperException.Code.NODEEXISTS) {
                        continue;
                    }

                    throw ex;
                }
            }
        } catch (Exception e) {
            throw CoordinatorException.fatals.unableToPersistTheState(e);
        }
    }

    /**
     * Get control nodes' state
     */
    @Override
    public ClusterInfo.ClusterState getControlNodesState() {
        try {
            // get target repository and configVersion
            final RepositoryInfo targetRepository = getTargetInfo(RepositoryInfo.class);
            final PropertyInfoRestRep targetProperty = getTargetInfo(PropertyInfoExt.class);
            final PowerOffState targetPowerOffState = getTargetInfo(PowerOffState.class);

            // get control nodes' repository and configVersion info
            final Map<Service, RepositoryInfo> controlNodesInfo = getAllNodeInfos(
                    RepositoryInfo.class, CONTROL_NODE_SYSSVC_ID_PATTERN);
            final Map<Service, ConfigVersion> controlNodesConfigVersions = getAllNodeInfos(
                    ConfigVersion.class, CONTROL_NODE_SYSSVC_ID_PATTERN);

            return getControlNodesState(targetRepository, controlNodesInfo, targetProperty,
                    controlNodesConfigVersions, targetPowerOffState);
        } catch (Exception e) {
            log.info("Fail to get the control node information ", e);
            return null;
        }
    }

    /**
     * Get all control nodes' state
     * 
     * @param targetGiven
     *            target repository
     * @param infos
     *            control nodes' repository
     * @param targetPropertiesGiven
     *            target property
     * @param configVersions
     *            control nodes' configVersions
     * @param targetPowerOffState
     *            target poweroff state
     * @return Control nodes' state
     */
    private ClusterInfo.ClusterState getControlNodesState(final RepositoryInfo targetGiven,
            final Map<Service, RepositoryInfo> infos,
            final PropertyInfoRestRep targetPropertiesGiven,
            final Map<Service, ConfigVersion> configVersions,
            final PowerOffState targetPowerOffState) {
        if (targetGiven == null || targetPropertiesGiven == null || targetPowerOffState == null) {
            // only for first time target initializing
            return ClusterInfo.ClusterState.INITIALIZING;
        }

        if (infos == null || infos.size() != getNodeCount() || configVersions == null
                || configVersions.size() != getNodeCount()) {
            return ClusterInfo.ClusterState.DEGRADED;
        }

        // 1st. Find nodes which currents and versions are different from target's
        List<String> differentCurrents = getDifferentCurrentsCommon(targetGiven, infos);
        List<String> differentVersions = getDifferentVersionsCommon(targetGiven, infos);

        // 2nd. Find nodes which configVersions are different from target's
        // Note : we use config version to judge if properties on a node are sync-ed with target's.
        List<String> differentConfigVersions = getDifferentConfigVersionCommon(
                targetPropertiesGiven, configVersions);

        if (targetPowerOffState.getPowerOffState() != PowerOffState.State.NONE) {
            log.info("Control nodes' state POWERINGOFF");
            return ClusterInfo.ClusterState.POWERINGOFF;
        } else if (differentConfigVersions.size() != 0) {
            log.info("Control nodes' state UPDATING: {}", Strings.repr(targetPropertiesGiven));
            return ClusterInfo.ClusterState.UPDATING;
        } else if (differentCurrents.size() == 0 && differentVersions.size() == 0) {
            // check for the extra upgrading states
            if (isDbSchemaVersionChanged()) {
                MigrationStatus status = getMigrationStatus();

                if (status == null) {
                    log.info("Control nodes state is UPGRADING_PREP_DB ");
                    return ClusterInfo.ClusterState.UPGRADING_PREP_DB;
                }

                log.info("Control nodes state is {}", status);
                switch (status) {
                case RUNNING:
                    return ClusterInfo.ClusterState.UPGRADING_CONVERT_DB;
                case FAILED:
                    return ClusterInfo.ClusterState.UPGRADING_FAILED;
                case DONE:
                    break;
                default:
                    log.error(
                            "The current db schema version doesn't match the target db schema version, "
                                    + "but the current migration status is {} ", status);
                }
            }
            log.info("Control nodes' state STABLE");
            return ClusterInfo.ClusterState.STABLE;
        } else if (differentCurrents.size() == 0) {
            log.info("Control nodes' state SYNCING: {}", Strings.repr(differentVersions));
            return ClusterInfo.ClusterState.SYNCING;
        } else if (differentVersions.size() == 0) {
            log.info("Control nodes' state UPGRADING: {}", Strings.repr(differentCurrents));
            return ClusterInfo.ClusterState.UPGRADING;
        } else {
            log.error("Control nodes' in an UNKNOWN state. Target given: {} {}", targetGiven,
                    Strings.repr(infos));
            return ClusterInfo.ClusterState.UNKNOWN;
        }
    }

    /**
     * Get if the DB schema version changed
     */
    public boolean isDbSchemaVersionChanged() {
        String currentVersion = getCurrentDbSchemaVersion();
        String targetVersion = getTargetDbSchemaVersion();
        log.info("currentVersion: {}, targetVersion {} ", currentVersion, targetVersion);
        return !(currentVersion.equals(targetVersion));
    }

    /**
     * Get the current db schema version from ZooKeeper could return null if current schema version
     * is not set.
     */
    @Override
    public String getCurrentDbSchemaVersion() {
        Configuration config = queryConfiguration(DB_CONFIG, GLOBAL_ID);
        if (config == null) {
            return null;
        }

        return config.getConfig(SCHEMA_VERSION);
    }

    /**
     * Get target DB schema version
     */
    @Override
    public String getTargetDbSchemaVersion() {
        return dbVersionInfo.getSchemaVersion();
    }

    public MigrationStatus getMigrationStatus() {
        log.debug("getMigrationStatus: target version: \"{}\"" , getTargetDbSchemaVersion());
        // TODO support geodbsvc
        Configuration config = queryConfiguration(getVersionedDbConfigPath(Constants.DBSVC_NAME, getTargetDbSchemaVersion()),
                                                  GLOBAL_ID);
        if (config == null || config.getConfig(MIGRATION_STATUS) == null) {
            log.debug("config is null");
            return null;
        }
        MigrationStatus status = MigrationStatus.valueOf(config.getConfig(MIGRATION_STATUS));
        log.debug("status: {}", status);
        return status;
    }

    private boolean isGeoDbsvc(String serviceName) {
    	return Constants.GEODBSVC_NAME.equalsIgnoreCase(serviceName);
    }
    
    @Override
    public String getDbConfigPath(String serviceName) {
        return isGeoDbsvc(serviceName) ? Constants.GEODB_CONFIG : Constants.DB_CONFIG;
    }
    
    @Override
    public String getVersionedDbConfigPath(String serviceName, String version) {
    	String kind = getDbConfigPath(serviceName);
        if (version != null) {
            kind = String.format("%s/%s", kind, version);
        }
        return kind;
    }

    /**
     * Get all Node Infos.
     * 
     * @param clazz
     * @param nodeIdFilter
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> Map<Service, T> getAllNodeInfos(Class<T> clazz,
            Pattern nodeIdFilter) throws Exception {
        final Map<Service, T> infos = new HashMap<Service, T>();
        List<Service> allSysSvcs = locateAllServices(sysSvcName, sysSvcVersion, (String) null, null);
        for (Service svc : allSysSvcs) {
            if (nodeIdFilter.matcher(svc.getId()).matches()) {
                try {
                    T info = getNodeSessionScopeInfo(svc, clazz);
                    if (info != null) {
                        infos.put(svc, info);
                    }
                } catch (Exception e) {
                    log.info("Failed to get all node info from {}: {}",
                            svc.getId() + ":" + clazz.getName(), e);
                }
            }
        }
        return infos;
    }

    /**
     * Common method to compare current version with target's current version
     * 
     * @param targetGiven
     *            target repository
     * @param infos
     *            nodes' repository
     * @return list of nodes which current version is different from the target's
     */
    private List<String> getDifferentCurrentsCommon(final RepositoryInfo targetGiven,
            final Map<Service, RepositoryInfo> infos) {
        List<String> differentCurrents = new ArrayList<String>();

        final SoftwareVersion targetCurrent = targetGiven.getCurrentVersion();

        for (Map.Entry<Service, RepositoryInfo> entry : infos.entrySet()) {
            if (!targetCurrent.equals(entry.getValue().getCurrentVersion())) {
                differentCurrents.add(entry.getKey().getId());
            }
        }

        return differentCurrents;
    }

    /**
     * Common method to compare available versions with target's available versions
     * 
     * @param targetGiven
     *            target repository
     * @param infos
     *            nodes' repository
     * @return list of nodes which available versions are different from the target's
     */
    private List<String> getDifferentVersionsCommon(final RepositoryInfo targetGiven,
            final Map<Service, RepositoryInfo> infos) {
        List<String> differentVersions = new ArrayList<String>();

        final List<SoftwareVersion> targetVersions = targetGiven.getVersions();

        for (Map.Entry<Service, RepositoryInfo> entry : infos.entrySet()) {
            if (!targetVersions.equals(entry.getValue().getVersions())) {
                differentVersions.add(entry.getKey().getId());
            }
        }

        return differentVersions;
    }

    /**
     * Common method to compare configVersions with target's configVersion
     * 
     * @param targetPropertiesGiven
     *            target property
     * @param configVersions
     *            nodes' configVersions
     * @return list of nodes which configVersions are different from the target's
     */
    private List<String> getDifferentConfigVersionCommon(
            final PropertyInfoRestRep targetPropertiesGiven,
            final Map<Service, ConfigVersion> configVersions) {
        List<String> differentConfigVersions = new ArrayList<String>();

        for (Map.Entry<Service, ConfigVersion> entry : configVersions.entrySet()) {
            if (targetPropertiesGiven.getProperty(PropertyInfoRestRep.CONFIG_VERSION) != null
                    && !targetPropertiesGiven.getProperty(PropertyInfoRestRep.CONFIG_VERSION)
                            .equals(entry.getValue().getConfigVersion())) {
                differentConfigVersions.add(entry.getKey().getId());
            }
        }

        return differentConfigVersions;
    }

    /**
     * The method to identify and return the node which is currently holding the persistent upgrade
     * lock
     * 
     * @param lockId
     *            - lock id
     * @return NodeHandle - for node which holds the lock null - If no node holds the lock
     */
    @Override
    public String getUpgradeLockOwner(String lockId) {
        try {
            DistributedPersistentLock lock = getPersistentLock(lockId);
            if (lock != null) {
                String lockOwner = lock.getLockOwner();
                if (lockOwner != null) {
                    return lockOwner;
                }
            }
        } catch (Exception e) {
            log.error("Fail to retrieve upgrade lock owner ", e);
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.coordinator.client.service.CoordinatorClient#isClusterUpgradable
     * ()
     */
    @Override
    public boolean isClusterUpgradable() {
        ClusterInfo.ClusterState controlNodeState = getControlNodesState();

        return (controlNodeState != null && (controlNodeState
                .equals(ClusterInfo.ClusterState.INITIALIZING) || (controlNodeState
                        .equals(ClusterInfo.ClusterState.STABLE) )));
    }

    @Override
    public CoordinatorClientInetAddressMap getInetAddessLookupMap() {
        return inetAddressLookupMap;
    }

    @Override
    public void setInetAddessLookupMap(CoordinatorClientInetAddressMap inetAddessLookupMap) {
        this.inetAddressLookupMap = inetAddessLookupMap;
        this.inetAddressLookupMap.setCoordinatorClient(this);
    }

    @Override
    public void addNodeListener(NodeListener listener) throws Exception {
        nodeWatcher.addListener(listener);
    }

    @Override
    public void removeNodeListener(NodeListener listener) {
        nodeWatcher.removeListener(listener);
    }

     /**
     * To share NodeCache for listeners listening same path.
     * The empty NodeCache (counter zero) means the NodeCache should be closed.
     * Note: it is not thread safe since we do synchronization at higher level
     */
    static class NodeCacheReference {

        private NodeCache cache;
        private int count = 0;

        public NodeCacheReference(NodeCache cache) {
            this.cache = cache;
            count = 1;
        }

        public void plus() {
            count ++;
        }

        public void minus() {
            if (count > 0) {
                count--;
            }
        }

        public NodeCache getInstance() {
            return cache;
        }

        public boolean empty() {
            return (count <= 0);
        }
    }

    /**
     * The class encapsulates the implementation of listening zk node change.
     */
    class NodeCacheWatcher {

        private final Map<String, NodeCacheReference> nodeCacheReferenceMap = new HashMap<>();

        public void addListener(final NodeListener listener) throws Exception {

            NodeCacheReference refer = null;

            synchronized (this) { // to protect multi adding, or adding and removing at same time
                refer = nodeCacheReferenceMap.get(listener.getPath());
                if (refer == null) {
                    refer = createNodeCacheAndStart(listener.getPath());
                } else {
                    refer.plus();
                }
            }

            addListenerToNodeCache(refer, listener);

            // to monitor network connection state change.
            // Note: The _listener is also thread safe.
            _listener.add(listener);

            log.info("Started to listen the node {}", listener.getPath());
        }

        private void addListenerToNodeCache(NodeCacheReference refer, final NodeListener listener) {
            NodeCacheListener nl = new NodeCacheListener() {
                @Override
                public void nodeChanged() throws Exception {
                    log.info("the zk node [ {} ] updated.", listener.getPath());
                    nodeChangeWorker.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                listener.nodeChanged();
                            } catch (Exception e) {
                                log.error("Error raised from the callback nodeChanged()", e);
                            }
                        }
                    });
                }
            };

            refer.getInstance().getListenable().addListener(nl);
        }

        private NodeCacheReference createNodeCacheAndStart(String path) throws Exception {
            NodeCache nodeCache = new NodeCache(_zkConnection.curator(), path);
            NodeCacheReference refer = new NodeCacheReference(nodeCache);
            nodeCacheReferenceMap.put(path, refer);
            nodeCache.start();
            return refer;
        }

        public void removeListener(final NodeListener listener) {

            NodeCacheReference refer = null;

            // remove NodeCacheListener
            synchronized (this) {
                refer = nodeCacheReferenceMap.get(listener.getPath());

                if (refer == null) return;

                refer.minus();
                if (refer.empty()) {
                    nodeCacheReferenceMap.remove(listener.getPath());
                }
            }

            refer.getInstance().getListenable().removeListener(listener);

            if (refer.empty()) {
                // close entire NodeCache when no listener
                // have to put in sync block in case a listener is added before close.
                try {
                    refer.getInstance().close();
                    log.info("The NodeCache [ {} ] has no listener, closed.", listener.getPath());
                } catch (Exception e) {
                    log.warn("Fail to close NodeCache for path" + listener.getPath(), e);
                }
            }

            // remove from ConnectionStateListener list
            _listener.remove(listener);

            log.info("Removed the listener {}", listener.getPath());
        }
    }
}
