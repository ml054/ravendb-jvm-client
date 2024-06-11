package net.ravendb.client.documents;

import net.ravendb.client.documents.bulkInsert.BulkInsertOptions;
import net.ravendb.client.documents.changes.DatabaseChanges;
import net.ravendb.client.documents.changes.DatabaseChangesOptions;
import net.ravendb.client.documents.changes.EvictItemsFromCacheBasedOnChanges;
import net.ravendb.client.documents.changes.IDatabaseChanges;
import net.ravendb.client.documents.identity.IHiLoIdGenerator;
import net.ravendb.client.documents.identity.MultiDatabaseHiLoIdGenerator;
import net.ravendb.client.documents.operations.MaintenanceOperationExecutor;
import net.ravendb.client.documents.operations.OperationExecutor;
import net.ravendb.client.documents.session.DocumentSession;
import net.ravendb.client.documents.session.IDocumentSession;
import net.ravendb.client.documents.session.SessionOptions;
import net.ravendb.client.documents.smuggler.DatabaseSmuggler;
import net.ravendb.client.http.AggressiveCacheMode;
import net.ravendb.client.http.AggressiveCacheOptions;
import net.ravendb.client.http.RequestExecutor;
import net.ravendb.client.primitives.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.hc.core5.ssl.SSLContexts;

import javax.crypto.BadPaddingException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Manages access to RavenDB and open sessions to work with RavenDB.
 */
public class DocumentStore extends DocumentStoreBase {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final ConcurrentMap<DatabaseChangesOptions, IDatabaseChanges> _databaseChanges = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Lazy<EvictItemsFromCacheBasedOnChanges>> _aggressiveCacheChanges = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Lazy<RequestExecutor>> requestExecutors = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    private MultiDatabaseHiLoIdGenerator _multiDbHiLo;

    private MaintenanceOperationExecutor maintenanceOperationExecutor;
    private OperationExecutor operationExecutor;

    private DatabaseSmuggler _smuggler;

    private String identifier;

    @Override
    public IHiLoIdGenerator getHiLoIdGenerator() {
        return _multiDbHiLo;
    }

    public DocumentStore(String url, String database) {
        this.setUrls(new String[]{url});
        this.setDatabase(database);
    }

    public DocumentStore(String[] urls, String database) {
        this.setUrls(urls);
        this.setDatabase(database);
    }

