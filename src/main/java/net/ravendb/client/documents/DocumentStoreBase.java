package net.ravendb.client.documents;

import net.ravendb.client.documents.bulkInsert.BulkInsertOptions;
import net.ravendb.client.documents.changes.IDatabaseChanges;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.identity.IHiLoIdGenerator;
import net.ravendb.client.documents.indexes.IAbstractIndexCreationTask;
import net.ravendb.client.documents.indexes.IndexCreation;
import net.ravendb.client.documents.indexes.IndexDefinition;
import net.ravendb.client.documents.operations.MaintenanceOperationExecutor;
import net.ravendb.client.documents.operations.OperationExecutor;
import net.ravendb.client.documents.operations.indexes.PutIndexesOperation;
import net.ravendb.client.documents.session.*;
import net.ravendb.client.documents.smuggler.DatabaseSmuggler;
import net.ravendb.client.documents.subscriptions.DocumentSubscriptions;
import net.ravendb.client.documents.timeSeries.TimeSeriesOperations;
import net.ravendb.client.http.AggressiveCacheMode;
import net.ravendb.client.http.RequestExecutor;
import net.ravendb.client.primitives.CleanCloseable;
import net.ravendb.client.primitives.EventHandler;
import net.ravendb.client.primitives.EventHelper;
import net.ravendb.client.primitives.VoidArgs;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 *  Contains implementation of some IDocumentStore operations shared by DocumentStore implementations
 */
public abstract class DocumentStoreBase implements IDocumentStore {

    private final List<EventHandler<BeforeStoreEventArgs>> onBeforeStore = new ArrayList<>();
    private final List<EventHandler<AfterSaveChangesEventArgs>> onAfterSaveChanges = new ArrayList<>();
    private final List<EventHandler<BeforeDeleteEventArgs>> onBeforeDelete = new ArrayList<>();
    private final List<EventHandler<BeforeQueryEventArgs>> onBeforeQuery = new ArrayList<>();
    private final List<EventHandler<SessionCreatedEventArgs>> onSessionCreated = new ArrayList<>();
    private final List<EventHandler<SessionClosingEventArgs>> onSessionClosing = new ArrayList<>();

    private final List<EventHandler<BeforeConversionToDocumentEventArgs>> onBeforeConversionToDocument = new ArrayList<>();
    private final List<EventHandler<AfterConversionToDocumentEventArgs>> onAfterConversionToDocument = new ArrayList<>();
    private final List<EventHandler<BeforeConversionToEntityEventArgs>> onBeforeConversionToEntity = new ArrayList<>();
    private final List<EventHandler<AfterConversionToEntityEventArgs>> onAfterConversionToEntity = new ArrayList<>();
    private final List<EventHandler<BeforeRequestEventArgs>> onBeforeRequest = new ArrayList<>();
    private final List<EventHandler<SucceedRequestEventArgs>> onSucceedRequest = new ArrayList<>();

    private final List<EventHandler<FailedRequestEventArgs>> onFailedRequest = new ArrayList<>();
    private final List<EventHandler<TopologyUpdatedEventArgs>> onTopologyUpdated = new ArrayList<>();

    protected DocumentStoreBase() {
        _subscriptions = new DocumentSubscriptions((DocumentStore)this);
    }

    public abstract void close();

    public abstract void addBeforeCloseListener(EventHandler<VoidArgs> event);

    public abstract void removeBeforeCloseListener(EventHandler<VoidArgs> event);

    public abstract void addAfterCloseListener(EventHandler<VoidArgs> event);

    public abstract void removeAfterCloseListener(EventHandler<VoidArgs> event);

    protected boolean disposed;

    public boolean isDisposed() {
        return disposed;
    }

    public abstract IDatabaseChanges changes();

    public abstract IDatabaseChanges changes(String database);

    public abstract IDatabaseChanges changes(String database, String nodeTag);

    @Override
    public abstract CleanCloseable aggressivelyCacheFor(Duration cacheDuration);

    @Override
    public abstract CleanCloseable aggressivelyCacheFor(Duration cacheDuration, String database);

    @Override
    public abstract CleanCloseable aggressivelyCacheFor(Duration cacheDuration, AggressiveCacheMode mode);

    @Override
    public abstract CleanCloseable aggressivelyCacheFor(Duration cacheDuration, AggressiveCacheMode mode, String database);

    @Override
    public abstract CleanCloseable disableAggressiveCaching();

    @Override
    public abstract CleanCloseable disableAggressiveCaching(String database);

    public abstract IHiLoIdGenerator getHiLoIdGenerator();

