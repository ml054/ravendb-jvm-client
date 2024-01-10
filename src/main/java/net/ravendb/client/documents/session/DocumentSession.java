package net.ravendb.client.documents.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Defaults;
import com.google.common.base.Stopwatch;
import net.ravendb.client.Constants;
import net.ravendb.client.documents.CloseableIterator;
import net.ravendb.client.documents.DocumentStore;
import net.ravendb.client.documents.IdTypeAndName;
import net.ravendb.client.documents.Lazy;
import net.ravendb.client.documents.commands.*;
import net.ravendb.client.documents.commands.batches.*;
import net.ravendb.client.documents.commands.multiGet.GetRequest;
import net.ravendb.client.documents.commands.multiGet.GetResponse;
import net.ravendb.client.documents.commands.multiGet.MultiGetCommand;
import net.ravendb.client.documents.indexes.AbstractCommonApiForIndexes;
import net.ravendb.client.documents.linq.IDocumentQueryGenerator;
import net.ravendb.client.documents.operations.PatchRequest;
import net.ravendb.client.documents.operations.timeSeries.AbstractTimeSeriesRange;
import net.ravendb.client.documents.operations.timeSeries.TimeSeriesConfiguration;
import net.ravendb.client.documents.queries.Query;
import net.ravendb.client.documents.session.loaders.*;
import net.ravendb.client.documents.session.operations.*;
import net.ravendb.client.documents.session.operations.lazy.*;
import net.ravendb.client.documents.session.tokens.FieldsToFetchToken;
import net.ravendb.client.documents.timeSeries.TimeSeriesOperations;
import net.ravendb.client.extensions.JsonExtensions;
import net.ravendb.client.json.MetadataAsDictionary;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.primitives.Tuple;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DocumentSession extends InMemoryDocumentSessionOperations
        implements IAdvancedSessionOperations, IDocumentSessionImpl, IDocumentQueryGenerator {

    /**
     * Get the accessor for advanced operations
     *
     * Note: Those operations are rarely needed, and have been moved to a separate
     * property to avoid cluttering the API
     */
    @Override
    public IAdvancedSessionOperations advanced() {
        return this;
    }

    @Override
    public ILazySessionOperations lazily() {
        return new LazySessionOperations(this);
    }

    @Override
    public IEagerSessionOperations eagerly() {
        return this;
    }

    private IAttachmentsSessionOperations _attachments;

    @Override
    public IAttachmentsSessionOperations attachments() {
        if (_attachments == null) {
            _attachments = new DocumentSessionAttachments(this);
        }
        return _attachments;
    }

    private IRevisionsSessionOperations _revisions;

    @Override
    public IRevisionsSessionOperations revisions() {
        if (_revisions == null) {
            _revisions = new DocumentSessionRevisions(this);
        }
        return _revisions;
    }

    private IClusterTransactionOperations _clusterTransaction;

    @Override
    public IClusterTransactionOperations clusterTransaction() {
        if (_clusterTransaction == null) {
            _clusterTransaction = new ClusterTransactionOperations(this);
        }
        return _clusterTransaction;
    }

    @Override
    protected boolean hasClusterSession() {
        return _clusterTransaction != null;
    }

    @Override
    protected void clearClusterSession() {
        if (!hasClusterSession()) {
            return;
        }

        getClusterSession().clear();
    }

    @Override
    public ClusterTransactionOperationsBase getClusterSession() {
        if (_clusterTransaction == null) {
            _clusterTransaction = new ClusterTransactionOperations(this);
        }
        return (ClusterTransactionOperationsBase) _clusterTransaction;
    }

    /**
     * Initializes new DocumentSession
     * @param documentStore Parent document store
     * @param id Identifier
     * @param options SessionOptions
     */
    public DocumentSession(DocumentStore documentStore, UUID id, SessionOptions options) {
        super(documentStore, id, options);
    }

    /**
     * Saves all the changes to the Raven server.
     */
    @Override
    public void saveChanges() {
        BatchOperation saveChangeOperation = new BatchOperation(this);

        try (SingleNodeBatchCommand command = saveChangeOperation.createRequest()) {
            if (command == null) {
                return;
            }

            if (noTracking) {
                throw new IllegalStateException("Cannot execute saveChanges when entity tracking is disabled in session.");
            }

            _requestExecutor.execute(command, sessionInfo);
            updateSessionAfterSaveChanges(command.getResult());
            saveChangeOperation.setResult(command.getResult());
        }
    }

    /**
     * Check if document exists without loading it
     */
    public boolean exists(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }

        if (_knownMissingIds.contains(id)) {
            return false;
        }

        if (documentsById.getValue(id) != null) {
            return true;
        }

        HeadDocumentCommand command = new HeadDocumentCommand(id, null);

        _requestExecutor.execute(command, sessionInfo);

        return command.getResult() != null;
    }

    /**
     * Refreshes the specified entity from Raven server.
     */
    public <T> void refresh(T entity) {
        DocumentInfo documentInfo = documentsByEntity.get(entity);
        if (documentInfo == null) {
            throwCouldNotRefreshDocument("Cannot refresh a transient instance");
        }

        incrementRequestCount();

        GetDocumentsCommand command = new GetDocumentsCommand(getConventions(), new String[]{documentInfo.getId()}, null, false);
        _requestExecutor.execute(command, sessionInfo);

        ObjectNode commandResult = (ObjectNode) command.getResult().getResults().get(0);

        refreshInternal(entity, commandResult, documentInfo);
    }

    /**
     * Refreshes the specified entities from Raven server.
     * @param entities Collection of instances of an entity that will be refreshed
     * @param <T> Type
     */
    @Override
    public <T> void refresh(List<T> entities) {
        Map<String, Pair<Object, DocumentInfo>> idsEntitiesPairs = buildEntityDocInfoByIdHolder(entities);

        incrementRequestCount();

        GetDocumentsCommand command = new GetDocumentsCommand(getConventions(), idsEntitiesPairs.keySet().toArray(new String[0]), null, false);
        _requestExecutor.execute(command, getSessionInfo());

        refreshEntities(command, idsEntitiesPairs);
    }

    /**
     * Generates the document ID.
     */
    @Override
    protected String generateId(Object entity) {
        return getConventions().generateDocumentId(getDatabaseName(), entity);
    }

    public ResponseTimeInformation executeAllPendingLazyOperations() {
        ArrayList<GetRequest> requests = new ArrayList<>();
        for (int i = 0; i < pendingLazyOperations.size(); i++) {
            GetRequest req = pendingLazyOperations.get(i).createRequest();
            if (req == null) {
                pendingLazyOperations.remove(i);
                i--; // so we'll recheck this index
                continue;
            }
            requests.add(req);
        }

        if (requests.isEmpty()) {
            return new ResponseTimeInformation();
        }

        try  {
            Stopwatch sw = Stopwatch.createStarted();

            ResponseTimeInformation responseTimeDuration = new ResponseTimeInformation();

            while (executeLazyOperationsSingleStep(responseTimeDuration, requests, sw)) {
                Thread.sleep(100);
            }

            responseTimeDuration.computeServerTotal();

            for (ILazyOperation pendingLazyOperation : pendingLazyOperations) {
                Consumer<Object> value = onEvaluateLazy.get(pendingLazyOperation);
                if (value != null) {
                    value.accept(pendingLazyOperation.getResult());
                }
            }

            sw.stop();
            responseTimeDuration.setTotalClientDuration(Duration.ofMillis(sw.elapsed(TimeUnit.MILLISECONDS)));
            return responseTimeDuration;
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to execute pending operations: "  + e.getMessage(), e);
        } finally {
            pendingLazyOperations.clear();
        }
    }

    private boolean executeLazyOperationsSingleStep(ResponseTimeInformation responseTimeInformation, List<GetRequest> requests, Stopwatch sw) {
        MultiGetOperation multiGetOperation = new MultiGetOperation(this);
        try (MultiGetCommand multiGetCommand = multiGetOperation.createRequest(requests)) {
            getRequestExecutor().execute(multiGetCommand, sessionInfo);

            List<GetResponse> responses = multiGetCommand.getResult();

            if (!multiGetCommand.aggressivelyCached) {
                incrementRequestCount();
            }

            for (int i = 0; i < pendingLazyOperations.size(); i++) {
                long totalTime;
                String tempReqTime;
                GetResponse response = responses.get(i);

                tempReqTime = response.getHeaders().get(Constants.Headers.REQUEST_TIME);
                response.setElapsed(sw.elapsed());
                totalTime = tempReqTime != null ? Long.parseLong(tempReqTime) : 0;

                ResponseTimeInformation.ResponseTimeItem timeItem = new ResponseTimeInformation.ResponseTimeItem();
                timeItem.setUrl(requests.get(i).getUrlAndQuery());
                timeItem.setDuration(Duration.ofMillis(totalTime));

                responseTimeInformation.getDurationBreakdown().add(timeItem);

                if (response.requestHasErrors()) {
                    throw new IllegalStateException("Got an error from server, status code: " + response.getStatusCode() + System.lineSeparator() + response.getResult());
                }

                pendingLazyOperations.get(i).handleResponse(response);
                if (pendingLazyOperations.get(i).isRequiresRetry()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Begin a load while including the specified path
     */
    public ILoaderWithInclude include(String path) {
        return new MultiLoaderWithInclude(this).include(path);
    }

    public <T> Lazy<T> addLazyOperation(Class<T> clazz, ILazyOperation operation, Consumer<T> onEval) {
        pendingLazyOperations.add(operation);
        Lazy<T> lazyValue = new Lazy<>(() -> {
            executeAllPendingLazyOperations();
            return getOperationResult(clazz, operation.getResult());
        });

        if (onEval != null) {
            onEvaluateLazy.put(operation, theResult -> onEval.accept(getOperationResult(clazz, theResult)));
        }

        return lazyValue;
    }

    protected Lazy<Integer> addLazyCountOperation(ILazyOperation operation) {
        pendingLazyOperations.add(operation);

        return new Lazy<>(() -> {
            executeAllPendingLazyOperations();
            long value = operation.getQueryResult().getTotalResults();
            if (value > Integer.MAX_VALUE) {
                DocumentSession.throwWhenResultsAreOverInt32(value, "addLazyCountOperation", "addLazyCountLongOperation");
            }
            return (int) value;
        });
    }

    protected Lazy<Long> addLazyCountLongOperation(ILazyOperation operation) {
        pendingLazyOperations.add(operation);

        return new Lazy<>(() -> {
            executeAllPendingLazyOperations();
            return operation.getQueryResult().getTotalResults();
        });
    }



    @SuppressWarnings("unchecked")
    @Override
    public <T> Lazy<Map<String, T>> lazyLoadInternal(Class<T> clazz, String[] ids, String[] includes, Consumer<Map<String, T>> onEval) {
        if (checkIfIdAlreadyIncluded(ids, Arrays.asList(includes))) {
            return new Lazy<>(() -> load(clazz, ids));
        }

        LoadOperation loadOperation = new LoadOperation(this)
                .byIds(ids)
                .withIncludes(includes);

        LazyLoadOperation<T> lazyOp = new LazyLoadOperation<>(clazz, this, loadOperation)
                .byIds(ids).withIncludes(includes);

        return addLazyOperation((Class<Map<String, T>>)(Class<?>)Map.class, lazyOp, onEval);
    }

    @Override
    public <T> T load(Class<T> clazz, String id) {
        if (StringUtils.isBlank(id)) {
            return Defaults.defaultValue(clazz);
        }

        LoadOperation loadOperation = new LoadOperation(this);

        loadOperation.byId(id);

        GetDocumentsCommand command = loadOperation.createRequest();

        if (command != null) {
            _requestExecutor.execute(command, sessionInfo);
            loadOperation.setResult(command.getResult());
        }

        return loadOperation.getDocument(clazz);
    }

    public <T> Map<String, T> load(Class<T> clazz, String... ids) {
        if (ids == null) {
            throw new IllegalArgumentException("Ids cannot be null");
        }
        LoadOperation loadOperation = new LoadOperation(this);
        loadInternal(ids, loadOperation, null);
        return loadOperation.getDocuments(clazz);
    }

    /**
     * Loads the specified entities with the specified ids.
     */
    public <T> Map<String, T> load(Class<T> clazz, Collection<String> ids) {
        LoadOperation loadOperation = new LoadOperation(this);
        loadInternal(ids.toArray(new String[0]), loadOperation, null);
        return loadOperation.getDocuments(clazz);
    }

    private <T> void loadInternal(String[] ids, LoadOperation operation, OutputStream stream) {
        operation.byIds(ids);

        GetDocumentsCommand command = operation.createRequest();
        if (command != null) {
            _requestExecutor.execute(command, sessionInfo);

            if (stream != null) {
                try {
                    GetDocumentsResult result = command.getResult();
                    JsonExtensions.getDefaultMapper().writeValue(new OutputStreamWriter(stream, StandardCharsets.UTF_8), result);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to serialize returned value into stream" + e.getMessage(), e);
                }
            } else {
                operation.setResult(command.getResult());
            }
        }
    }

    @Override
    public <T> T load(Class<T> clazz, String id, Consumer<IIncludeBuilder> includes) {
        if (id == null) {
            return null;
        }

        Collection<T> values = load(clazz, Collections.singletonList(id), includes).values();
        return values.isEmpty() ? null : values.iterator().next();
    }

    @Override
    public <TResult> Map<String, TResult> load(Class<TResult> clazz, Collection<String> ids, Consumer<IIncludeBuilder> includes) {
        if (ids == null) {
            throw new IllegalArgumentException("ids cannot be null");
        }

        if (includes == null) {
            return load(clazz, ids);
        }

        IncludeBuilder includeBuilder = new IncludeBuilder(getConventions());
        includes.accept(includeBuilder);

        List<AbstractTimeSeriesRange> timeSeriesIncludes = includeBuilder.getTimeSeriesToInclude() != null
                ? new ArrayList<>(includeBuilder.getTimeSeriesToInclude())
                : null;

        String[] compareExchangeValuesToInclude = includeBuilder.getCompareExchangeValuesToInclude() != null
                ? includeBuilder.getCompareExchangeValuesToInclude().toArray(new String[0])
                : null;

        String[] revisionsToIncludeByChangeVector = includeBuilder.revisionsToIncludeByChangeVector != null
                ? includeBuilder.revisionsToIncludeByChangeVector.toArray(new String[0])
                : null;

        return loadInternal(clazz,
                ids.toArray(new String[0]),
                includeBuilder.documentsToInclude != null ? includeBuilder.documentsToInclude.toArray(new String[0]) : null,
                includeBuilder.getCountersToInclude() != null ? includeBuilder.getCountersToInclude().toArray(new String[0]) : null,
                includeBuilder.isAllCounters(),
                timeSeriesIncludes,
                compareExchangeValuesToInclude,
                revisionsToIncludeByChangeVector,
                includeBuilder.revisionsToIncludeByDateTime);
    }

    public <TResult> Map<String, TResult> loadInternal(Class<TResult> clazz, String[] ids, String[] includes) {
        return loadInternal(clazz, ids, includes, null, false);
    }

    public <TResult> Map<String, TResult> loadInternal(Class<TResult> clazz, String[] ids, String[] includes,
                                                       String[] counterIncludes) {
        return loadInternal(clazz, ids, includes, counterIncludes, false);
    }

    @Override
    public <TResult> Map<String, TResult> loadInternal(Class<TResult> clazz, String[] ids, String[] includes,
                                                       String[] counterIncludes, boolean includeAllCounters) {
        return loadInternal(clazz, ids, includes, counterIncludes, includeAllCounters, null, null);
    }

    @Override
    public <TResult> Map<String, TResult> loadInternal(Class<TResult> clazz, String[] ids, String[] includes,
                                                       String[] counterIncludes, boolean includeAllCounters,
                                                       List<AbstractTimeSeriesRange> timeSeriesIncludes) {
        return loadInternal(clazz, ids, includes, counterIncludes, includeAllCounters, timeSeriesIncludes, null);
    }

    public <TResult> Map<String, TResult> loadInternal(Class<TResult> clazz, String[] ids, String[] includes,
                                                       String[] counterIncludes, boolean includeAllCounters,
                                                       List<AbstractTimeSeriesRange> timeSeriesIncludes,
                                                       String[] compareExchangeValueIncludes) {
        return loadInternal(clazz, ids, includes, counterIncludes, includeAllCounters, timeSeriesIncludes, compareExchangeValueIncludes, null, null);
    }

    public <TResult> Map<String, TResult> loadInternal(Class<TResult> clazz, String[] ids, String[] includes,
                                                       String[] counterIncludes, boolean includeAllCounters,
                                                       List<AbstractTimeSeriesRange> timeSeriesIncludes,
                                                       String[] compareExchangeValueIncludes,
                                                       String[] revisionIncludesByChangeVector,
                                                       Date revisionsToIncludeByDateTime) {
        if (ids == null) {
            throw new IllegalArgumentException("Ids cannot be null");
        }

        LoadOperation loadOperation = new LoadOperation(this);
        loadOperation.byIds(ids);
        loadOperation.withIncludes(includes);

        if (includeAllCounters) {
            loadOperation.withAllCounters();
        } else {
            loadOperation.withCounters(counterIncludes);
        }

        loadOperation.withRevisions(revisionIncludesByChangeVector);
        loadOperation.withRevisions(revisionsToIncludeByDateTime);
        loadOperation.withTimeSeries(timeSeriesIncludes);
        loadOperation.withCompareExchange(compareExchangeValueIncludes);

        GetDocumentsCommand command = loadOperation.createRequest();
        if (command != null) {
            _requestExecutor.execute(command, sessionInfo);
            loadOperation.setResult(command.getResult());
        }

        return loadOperation.getDocuments(clazz);
    }

    public <T> T[] loadStartingWith(Class<T> clazz, String idPrefix) {
        return loadStartingWith(clazz, idPrefix, null, 0, 25, null, null);
    }

    public <T> T[] loadStartingWith(Class<T> clazz, String idPrefix, String matches) {
        return loadStartingWith(clazz, idPrefix, matches, 0, 25, null, null);
    }

    public <T> T[] loadStartingWith(Class<T> clazz, String idPrefix, String matches, int start) {
        return loadStartingWith(clazz, idPrefix, matches, start, 25, null, null);
    }

    public <T> T[] loadStartingWith(Class<T> clazz, String idPrefix, String matches, int start, int pageSize) {
        return loadStartingWith(clazz, idPrefix, matches, start, pageSize, null, null);
    }

    public <T> T[] loadStartingWith(Class<T> clazz, String idPrefix, String matches, int start, int pageSize, String exclude) {
        return loadStartingWith(clazz, idPrefix, matches, start, pageSize, exclude, null);
    }

    public <T> T[] loadStartingWith(Class<T> clazz, String idPrefix, String matches, int start, int pageSize, String exclude, String startAfter) {
        LoadStartingWithOperation loadStartingWithOperation = new LoadStartingWithOperation(this);
        loadStartingWithInternal(idPrefix, loadStartingWithOperation, null, matches, start, pageSize, exclude, startAfter);
        return loadStartingWithOperation.getDocuments(clazz);
    }

    @Override
    public void loadStartingWithIntoStream(String idPrefix, OutputStream output) {
        loadStartingWithIntoStream(idPrefix, output, null, 0, 25, null, null);
    }

    @Override
    public void loadStartingWithIntoStream(String idPrefix, OutputStream output, String matches) {
        loadStartingWithIntoStream(idPrefix, output, matches, 0, 25, null, null);
    }

    @Override
    public void loadStartingWithIntoStream(String idPrefix, OutputStream output, String matches, int start) {
        loadStartingWithIntoStream(idPrefix, output, matches, start, 25, null, null);
    }

    @Override
    public void loadStartingWithIntoStream(String idPrefix, OutputStream output, String matches, int start, int pageSize) {
        loadStartingWithIntoStream(idPrefix, output, matches, start, pageSize, null, null);
    }

    @Override
    public void loadStartingWithIntoStream(String idPrefix, OutputStream output, String matches, int start, int pageSize, String exclude) {
        loadStartingWithIntoStream(idPrefix, output, matches, start, pageSize, exclude, null);
    }

    @Override
    public void loadStartingWithIntoStream(String idPrefix, OutputStream output, String matches, int start, int pageSize, String exclude, String startAfter) {
        if (output == null) {
            throw new IllegalArgumentException("Output cannot be null");
        }
        if (idPrefix == null) {
            throw new IllegalArgumentException("idPrefix cannot be null");
        }
        loadStartingWithInternal(idPrefix, new LoadStartingWithOperation(this), output, matches, start, pageSize, exclude, startAfter);
    }

    @SuppressWarnings("UnusedReturnValue")
    private GetDocumentsCommand loadStartingWithInternal(String idPrefix, LoadStartingWithOperation operation, OutputStream stream,
                                                         String matches, int start, int pageSize, String exclude, String startAfter) {
        operation.withStartWith(idPrefix, matches, start, pageSize, exclude, startAfter);

        GetDocumentsCommand command = operation.createRequest();
        if (command != null) {
            _requestExecutor.execute(command, sessionInfo);

            if (stream != null) {
                try {
                    GetDocumentsResult result = command.getResult();
                    JsonExtensions.getDefaultMapper().writeValue(new OutputStreamWriter(stream, StandardCharsets.UTF_8), result);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to serialize returned value into stream" + e.getMessage(), e);
                }
            } else {
                operation.setResult(command.getResult());
            }
        }
        return command;
    }

    @Override
    public void loadIntoStream(Collection<String> ids, OutputStream output) {
        if (ids == null) {
            throw new IllegalArgumentException("Ids cannot be null");
        }

        loadInternal(ids.toArray(new String[0]), new LoadOperation(this), output);
    }

    @Override
    public <T, U> void increment(T entity, String path, U valueToAdd) {
        IMetadataDictionary metadata = getMetadataFor(entity);
        String id = (String) metadata.get(Constants.Documents.Metadata.ID);
        increment(id, path, valueToAdd);
    }

    private int _valsCount;
    private int _customCount;

    @Override
    public <T, U> void increment(String id, String path, U valueToAdd) {
        PatchRequest patchRequest = new PatchRequest();

        String variable = "this." + path;
        String value = "args.val_" + _valsCount;
        patchRequest.setScript(variable + " = " + variable
                + " ? " + variable + " + " + value
                + " : " + value + ";");
        patchRequest.setValues(Collections.singletonMap("val_" + _valsCount, valueToAdd));

        _valsCount++;

        if (!tryMergePatches(id, patchRequest)) {
            defer(new PatchCommandData(id, null, patchRequest, null));
        }
    }

    @Override
    public <T, TU> void addOrIncrement(String id, T entity, String pathToObject, TU valToAdd) {
        String variable = "this." + pathToObject;
        String value = "args.val_" + _valsCount;

        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setScript(variable + " = " + variable + " ? " + variable + " + " + value + " : " + value);
        patchRequest.setValues(Collections.singletonMap("val_" + _valsCount, valToAdd));

        String collectionName = _requestExecutor.getConventions().getCollectionName(entity);
        String javaType = _requestExecutor.getConventions().getJavaClassName(entity.getClass());

        MetadataAsDictionary metadataAsDictionary = new MetadataAsDictionary();
        metadataAsDictionary.put(Constants.Documents.Metadata.COLLECTION, collectionName);
        metadataAsDictionary.put(Constants.Documents.Metadata.RAVEN_JAVA_TYPE, javaType);

        DocumentInfo documentInfo = new DocumentInfo();
        documentInfo.setId(id);
        documentInfo.setCollection(collectionName);
        documentInfo.setMetadataInstance(metadataAsDictionary);

        ObjectNode newInstance = getEntityToJson().convertEntityToJson(entity, documentInfo);

        _valsCount++;

        PatchCommandData patchCommandData = new PatchCommandData(id, null, patchRequest);
        patchCommandData.setCreateIfMissing(newInstance);
        defer(patchCommandData);
    }

    @Override
    public <T, TU> void addOrPatchArray(String id, T entity, String pathToArray, Consumer<JavaScriptArray<TU>> arrayAdder) {
        JavaScriptArray<TU> scriptArray = new JavaScriptArray<>(_customCount++, pathToArray);

        arrayAdder.accept(scriptArray);

        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setScript(scriptArray.getScript());
        patchRequest.setValues(scriptArray.getParameters());

        String collectionName = _requestExecutor.getConventions().getCollectionName(entity);
        String javaType = _requestExecutor.getConventions().getJavaClassName(entity.getClass());

        MetadataAsDictionary metadataAsDictionary = new MetadataAsDictionary();
        metadataAsDictionary.put(Constants.Documents.Metadata.COLLECTION, collectionName);
        metadataAsDictionary.put(Constants.Documents.Metadata.RAVEN_JAVA_TYPE, javaType);

        DocumentInfo documentInfo = new DocumentInfo();
        documentInfo.setId(id);
        documentInfo.setCollection(collectionName);
        documentInfo.setMetadataInstance(metadataAsDictionary);

        ObjectNode newInstance = getEntityToJson().convertEntityToJson(entity, documentInfo);

        _valsCount++;

        PatchCommandData patchCommandData = new PatchCommandData(id, null, patchRequest);
        patchCommandData.setCreateIfMissing(newInstance);
        defer(patchCommandData);
    }

    @Override
    public <T, TU> void addOrPatch(String id, T entity, String pathToObject, TU value) {
        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setScript("this." + pathToObject + " = args.val_" + _valsCount);
        patchRequest.setValues(Collections.singletonMap("val_" + _valsCount, value));

        String collectionName = _requestExecutor.getConventions().getCollectionName(entity);
        String javaType = _requestExecutor.getConventions().getJavaClassName(entity.getClass());

        MetadataAsDictionary metadataAsDictionary = new MetadataAsDictionary();
        metadataAsDictionary.put(Constants.Documents.Metadata.COLLECTION, collectionName);
        metadataAsDictionary.put(Constants.Documents.Metadata.RAVEN_JAVA_TYPE, javaType);

        DocumentInfo documentInfo = new DocumentInfo();
        documentInfo.setId(id);
        documentInfo.setCollection(collectionName);
        documentInfo.setMetadataInstance(metadataAsDictionary);

        ObjectNode newInstance = getEntityToJson().convertEntityToJson(entity, documentInfo);

        _valsCount++;

        PatchCommandData patchCommandData = new PatchCommandData(id, null, patchRequest);
        patchCommandData.setCreateIfMissing(newInstance);
        defer(patchCommandData);
    }

    @Override
    public <T, U> void patch(T entity, String path, U value) {
        IMetadataDictionary metadata = getMetadataFor(entity);
        String id = (String) metadata.get(Constants.Documents.Metadata.ID);
        patch(id, path, value);
    }

    @Override
    public <T, U> void patch(String id, String path, U value) {
        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setScript("this." + path + " = args.val_" + _valsCount + ";");
        patchRequest.setValues(Collections.singletonMap("val_" + _valsCount, value));

        _valsCount++;

        if (!tryMergePatches(id, patchRequest)) {
            defer(new PatchCommandData(id, null, patchRequest, null));
        }
    }

    @Override
    public <T, U> void patchArray(T entity, String pathToArray, Consumer<JavaScriptArray<U>> arrayAdder) {
        IMetadataDictionary metadata = getMetadataFor(entity);
        String id = (String) metadata.get(Constants.Documents.Metadata.ID);
        patchArray(id, pathToArray, arrayAdder);
    }

    @Override
    public <T, U> void patchArray(String id, String pathToArray, Consumer<JavaScriptArray<U>> arrayAdder) {
        JavaScriptArray<U> scriptArray = new JavaScriptArray<>(_customCount++, pathToArray);

        arrayAdder.accept(scriptArray);

        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setScript(scriptArray.getScript());
        patchRequest.setValues(scriptArray.getParameters());

        if (!tryMergePatches(id, patchRequest)) {
            defer(new PatchCommandData(id, null, patchRequest, null));
        }
    }

    @Override
    public <T, TKey, TValue> void patchObject(T entity, String pathToObject, Consumer<JavaScriptMap<TKey, TValue>> mapAdder) {
        IMetadataDictionary metadata = getMetadataFor(entity);
        String id = (String) metadata.get(Constants.Documents.Metadata.ID);
        patchObject(id, pathToObject, mapAdder);
    }

    @Override
    public <T, TKey, TValue> void patchObject(String id, String pathToObject, Consumer<JavaScriptMap<TKey, TValue>> mapAdder) {
        JavaScriptMap<TKey, TValue> scriptMap = new JavaScriptMap<>(_customCount++, pathToObject);

        mapAdder.accept(scriptMap);

        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setScript(scriptMap.getScript());
        patchRequest.setValues(scriptMap.getParameters());

        if (!tryMergePatches(id, patchRequest)) {
            defer(new PatchCommandData(id, null, patchRequest, null));
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean tryMergePatches(String id, PatchRequest patchRequest) {
        ICommandData command = deferredCommandsMap.get(IdTypeAndName.create(id, CommandType.PATCH, null));
        if (command == null) {
            return false;
        }

        deferredCommands.remove(command);
        // We'll overwrite the deferredCommandsMap when calling Defer
        // No need to call deferredCommandsMap.remove((id, CommandType.PATCH, null));

        PatchCommandData oldPatch = (PatchCommandData) command;
        String newScript = oldPatch.getPatch().getScript() + "\n" + patchRequest.getScript();
        Map<String, Object> newVals = new HashMap<>(oldPatch.getPatch().getValues());

        newVals.putAll(patchRequest.getValues());

        PatchRequest newPatchRequest = new PatchRequest();
        newPatchRequest.setScript(newScript);
        newPatchRequest.setValues(newVals);

        defer(new PatchCommandData(id, null, newPatchRequest, null));

        return true;
    }

    public <T, TIndex extends AbstractCommonApiForIndexes> IDocumentQuery<T> documentQuery(Class<T> clazz, Class<TIndex> indexClazz) {
        try {
            TIndex index = indexClazz.newInstance();
            return documentQuery(clazz, index.getIndexName(), null, index.isMapReduce());
        } catch (IllegalAccessException | IllegalStateException | InstantiationException e) {
            throw new RuntimeException("Unable to query index: " + indexClazz.getSimpleName() + e.getMessage(), e);
        }
    }

    /**
     * Query the specified index using Lucene syntax
     * @param clazz The result of the query
     */
    public <T> IDocumentQuery<T> documentQuery(Class<T> clazz) {
        return documentQuery(clazz, null, null, false);
    }

    /**
     * Query the specified index using Lucene syntax
     * @param clazz The result of the query
     * @param indexName Name of the index (mutually exclusive with collectionName)
     * @param collectionName Name of the collection (mutually exclusive with indexName)
     * @param isMapReduce Whether we are querying a map/reduce index (modify how we treat identifier properties)
     */
    public <T> IDocumentQuery<T> documentQuery(Class<T> clazz, String indexName, String collectionName, boolean isMapReduce) {
        Tuple<String, String> indexNameAndCollection = processQueryParameters(clazz, indexName, collectionName, getConventions());
        indexName = indexNameAndCollection.first;
        collectionName = indexNameAndCollection.second;

        return new DocumentQuery<>(clazz, this, indexName, collectionName, isMapReduce);
    }

    @Override
    public InMemoryDocumentSessionOperations getSession() {
        return this;
    }

    public <T> IRawDocumentQuery<T> rawQuery(Class<T> clazz, String query) {
        return new RawDocumentQuery<>(clazz, this, query);
    }

    @Override
    public <T> IDocumentQuery<T> query(Class<T> clazz) {
        return documentQuery(clazz, null, null, false);
    }

    @Override
    public <T> IDocumentQuery<T> query(Class<T> clazz, Query collectionOrIndexName) {
        if (StringUtils.isNotEmpty(collectionOrIndexName.getCollection())) {
            return documentQuery(clazz, null, collectionOrIndexName.getCollection(), false);
        }

        return documentQuery(clazz, collectionOrIndexName.getIndexName(), null, false);
    }

    @Override
    public <T, TIndex extends AbstractCommonApiForIndexes> IDocumentQuery<T> query(Class<T> clazz, Class<TIndex> indexClazz) {
        return documentQuery(clazz, indexClazz);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(IDocumentQuery<T> query) {
        StreamOperation streamOperation = new StreamOperation(this);
        QueryStreamCommand command = streamOperation.createRequest(query.getIndexQuery());

        getRequestExecutor().execute(command, sessionInfo);

        CloseableIterator<ObjectNode> result = streamOperation.setResult(command.getResult());
        return yieldResults((AbstractDocumentQuery) query, result);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(IDocumentQuery<T> query, Reference<StreamQueryStatistics> streamQueryStats) {
        StreamQueryStatistics stats = new StreamQueryStatistics();
        StreamOperation streamOperation = new StreamOperation(this, stats);
        QueryStreamCommand command = streamOperation.createRequest(query.getIndexQuery());

        getRequestExecutor().execute(command, sessionInfo);

        CloseableIterator<ObjectNode> result = streamOperation.setResult(command.getResult());
        streamQueryStats.value = stats;

        return yieldResults((AbstractDocumentQuery)query, result);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(IRawDocumentQuery<T> query) {
        StreamOperation streamOperation = new StreamOperation(this);
        QueryStreamCommand command = streamOperation.createRequest(query.getIndexQuery());

        getRequestExecutor().execute(command, sessionInfo);

        CloseableIterator<ObjectNode> result = streamOperation.setResult(command.getResult());
        return yieldResults((AbstractDocumentQuery) query, result);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(IRawDocumentQuery<T> query, Reference<StreamQueryStatistics> streamQueryStats) {
        StreamQueryStatistics stats = new StreamQueryStatistics();
        StreamOperation streamOperation = new StreamOperation(this, stats);
        QueryStreamCommand command = streamOperation.createRequest(query.getIndexQuery());

        getRequestExecutor().execute(command, sessionInfo);

        CloseableIterator<ObjectNode> result = streamOperation.setResult(command.getResult());
        streamQueryStats.value = stats;

        return yieldResults((AbstractDocumentQuery) query, result);
    }

    @SuppressWarnings("unchecked")
    private <T> CloseableIterator<StreamResult<T>> yieldResults(AbstractDocumentQuery query, CloseableIterator<ObjectNode> enumerator) {
        return new StreamIterator<T>(query.getQueryClass(), enumerator, query.fieldsToFetchToken, query.isProjectInto, query::invokeAfterStreamExecuted);
    }

    @Override
    public <T> void streamInto(IRawDocumentQuery<T> query, OutputStream output) {
        StreamOperation streamOperation = new StreamOperation(this);
        QueryStreamCommand command = streamOperation.createRequest(query.getIndexQuery());

        getRequestExecutor().execute(command, sessionInfo);

        try {
            IOUtils.copy(command.getResult().getStream(), output);
        } catch (IOException e) {
            throw new RuntimeException("Unable to stream results into OutputStream: " + e.getMessage(), e);
        } finally {
            EntityUtils.consumeQuietly(command.getResult().getResponse().getEntity());
        }
    }

    @Override
    public <T> void streamInto(IDocumentQuery<T> query, OutputStream output) {
        StreamOperation streamOperation = new StreamOperation(this);
        QueryStreamCommand command = streamOperation.createRequest(query.getIndexQuery());

        getRequestExecutor().execute(command, sessionInfo);

        try {
            IOUtils.copy(command.getResult().getStream(), output);
        } catch (IOException e) {
            throw new RuntimeException("Unable to stream results into OutputStream: " + e.getMessage(), e);
        } finally {
            EntityUtils.consumeQuietly(command.getResult().getResponse().getEntity());
        }
    }

    private <T> StreamResult<T> createStreamResult(Class<T> clazz, ObjectNode json, FieldsToFetchToken fieldsToFetch, boolean isProjectInto) throws IOException {

        ObjectNode metadata = (ObjectNode) json.get(Constants.Documents.Metadata.KEY);
        String changeVector = null;
        // MapReduce indexes return reduce results that don't have @id property
        String id = null;
        JsonNode idJson = metadata.get(Constants.Documents.Metadata.ID);
        if (idJson != null && !idJson.isNull()) {
            id = idJson.asText();
            changeVector = metadata.get(Constants.Documents.Metadata.CHANGE_VECTOR).asText();
        }

        T entity = QueryOperation.deserialize(clazz, id, json, metadata, fieldsToFetch, true, this, isProjectInto);

        StreamResult<T> streamResult = new StreamResult<>();
        streamResult.setChangeVector(changeVector);
        streamResult.setId(id);
        streamResult.setDocument(entity);
        streamResult.setMetadata(new MetadataAsDictionary(metadata));

        return streamResult;
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(Class<T> clazz, String startsWith) {
        return stream(clazz, startsWith, null, 0, Integer.MAX_VALUE, null);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(Class<T> clazz, String startsWith, String matches) {
        return stream(clazz, startsWith, matches, 0, Integer.MAX_VALUE, null);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(Class<T> clazz, String startsWith, String matches, int start) {
        return stream(clazz, startsWith, matches, start, Integer.MAX_VALUE, null);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(Class<T> clazz, String startsWith, String matches, int start, int pageSize) {
        return stream(clazz, startsWith, matches, start, pageSize, null);
    }

    @Override
    public <T> CloseableIterator<StreamResult<T>> stream(Class<T> clazz, String startsWith, String matches, int start, int pageSize, String startAfter) {
        StreamOperation streamOperation = new StreamOperation(this);

        StreamCommand command = streamOperation.createRequest(startsWith, matches, start, pageSize, null, startAfter);
        getRequestExecutor().execute(command, sessionInfo);

        CloseableIterator<ObjectNode> result = streamOperation.setResult(command.getResult());
        return new StreamIterator<>(clazz, result, null, false, null);
    }

    private class StreamIterator<T> implements CloseableIterator<StreamResult<T>> {

        private final Class<T> _clazz;
        private final CloseableIterator<ObjectNode> _innerIterator;
        private final FieldsToFetchToken _fieldsToFetchToken;
        private final boolean _isProjectInto;
        private final Consumer<ObjectNode> _onNextItem;

        public StreamIterator(Class<T> clazz, CloseableIterator<ObjectNode> innerIterator, FieldsToFetchToken fieldsToFetch, boolean isProjectInto, Consumer<ObjectNode> onNextItem) {
            _clazz = clazz;
            _innerIterator = innerIterator;
            _fieldsToFetchToken = fieldsToFetch;
            _isProjectInto = isProjectInto;
            _onNextItem = onNextItem;
        }

        @Override
        public boolean hasNext() {
            return _innerIterator.hasNext();
        }

        @Override
        public StreamResult<T> next() {
            ObjectNode nextValue = _innerIterator.next();
            try {
                if (_onNextItem != null) {
                    _onNextItem.accept(nextValue);
                }
                return createStreamResult(_clazz, nextValue, _fieldsToFetchToken, _isProjectInto);
            } catch (IOException e) {
                throw new RuntimeException("Unable to parse stream result: " + e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            _innerIterator.close();
        }
    }

    @Override
    public ISessionDocumentCounters countersFor(String documentId) {
        return new SessionDocumentCounters(this, documentId);
    }

    @Override
    public ISessionDocumentCounters countersFor(Object entity) {
        return new SessionDocumentCounters(this, entity);
    }


    @Override
    public ISessionDocumentTimeSeries timeSeriesFor(String documentId, String name) {
        return new SessionDocumentTimeSeries(this, documentId, name);
    }

    @Override
    public ISessionDocumentTimeSeries timeSeriesFor(Object entity, String name) {
        return new SessionDocumentTimeSeries(this, entity, name);
    }

    @Override
    public <T> ISessionDocumentTypedTimeSeries<T> timeSeriesFor(Class<T> clazz, Object entity) {
        return timeSeriesFor(clazz, entity, null);
    }

    @Override
    public <T> ISessionDocumentTypedTimeSeries<T> timeSeriesFor(Class<T> clazz, Object entity, String name) {
        String tsName = ObjectUtils.firstNonNull(name, TimeSeriesOperations.getTimeSeriesName(clazz, getConventions()));
        validateTimeSeriesName(tsName);
        return new SessionDocumentTypedTimeSeries<T>(clazz, this, entity, tsName);
    }

    @Override
    public <T> ISessionDocumentTypedTimeSeries<T> timeSeriesFor(Class<T> clazz, String documentId) {
        return timeSeriesFor(clazz, documentId, null);
    }

    @Override
    public <T> ISessionDocumentTypedTimeSeries<T> timeSeriesFor(Class<T> clazz, String documentId, String name) {
        String tsName = ObjectUtils.firstNonNull(name, TimeSeriesOperations.getTimeSeriesName(clazz, getConventions()));
        validateTimeSeriesName(tsName);
        return new SessionDocumentTypedTimeSeries<>(clazz, this, documentId, tsName);
    }

    @Override
    public <T> ISessionDocumentRollupTypedTimeSeries<T> timeSeriesRollupFor(Class<T> clazz, Object entity, String policy) {
        return timeSeriesRollupFor(clazz, entity, policy, null);
    }

    @Override
    public <T> ISessionDocumentRollupTypedTimeSeries<T> timeSeriesRollupFor(Class<T> clazz, Object entity, String policy, String raw) {
        String tsName = ObjectUtils.firstNonNull(raw, TimeSeriesOperations.getTimeSeriesName(clazz, getConventions()));
        return new SessionDocumentRollupTypedTimeSeries<T>(clazz, this, entity, tsName + TimeSeriesConfiguration.TIME_SERIES_ROLLUP_SEPARATOR + policy);
    }

    @Override
    public <T> ISessionDocumentRollupTypedTimeSeries<T> timeSeriesRollupFor(Class<T> clazz, String documentId, String policy) {
        return timeSeriesRollupFor(clazz, documentId, policy, null);
    }

    @Override
    public <T> ISessionDocumentRollupTypedTimeSeries<T> timeSeriesRollupFor(Class<T> clazz, String documentId, String policy, String raw) {
        String tsName = ObjectUtils.firstNonNull(raw, TimeSeriesOperations.getTimeSeriesName(clazz, getConventions()));
        return new SessionDocumentRollupTypedTimeSeries<T>(clazz, this, documentId, tsName + TimeSeriesConfiguration.TIME_SERIES_ROLLUP_SEPARATOR + policy);
    }

    public ISessionDocumentIncrementalTimeSeries incrementalTimeSeriesFor(String documentId, String name) {
        validateIncrementalTimeSeriesName(name);

        return new SessionDocumentTimeSeries(this, documentId, name);
    }

    public ISessionDocumentIncrementalTimeSeries incrementalTimeSeriesFor(Object entity, String name) {
        validateIncrementalTimeSeriesName(name);

        return new SessionDocumentTimeSeries(this, entity, name);
    }

    public <T> ISessionDocumentTypedIncrementalTimeSeries<T> incrementalTimeSeriesFor(Class<T> clazz, String documentId) {
        return incrementalTimeSeriesFor(clazz, documentId, null);
    }
    public <T> ISessionDocumentTypedIncrementalTimeSeries<T> incrementalTimeSeriesFor(Class<T> clazz, String documentId, String name) {
        String tsName = ObjectUtils.firstNonNull(name, TimeSeriesOperations.getTimeSeriesName(clazz, getConventions()));
        validateIncrementalTimeSeriesName(tsName);
        return new SessionDocumentTypedTimeSeries<T>(clazz, this, documentId, tsName);
    }

    public <T> ISessionDocumentTypedIncrementalTimeSeries<T> incrementalTimeSeriesFor(Class<T> clazz, Object entity) {
        return incrementalTimeSeriesFor(clazz, entity, null);
    }
    public <T> ISessionDocumentTypedIncrementalTimeSeries<T> incrementalTimeSeriesFor(Class<T> clazz, Object entity, String name) {
        String tsName = ObjectUtils.firstNonNull(name, TimeSeriesOperations.getTimeSeriesName(clazz, getConventions()));
        validateIncrementalTimeSeriesName(tsName);
        return new SessionDocumentTypedTimeSeries<>(clazz, this, entity, tsName);
    }

    @Override
    public <T> ConditionalLoadResult<T> conditionalLoad(Class<T> clazz, String id, String changeVector) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Id cannot be null");
        }

        if (advanced().isLoaded(id)) {
            T entity = load(clazz, id);
            if (entity == null) {
                return ConditionalLoadResult.create(null, null);
            }

            String cv = advanced().getChangeVectorFor(entity);
            return ConditionalLoadResult.create(entity, cv);
        }

        if (StringUtils.isEmpty(changeVector)) {
            throw new IllegalArgumentException("The requested document with id '" + id + "' is not loaded into the session and could not conditional load when changeVector is null or empty.");
        }

        incrementRequestCount();

        ConditionalGetDocumentsCommand cmd = new ConditionalGetDocumentsCommand(id, changeVector);
        advanced().getRequestExecutor().execute(cmd);

        switch (cmd.getStatusCode()) {
            case HttpStatus.SC_NOT_MODIFIED:
                return ConditionalLoadResult.create(null, changeVector); // value not changed
            case HttpStatus.SC_NOT_FOUND:
                registerMissing(id);
                return ConditionalLoadResult.create(null, null); // value is missing
        }

        DocumentInfo documentInfo = DocumentInfo.getNewDocumentInfo((ObjectNode) cmd.getResult().getResults().get(0));
        T r = trackEntity(clazz, documentInfo);
        return ConditionalLoadResult.create(r, cmd.getResult().getChangeVector());
    }

    public static void throwWhenResultsAreOverInt32(long value, String caller, String suggestedMethod) {
        if (Integer.MAX_VALUE < value) {
            throw new IllegalArgumentException("Value '" + value + "' from '" + caller + "' method exceeds max Integer value.'. You should use '" + suggestedMethod + "' instead.");
        }
    }
}