    public DocumentStore() {

    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Gets the identifier for this store.
     */
    public String getIdentifier() {
        if (identifier != null) {
            return identifier;
        }

        if (urls == null) {
            return null;
        }

        if (database != null) {
            return String.join(",", urls) + " (DB: " + database + ")";
        }

        return String.join(",", urls);
    }

    /**
     * Sets the identifier for this store.
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @SuppressWarnings("EmptyTryBlock")
    public void close() {
        EventHelper.invoke(beforeClose, this, EventArgs.EMPTY);

        for (Lazy<EvictItemsFromCacheBasedOnChanges> value : _aggressiveCacheChanges.values()) {
            if (!value.isValueCreated()) {
                continue;
            }

            value.getValue().close();
        }

        for (IDatabaseChanges changes : _databaseChanges.values()) {
            try (CleanCloseable value = changes) {
                // try will close all values
            }
        }

        if (_multiDbHiLo != null) {
            try {
                _multiDbHiLo.returnUnusedRange();
            } catch (Exception e) {
                // ignore
            }
        }

        if (subscriptions() != null) {
            subscriptions().close();
        }

        disposed = true;

        EventHelper.invoke(new ArrayList<>(afterClose), this, EventArgs.EMPTY);

        for (Map.Entry<String, Lazy<RequestExecutor>> kvp : requestExecutors.entrySet()) {
            if (!kvp.getValue().isValueCreated()) {
                continue;
            }

            kvp.getValue().getValue().close();
        }

        executorService.shutdown();
    }

    /**
     * Opens the session.
     */
    @Override
    public IDocumentSession openSession() {
        SessionOptions sessionOptions = new SessionOptions();
        sessionOptions.setDisableAtomicDocumentWritesInClusterWideTransaction(getConventions().getDisableAtomicDocumentWritesInClusterWideTransaction());
        return openSession(sessionOptions);
    }

    /**
     * Opens the session for a particular database
     */
    @Override
    public IDocumentSession openSession(String database) {
        SessionOptions sessionOptions = new SessionOptions();
        sessionOptions.setDatabase(database);
        sessionOptions.setDisableAtomicDocumentWritesInClusterWideTransaction(getConventions().getDisableAtomicDocumentWritesInClusterWideTransaction());

        return openSession(sessionOptions);
    }

    @Override
    public IDocumentSession openSession(SessionOptions options) {
        assertInitialized();
        ensureNotClosed();

        UUID sessionId = UUID.randomUUID();
        DocumentSession session = new DocumentSession(this, sessionId, options);
        registerEvents(session);
        afterSessionCreated(session);
        return session;
    }

    @Override
    public RequestExecutor getRequestExecutor() {
        return getRequestExecutor(null);
    }

    @Override
    public RequestExecutor getRequestExecutor(String database) {
        assertInitialized();

        database = getEffectiveDatabase(database);

        Lazy<RequestExecutor> executor = requestExecutors.get(database);
        if (executor != null) {
            return executor.getValue();
        }

        final String effectiveDatabase = database;

        Supplier<RequestExecutor> createRequestExecutor = () -> {
            RequestExecutor requestExecutor = RequestExecutor.create(getUrls(), effectiveDatabase, getCertificate(), getCertificatePrivateKeyPassword(), getTrustStore(), executorService, getConventions());
            registerEvents(requestExecutor);

            return requestExecutor;
        };

        Supplier<RequestExecutor> createRequestExecutorForSingleNode = () -> {
            RequestExecutor forSingleNode = RequestExecutor.createForSingleNodeWithConfigurationUpdates(getUrls()[0], effectiveDatabase, getCertificate(), getCertificatePrivateKeyPassword(), getTrustStore(), executorService, getConventions());
            registerEvents(forSingleNode);

            return forSingleNode;
        };

        if (!getConventions().isDisableTopologyUpdates()) {
            executor = new Lazy<>(createRequestExecutor);
        } else {
            executor = new Lazy<>(createRequestExecutorForSingleNode);
        }

        requestExecutors.put(database, executor);

        return executor.getValue();
    }

    @Override
    public CleanCloseable setRequestTimeout(Duration timeout) {
        return setRequestTimeout(timeout, null);
    }

    @Override
    public CleanCloseable setRequestTimeout(Duration timeout, String database) {
        assertInitialized();

        database = this.getEffectiveDatabase(database);

        RequestExecutor requestExecutor = getRequestExecutor(database);
        Duration oldTimeout = requestExecutor.getDefaultTimeout();
        requestExecutor.setDefaultTimeout(timeout);

        return () -> requestExecutor.setDefaultTimeout(oldTimeout);
    }

    /**
     * Initializes this instance.
     */
    @Override
    public IDocumentStore initialize() {
        if (initialized) {
            return this;
        }

        assertValidConfiguration();

        RequestExecutor.validateUrls(urls, getCertificate());

        try {
            if (getConventions().getDocumentIdGenerator() == null) { // don't overwrite what the user is doing
                MultiDatabaseHiLoIdGenerator generator = new MultiDatabaseHiLoIdGenerator(this);
                _multiDbHiLo = generator;

                getConventions().setDocumentIdGenerator(generator::generateDocumentId);
            }

            getConventions().freeze();
            initialized = true;
        } catch (Exception e) {
            close();
            throw ExceptionsUtils.unwrapException(e);
        }

        return this;
    }


    /**
     * Validate the configuration for the document store
     */
    protected void assertValidConfiguration() {
        if (urls == null || urls.length == 0) {
            throw new IllegalArgumentException("Document store URLs cannot be empty");
        }

        // java specific: check if PFX (if provided) can be read correctly
        if (getCertificate() != null) {
            try {
                // do quick test if PFX can be opened by security provider
                SSLContexts.custom()
                        .loadKeyMaterial(getCertificate(), getCertificatePrivateKeyPassword());
            } catch (UnrecoverableKeyException e) {
                if (e.getCause() instanceof BadPaddingException) {
                    throw new IllegalStateException("Unable to verify certificate. Looks like your security provider " +
                            "can't read keystore properly. Suggested solutions: (1) Provide certificate as PEM file containing both private and public key, " +
                            "(2) Use different security provider (ie. BouncyCastle)", e);
                }
                throw new IllegalStateException("Unable to verify certificate", e);
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                throw new IllegalStateException("Unable to verify certificate", e);
            }
        }
    }

    /**
     * Setup the context for no aggressive caching
     * <p>
     * This is mainly useful for internal use inside RavenDB, when we are executing
     * queries that have been marked with WaitForNonStaleResults, we temporarily disable
     * aggressive caching.
     */
    public CleanCloseable disableAggressiveCaching() {
        return disableAggressiveCaching(null);
    }

    /**
     * Setup the context for no aggressive caching
     * <p>
     * This is mainly useful for internal use inside RavenDB, when we are executing
     * queries that have been marked with WaitForNonStaleResults, we temporarily disable
     * aggressive caching.
     */
    public CleanCloseable disableAggressiveCaching(String databaseName) {
        assertInitialized();
        RequestExecutor re = getRequestExecutor(getEffectiveDatabase(databaseName));
        AggressiveCacheOptions old = re.aggressiveCaching.get();
        re.aggressiveCaching.set(null);

        return () -> re.aggressiveCaching.set(old);
    }

    @Override
    public IDatabaseChanges changes() {
        return changes(null, null);
    }

    @Override
    public IDatabaseChanges changes(String database) {
        return changes(database, null);
    }

    @Override
    public IDatabaseChanges changes(String database, String nodeTag) {
        assertInitialized();

        DatabaseChangesOptions changesOptions = new DatabaseChangesOptions(ObjectUtils.firstNonNull(database, getDatabase()), nodeTag);

        return _databaseChanges.computeIfAbsent(changesOptions, this::createDatabaseChanges);
    }

    protected IDatabaseChanges createDatabaseChanges(DatabaseChangesOptions node) {
        return new DatabaseChanges(getRequestExecutor(node.getDatabaseName()), node.getDatabaseName(), executorService, () -> _databaseChanges.remove(node), node.getNodeTag());
    }

    @Override
    public CleanCloseable aggressivelyCacheFor(Duration cacheDuration) {
        return aggressivelyCacheFor(cacheDuration, getConventions().aggressiveCache().getMode(), null);
    }

    @Override
    public CleanCloseable aggressivelyCacheFor(Duration cacheDuration, String database) {
        return aggressivelyCacheFor(cacheDuration, getConventions().aggressiveCache().getMode(), database);
    }

    @Override
    public CleanCloseable aggressivelyCacheFor(Duration cacheDuration, AggressiveCacheMode mode) {
        return aggressivelyCacheFor(cacheDuration, mode, null);
    }

    @Override
    public CleanCloseable aggressivelyCacheFor(Duration cacheDuration, AggressiveCacheMode mode, String database) {
        assertInitialized();

        database = ObjectUtils.firstNonNull(database, getDatabase());

        if (database == null) {
            throw new IllegalStateException("Cannot use aggressivelyCache and aggressivelyCacheFor without a default database defined " +
                    "unless 'database' parameter is provided. Did you forget to pass 'database' parameter?");
        }

        if (mode != AggressiveCacheMode.DO_NOT_TRACK_CHANGES) {
            listenToChangesAndUpdateTheCache(database);
        }

        RequestExecutor re = getRequestExecutor(database);
        AggressiveCacheOptions old = re.aggressiveCaching.get();

        AggressiveCacheOptions newOptions = new AggressiveCacheOptions(cacheDuration, mode);
        re.aggressiveCaching.set(newOptions);

        return () -> re.aggressiveCaching.set(old);
    }

    private void listenToChangesAndUpdateTheCache(String database) {
        Lazy<EvictItemsFromCacheBasedOnChanges> lazy = _aggressiveCacheChanges.get(database);

        if (lazy == null) {
            lazy = _aggressiveCacheChanges.computeIfAbsent(database, db -> new Lazy<>(() -> new EvictItemsFromCacheBasedOnChanges(this, database)));
        }

        lazy.getValue(); // force evaluation

        lazy.getValue().ensureConnected();
    }

    private final List<EventHandler<VoidArgs>> afterClose = new ArrayList<>();

    private final List<EventHandler<VoidArgs>> beforeClose = new ArrayList<>();

    public void addBeforeCloseListener(EventHandler<VoidArgs> event) {
        this.beforeClose.add(event);
    }

    @Override
    public void removeBeforeCloseListener(EventHandler<VoidArgs> event) {
        this.beforeClose.remove(event);
    }

    public void addAfterCloseListener(EventHandler<VoidArgs> event) {
        this.afterClose.add(event);
    }

    @Override
    public void removeAfterCloseListener(EventHandler<VoidArgs> event) {
        this.afterClose.remove(event);
    }

    @Override
    public DatabaseSmuggler smuggler() {
        if (_smuggler == null) {
            _smuggler = new DatabaseSmuggler(this);
        }

        return _smuggler;
    }

    @Override
    public MaintenanceOperationExecutor maintenance() {
        assertInitialized();

        if (maintenanceOperationExecutor == null) {
            maintenanceOperationExecutor = new MaintenanceOperationExecutor(this);
        }

        return maintenanceOperationExecutor;
    }

    @Override
    public OperationExecutor operations() {
        if (operationExecutor == null) {
            operationExecutor = new OperationExecutor(this);
        }

        return operationExecutor;
    }

    @Override
    public BulkInsertOperation bulkInsert() {
        return bulkInsert(null, null);
    }


    @Override
    public BulkInsertOperation bulkInsert(String database) {
        return bulkInsert(database, null);
    }

    @Override
    public BulkInsertOperation bulkInsert(BulkInsertOptions options) {
        return bulkInsert(null, options);
    }

    @Override
    public BulkInsertOperation bulkInsert(String database, BulkInsertOptions options) {
        assertInitialized();

        return new BulkInsertOperation(getEffectiveDatabase(database), this, options);
    }
}