    public abstract String getIdentifier();

    public abstract void setIdentifier(String identifier);

    public abstract IDocumentStore initialize();

    public abstract IDocumentSession openSession();

    public abstract IDocumentSession openSession(String database);

    public abstract IDocumentSession openSession(SessionOptions sessionOptions);

    public void executeIndex(IAbstractIndexCreationTask task) {
        executeIndex(task, null);
    }

    public void executeIndex(IAbstractIndexCreationTask task, String database) {
        assertInitialized();
        task.execute(this, conventions, database);
    }

    @Override
    public void executeIndexes(List<IAbstractIndexCreationTask> tasks) {
        executeIndexes(tasks, null);
    }

    @Override
    public void executeIndexes(List<IAbstractIndexCreationTask> tasks, String database) {
        assertInitialized();
        IndexDefinition[] indexesToAdd = IndexCreation.createIndexesToAdd(tasks, conventions);

        maintenance()
                .forDatabase(getEffectiveDatabase(database))
                .send(new PutIndexesOperation(indexesToAdd));
    }

    private TimeSeriesOperations _timeSeriesOperation;

    public TimeSeriesOperations timeSeries() {
        if (_timeSeriesOperation == null) {
            _timeSeriesOperation = new TimeSeriesOperations(this);
        }

        return _timeSeriesOperation;
    }

    private DocumentConventions conventions;

    /**
     * Gets the conventions.
     */
    @Override
    public DocumentConventions getConventions() {
        if (conventions == null) {
            conventions = new DocumentConventions();
        }
        return conventions;
    }

    public void setConventions(DocumentConventions conventions) {
        assertNotInitialized("conventions");
        this.conventions = conventions;
    }

    protected String[] urls = new String[0];

    public String[] getUrls() {
        return urls;
    }

    public void setUrls(String[] value) {
        assertNotInitialized("urls");

        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }

        for (int i = 0; i < value.length; i++) {
            if (value[i] == null)
                throw new IllegalArgumentException("Urls cannot contain null");

            try {
                new URL(value[i]);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("The url '" + value[i] + "' is not valid");
            }

            value[i] = StringUtils.stripEnd(value[i], "/");
        }

