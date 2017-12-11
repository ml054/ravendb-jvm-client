package net.ravendb.client.documents;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.indexes.AbstractIndexCreationTask;
import net.ravendb.client.documents.operations.MaintenanceOperationExecutor;
import net.ravendb.client.documents.operations.OperationExecutor;
import net.ravendb.client.documents.session.*;
import net.ravendb.client.http.RequestExecutor;
import net.ravendb.client.primitives.CleanCloseable;
import net.ravendb.client.primitives.EventHandler;
import net.ravendb.client.util.IDisposalNotification;

import java.security.KeyStore;
import java.util.List;

/**
 * Interface for managing access to RavenDB and open sessions.
 */
public interface IDocumentStore extends IDisposalNotification {

    KeyStore getCertificate();

    void addBeforeStoreListener(EventHandler<BeforeStoreEventArgs> handler);
    void removeBeforeStoreListener(EventHandler<BeforeStoreEventArgs> handler);

    void addAfterStoreListener(EventHandler<AfterStoreEventArgs> handler);
    void removeAfterStoreListener(EventHandler<AfterStoreEventArgs> handler);

    void addBeforeDeleteListener(EventHandler<BeforeDeleteEventArgs> handler);
    void removeBeforeDeleteListener(EventHandler<BeforeDeleteEventArgs> handler);

    void addBeforeQueryExecutedListener(EventHandler<BeforeQueryExecutedEventArgs> handler);
    void removeBeforeQueryExecutedListener(EventHandler<BeforeQueryExecutedEventArgs> handler);

    //TBD: IDatabaseChanges Changes(string database = null);
    //TBD: IDisposable AggressivelyCacheFor(TimeSpan cacheDuration, string database = null);
    //TBD IDisposable AggressivelyCache(string database = null);

    /**
     * Setup the context for no aggressive caching
     *
     * This is mainly useful for internal use inside RavenDB, when we are executing
     * queries that have been marked with WaitForNonStaleResults, we temporarily disable
     * aggressive caching.
     */
    CleanCloseable disableAggressiveCaching();

    /**
     * Setup the context for no aggressive caching
     *
     * This is mainly useful for internal use inside RavenDB, when we are executing
     * queries that have been marked with WaitForNonStaleResults, we temporarily disable
     * aggressive caching.
     */
    CleanCloseable disableAggressiveCaching(String database);

    /**
     * @return Gets the identifier for this store.
     */
    String getIdentifier();

    /**
     * Sets the identifier for this store.
     */
    void setIdentifier(String identifier);

    /**
     * Initializes this instance.
     */
    IDocumentStore initialize();


    /**
     * Opens the session
     */
    IDocumentSession openSession();

    /**
     * Opens the session for a particular database
     */
    IDocumentSession openSession(String database);

    /**
     * Opens the session with the specified options.
     */
    IDocumentSession openSession(SessionOptions sessionOptions);

    /**
     * Executes the index creation
     */
    void executeIndex(AbstractIndexCreationTask task);

    void executeIndex(AbstractIndexCreationTask task, String database);

    void executeIndexes(List<AbstractIndexCreationTask> tasks);

    void executeIndexes(List<AbstractIndexCreationTask> tasks, String database);

    /**
     * Gets the conventions
     */
    DocumentConventions getConventions();

    /**
     * Gets the URL's
     */
    String[] getUrls();

    //TBD: BulkInsertOperation BulkInsert(string database = null);
    //TBD: IReliableSubscriptions Subscriptions { get; }

    String getDatabase();

    RequestExecutor getRequestExecutor();

    RequestExecutor getRequestExecutor(String databaseName);

    MaintenanceOperationExecutor maintenance();

    OperationExecutor operations();

}