        this.urls = value;
    }

    protected boolean initialized;

    private KeyStore _certificate;
    private char[] _certificatePrivateKeyPassword = "".toCharArray();
    private KeyStore _trustStore;

    public abstract BulkInsertOperation bulkInsert();

    public abstract BulkInsertOperation bulkInsert(String database);

    public abstract BulkInsertOperation bulkInsert(String database, BulkInsertOptions options);

    public abstract BulkInsertOperation bulkInsert(BulkInsertOptions options);

    private final DocumentSubscriptions _subscriptions;

    public DocumentSubscriptions subscriptions() {
        return _subscriptions;
    }

    private final ConcurrentMap<String, Long> _lastRaftIndexPerDatabase = new ConcurrentSkipListMap<>(String::compareToIgnoreCase);

    public Long getLastTransactionIndex(String database) {
        Long index = _lastRaftIndexPerDatabase.get(database);
        if (index == null || index == 0) {
            return null;
        }

        return index;
    }

    public void setLastTransactionIndex(String database, Long index) {
        if (index == null) {
            return;
        }

        _lastRaftIndexPerDatabase.compute(database, (__, initialValue) -> {
            if (initialValue == null) {
                return index;
            }
            return Math.max(initialValue, index);
        });
    }

    protected void ensureNotClosed() {
        if (disposed) {
            throw new IllegalStateException("The document store has already been disposed and cannot be used");
        }
    }

    public void assertInitialized() {
        if (!initialized) {
            throw new IllegalStateException("You cannot open a session or access the database commands before initializing the document store. Did you forget calling initialize()?");
        }
    }

    private void assertNotInitialized(String property) {
        if (initialized) {
            throw new IllegalStateException("You cannot set '" + property + "' after the document store has been initialized.");
        }
    }

    public void addBeforeStoreListener(EventHandler<BeforeStoreEventArgs> handler) {
        this.onBeforeStore.add(handler);

    }
    public void removeBeforeStoreListener(EventHandler<BeforeStoreEventArgs> handler) {
        this.onBeforeStore.remove(handler);
    }

    public void addAfterSaveChangesListener(EventHandler<AfterSaveChangesEventArgs> handler) {
        this.onAfterSaveChanges.add(handler);
    }

    public void removeAfterSaveChangesListener(EventHandler<AfterSaveChangesEventArgs> handler) {
        this.onAfterSaveChanges.remove(handler);
    }

    public void addBeforeDeleteListener(EventHandler<BeforeDeleteEventArgs> handler) {
        this.onBeforeDelete.add(handler);
    }
    public void removeBeforeDeleteListener(EventHandler<BeforeDeleteEventArgs> handler) {
        this.onBeforeDelete.remove(handler);
    }

    public void addBeforeQueryListener(EventHandler<BeforeQueryEventArgs> handler) {
        this.onBeforeQuery.add(handler);
    }

    public void removeBeforeQueryListener(EventHandler<BeforeQueryEventArgs> handler) {
        this.onBeforeQuery.remove(handler);
    }

    public void addOnSessionClosingListener(EventHandler<SessionClosingEventArgs> handler) {
        this.onSessionClosing.add(handler);
    }

    public void removeOnSessionClosingListener(EventHandler<SessionClosingEventArgs> handler) {
        this.onSessionClosing.remove(handler);
    }

    public void addBeforeConversionToDocumentListener(EventHandler<BeforeConversionToDocumentEventArgs> handler) {
        this.onBeforeConversionToDocument.add(handler);
    }

    public void removeBeforeConversionToDocumentListener(EventHandler<BeforeConversionToDocumentEventArgs> handler) {
        this.onBeforeConversionToDocument.remove(handler);
    }

    public void addAfterConversionToDocumentListener(EventHandler<AfterConversionToDocumentEventArgs> handler) {
        this.onAfterConversionToDocument.add(handler);
    }

    public void removeAfterConversionToDocumentListener(EventHandler<AfterConversionToDocumentEventArgs> handler) {
        this.onAfterConversionToDocument.remove(handler);
    }

    public void addBeforeConversionToEntityListener(EventHandler<BeforeConversionToEntityEventArgs> handler) {
        this.onBeforeConversionToEntity.add(handler);
    }

    public void removeBeforeConversionToEntityListener(EventHandler<BeforeConversionToEntityEventArgs> handler) {
        this.onBeforeConversionToEntity.remove(handler);
    }

    public void addAfterConversionToEntityListener(EventHandler<AfterConversionToEntityEventArgs> handler) {
        this.onAfterConversionToEntity.add(handler);
    }

    public void removeAfterConversionToEntityListener(EventHandler<AfterConversionToEntityEventArgs> handler) {
        this.onAfterConversionToEntity.remove(handler);
    }

    public void addOnBeforeRequestListener(EventHandler<BeforeRequestEventArgs> handler) {
        assertNotInitialized("onSucceedRequest");
        this.onBeforeRequest.add(handler);
    }

    public void removeOnBeforeRequestListener(EventHandler<BeforeRequestEventArgs> handler) {
        assertNotInitialized("onSucceedRequest");
        this.onBeforeRequest.remove(handler);
    }

    public void addOnSucceedRequestListener(EventHandler<SucceedRequestEventArgs> handler) {
        assertNotInitialized("onSucceedRequest");
        this.onSucceedRequest.add(handler);
    }

    public void removeOnSucceedRequestListener(EventHandler<SucceedRequestEventArgs> handler) {
        assertNotInitialized("onSucceedRequest");
        this.onSucceedRequest.remove(handler);
    }

    public void addOnFailedRequestListener(EventHandler<FailedRequestEventArgs> handler) {
        assertNotInitialized("onFailedRequest");
        this.onFailedRequest.add(handler);
    }

    public void removeOnFailedRequestListener(EventHandler<FailedRequestEventArgs> handler) {
        assertNotInitialized("onFailedRequest");
        this.onFailedRequest.remove(handler);
    }

    public void addOnTopologyUpdatedListener(EventHandler<TopologyUpdatedEventArgs> handler) {
        assertNotInitialized("onTopologyUpdated");
        this.onTopologyUpdated.add(handler);
    }

    public void removeOnTopologyUpdatedListener(EventHandler<TopologyUpdatedEventArgs> handler) {
        assertNotInitialized("onTopologyUpdated");
        this.onTopologyUpdated.remove(handler);
    }

    protected String database;

    /**
     * Gets the default database
     */
    @Override
    public String getDatabase() {
        return database;
    }

    /**
     * Sets the default database
     * @param database Sets the value
     */
    public void setDatabase(String database) {
        assertNotInitialized("database");
        this.database = database;
    }

    /**
     * The client certificate to use for authentication
     * @return Certificate to use
     */
    public KeyStore getCertificate() {
        return _certificate;
    }

    /**
     * The client certificate to use for authentication
     * @param certificate Certificate to use
     */
    public void setCertificate(KeyStore certificate) {
        assertNotInitialized("certificate");
        _certificate = certificate;
    }

    /**
     * Password used for private key encryption
     * @return Private key password
     */
    public char[] getCertificatePrivateKeyPassword() {
        return _certificatePrivateKeyPassword;
    }

    /**
     * If private key is inside certificate is encrypted, you can specify password
     * @param certificatePrivateKeyPassword Private key password
     */
    public void setCertificatePrivateKeyPassword(char[] certificatePrivateKeyPassword) {
        assertNotInitialized("certificatePrivateKeyPassword");
        _certificatePrivateKeyPassword = certificatePrivateKeyPassword;
    }

    public KeyStore getTrustStore() {
        return _trustStore;
    }

    public void setTrustStore(KeyStore trustStore) {
        this._trustStore = trustStore;
    }

    public abstract DatabaseSmuggler smuggler();

    public abstract RequestExecutor getRequestExecutor();

    public abstract RequestExecutor getRequestExecutor(String databaseName);

    @Override
    public CleanCloseable aggressivelyCache() {
        return aggressivelyCache(null);
    }

    @Override
    public CleanCloseable aggressivelyCache(String database) {
        return aggressivelyCacheFor(conventions.aggressiveCache().getDuration(), database);
    }

    protected void registerEvents(InMemoryDocumentSessionOperations session) {
        for (EventHandler<BeforeStoreEventArgs> handler : onBeforeStore) {
            session.addBeforeStoreListener(handler);
        }

        for (EventHandler<AfterSaveChangesEventArgs> handler : onAfterSaveChanges) {
            session.addAfterSaveChangesListener(handler);
        }

        for (EventHandler<BeforeDeleteEventArgs> handler : onBeforeDelete) {
            session.addBeforeDeleteListener(handler);
        }

        for (EventHandler<BeforeQueryEventArgs> handler : onBeforeQuery) {
            session.addBeforeQueryListener(handler);
        }

        for (EventHandler<BeforeConversionToDocumentEventArgs> handler : onBeforeConversionToDocument) {
            session.addBeforeConversionToDocumentListener(handler);
        }

        for (EventHandler<AfterConversionToDocumentEventArgs> handler : onAfterConversionToDocument) {
            session.addAfterConversionToDocumentListener(handler);
        }

        for (EventHandler<BeforeConversionToEntityEventArgs> handler : onBeforeConversionToEntity) {
            session.addBeforeConversionToEntityListener(handler);
        }

        for (EventHandler<AfterConversionToEntityEventArgs> handler : onAfterConversionToEntity) {
            session.addAfterConversionToEntityListener(handler);
        }

        for (EventHandler<SessionClosingEventArgs> handler : onSessionClosing) {
            session.addOnSessionClosingListener(handler);
        }
    }

    public void registerEvents(RequestExecutor requestExecutor) {
        for (EventHandler<FailedRequestEventArgs> handler : onFailedRequest) {
            requestExecutor.addOnFailedRequestListener(handler);
        }

        for (EventHandler<TopologyUpdatedEventArgs> handler : onTopologyUpdated) {
            requestExecutor.addOnTopologyUpdatedListener(handler);
        }

        for (EventHandler<BeforeRequestEventArgs> handler : onBeforeRequest) {
            requestExecutor.addOnBeforeRequestListener(handler);
        }

        for (EventHandler<SucceedRequestEventArgs> handler : onSucceedRequest) {
            requestExecutor.addOnSucceedRequestListener(handler);
        }
    }

    protected void afterSessionCreated(InMemoryDocumentSessionOperations session) {
        EventHelper.invoke(onSessionCreated, this, new SessionCreatedEventArgs(session));
    }

    public abstract MaintenanceOperationExecutor maintenance();

    public abstract OperationExecutor operations();

    public abstract CleanCloseable setRequestTimeout(Duration timeout);

    public abstract CleanCloseable setRequestTimeout(Duration timeout, String database);

    public String getEffectiveDatabase(String database) {
        return DocumentStoreBase.getEffectiveDatabase(this, database);
    }

    public static String getEffectiveDatabase(IDocumentStore store, String database) {
        if (database == null) {
            database = store.getDatabase();
        }

        if (StringUtils.isNotBlank(database)) {
            return database;
        }

        throw new IllegalArgumentException("Cannot determine database to operate on. " +
                "Please either specify 'database' directly as an action parameter " +
                "or set the default database to operate on using 'DocumentStore.setDatabase' method. " +
                "Did you forget to pass 'database' parameter?");
    }
}
