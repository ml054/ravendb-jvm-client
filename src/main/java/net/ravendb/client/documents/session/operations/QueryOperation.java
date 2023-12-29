package net.ravendb.client.documents.session.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.google.common.base.Defaults;
import com.google.common.base.Stopwatch;
import net.ravendb.client.Constants;
import net.ravendb.client.documents.commands.QueryCommand;
import net.ravendb.client.documents.queries.IndexQuery;
import net.ravendb.client.documents.queries.QueryResult;
import net.ravendb.client.documents.queries.facets.FacetResult;
import net.ravendb.client.documents.session.ClusterTransactionOperationsBase;
import net.ravendb.client.documents.session.InMemoryDocumentSessionOperations;
import net.ravendb.client.documents.session.tokens.FieldsToFetchToken;
import net.ravendb.client.exceptions.TimeoutException;
import net.ravendb.client.exceptions.documents.indexes.IndexDoesNotExistException;
import net.ravendb.client.primitives.CleanCloseable;
import net.ravendb.client.primitives.Reference;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class QueryOperation {
    private final InMemoryDocumentSessionOperations _session;
    private final String _indexName;
    private final IndexQuery _indexQuery;
    private final boolean _metadataOnly;
    private final boolean _indexEntriesOnly;
    private final boolean _isProjectInto;
    private QueryResult _currentQueryResults;
    private final FieldsToFetchToken _fieldsToFetch;
    private Stopwatch _sp;
    private boolean _noTracking;

    private static final Log logger = LogFactory.getLog(QueryOperation.class);
    private static PropertyDescriptor[] _facetResultFields;

    static {
        try {
            _facetResultFields = Arrays.stream(Introspector.getBeanInfo(FacetResult.class).getPropertyDescriptors())
                    .filter(x -> !"class".equals(x.getName()))
                    .toArray(PropertyDescriptor[]::new);
        } catch (IntrospectionException e) {
            // ignore
        }
    }

    public QueryOperation(InMemoryDocumentSessionOperations session, String indexName, IndexQuery indexQuery,
                          FieldsToFetchToken fieldsToFetch, boolean disableEntitiesTracking, boolean metadataOnly, boolean indexEntriesOnly,
                          boolean isProjectInto) {
        _session = session;
        _indexName = indexName;
        _indexQuery = indexQuery;
        _fieldsToFetch = fieldsToFetch;
        _noTracking = disableEntitiesTracking;
        _metadataOnly = metadataOnly;
        _indexEntriesOnly = indexEntriesOnly;
        _isProjectInto = isProjectInto;
    }

    public QueryCommand createRequest() {
        _session.incrementRequestCount();

        logQuery();

        return new QueryCommand(_session, _indexQuery, _metadataOnly, _indexEntriesOnly);
    }

    public QueryResult getCurrentQueryResults() {
        return _currentQueryResults;
    }

    public void setResult(QueryResult queryResult) {
        ensureIsAcceptableAndSaveResult(queryResult);
    }

    private void startTiming() {
        _sp = Stopwatch.createStarted();
    }

    public void logQuery() {
        if (logger.isInfoEnabled()) {
            logger.info("Executing query " + _indexQuery.getQuery() + " on index " + _indexName + " in " + _session.storeIdentifier());
        }
    }

    public CleanCloseable enterQueryContext() {
        startTiming();

        if (!_indexQuery.isWaitForNonStaleResults()) {
            return null;
        }

        return _session.getDocumentStore().disableAggressiveCaching(_session.getDatabaseName());
    }

    @SuppressWarnings("unchecked")
    public <T> T[] completeAsArray(Class<T> clazz) {
        QueryResult queryResult = _currentQueryResults.createSnapshot();

        T[] result = (T[]) Array.newInstance(clazz, queryResult.getResults().size());
        completeInternal(clazz, queryResult, (idx, item) -> result[idx] = item);

        return result;
    }

    public <T> List<T> complete(Class<T> clazz) {
        QueryResult queryResult = _currentQueryResults.createSnapshot();

        List<T> result = new ArrayList<>(queryResult.getResults().size());

        completeInternal(clazz, queryResult, result::add);

        return result;
    }

    private <T> void completeInternal(Class<T> clazz, QueryResult queryResult, BiConsumer<Integer, T> addToResult) {
        if (!_noTracking) {
            _session.registerIncludes(queryResult.getIncludes());
        }

        try {
            for (int i = 0; i < queryResult.getResults().size(); i++) {
                JsonNode document = queryResult.getResults().get(i);
                ObjectNode metadata = (ObjectNode) document.get(Constants.Documents.Metadata.KEY);
                try {
                    JsonNode idNode = metadata.get(Constants.Documents.Metadata.ID);

                    String id = null;
                    if (idNode != null && idNode.isTextual()) {
                        id = idNode.asText();
                    }

                    addToResult.accept(i, deserialize(clazz, id, (ObjectNode) document, metadata, _fieldsToFetch, _noTracking, _session, _isProjectInto));
                } catch (NullPointerException e) {
                    if (document.size() != _facetResultFields.length) {
                        throw e;
                    }

                    for (PropertyDescriptor prop : _facetResultFields) {
                        if (document.get(StringUtils.capitalize(prop.getName())) == null) {
                            throw e;
                        }
                    }

                    throw new IllegalArgumentException("Raw query with aggregation by facet should be called by executeAggregation method.");
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to read json: " + e.getMessage(), e);
        }

        if (!_noTracking) {
            _session.registerMissingIncludes(queryResult.getResults(), queryResult.getIncludes(), queryResult.getIncludedPaths());

            if (queryResult.getCounterIncludes() != null) {
                _session.registerCounters(queryResult.getCounterIncludes(), queryResult.getIncludedCounterNames());
            }
            if (queryResult.getTimeSeriesIncludes() != null) {
                _session.registerTimeSeries(queryResult.getTimeSeriesIncludes());
            }
            if (queryResult.getCompareExchangeValueIncludes() != null) {
                ClusterTransactionOperationsBase clusterSession = _session.getClusterSession();
                clusterSession.registerCompareExchangeIncludes(queryResult.getCompareExchangeValueIncludes(), false);
            }
            if (queryResult.getRevisionIncludes() != null) {
                _session.registerRevisionIncludes(queryResult.getRevisionIncludes());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Class<T> clazz, String id, ObjectNode document, ObjectNode metadata, FieldsToFetchToken fieldsToFetch, boolean disableEntitiesTracking, InMemoryDocumentSessionOperations session, boolean isProjectInto) throws JsonProcessingException {
        JsonNode projection = metadata.get("@projection");
        if (projection == null || !projection.asBoolean()) {
            return (T)session.trackEntity(clazz, id, document, metadata, disableEntitiesTracking);
        }

        if (fieldsToFetch != null && fieldsToFetch.projections != null && fieldsToFetch.projections.length == 1) { // we only select a single field
            String projectionField = fieldsToFetch.projections[0];

            if (fieldsToFetch.sourceAlias != null) {

                if (projectionField.startsWith(fieldsToFetch.sourceAlias)) {
                    // remove source-alias from projection name
                    projectionField = projectionField.substring(fieldsToFetch.sourceAlias.length() + 1);
                }

                if (projectionField.startsWith("'")) {
                    projectionField = projectionField.substring(1, projectionField.length() - 1);
                }
            }

            if (String.class.equals(clazz) || ClassUtils.isPrimitiveOrWrapper(clazz) || clazz.isEnum()) {
                JsonNode jsonNode = document.get(projectionField);
                if (jsonNode instanceof ValueNode) {
                    return ObjectUtils.firstNonNull(session.getConventions().getEntityMapper().treeToValue(jsonNode, clazz), Defaults.defaultValue(clazz));
                }
            }

            boolean isTimeSeriesField = fieldsToFetch.projections[0].startsWith(Constants.TimeSeries.QUERY_FUNCTION);

            if (!isProjectInto || isTimeSeriesField) {
                JsonNode inner = document.get(projectionField);
                if (inner == null) {
                    return Defaults.defaultValue(clazz);
                }

                if (isTimeSeriesField || fieldsToFetch.fieldsToFetch != null && fieldsToFetch.fieldsToFetch[0].equals(fieldsToFetch.projections[0])) {
                    if (inner instanceof ObjectNode) { //extraction from original type
                        document = (ObjectNode) inner;
                    }
                }
            }
        }

        if (ObjectNode.class.equals(clazz)) {
            return (T)document;
        }

        Reference<ObjectNode> documentRef = new Reference<>(document);
        session.onBeforeConversionToEntityInvoke(id, clazz, documentRef);
        document = documentRef.value;

        T result = session.getConventions().getEntityMapper().treeToValue(document, clazz);

        session.onAfterConversionToEntityInvoke(id, document, result);

        return result;
    }

    public boolean isNoTracking() {
        return _noTracking;
    }

    public void setNoTracking(boolean noTracking) {
        _noTracking = noTracking;
    }

    public void ensureIsAcceptableAndSaveResult(QueryResult result) {
        if (_sp == null) {
            ensureIsAcceptableAndSaveResult(result, null);
        } else {
            _sp.stop();
            ensureIsAcceptableAndSaveResult(result, _sp.elapsed());
        }
    }

    public void ensureIsAcceptableAndSaveResult(QueryResult result, Duration duration) {
        if (result == null) {
            throw new IndexDoesNotExistException("Could not find index " + _indexName);
        }

        ensureIsAcceptable(result, _indexQuery.isWaitForNonStaleResults(), duration, _session);

        saveQueryResult(result);
    }

    private void saveQueryResult(QueryResult result) {
        _currentQueryResults = result;

        if (logger.isInfoEnabled()) {
            String isStale = result.isStale() ? " stale " : " ";

            StringBuilder parameters = new StringBuilder();
            if (_indexQuery.getQueryParameters() != null && !_indexQuery.getQueryParameters().isEmpty()) {
                parameters.append("(parameters: ");

                boolean first = true;

                for (Map.Entry<String, Object> parameter : _indexQuery.getQueryParameters().entrySet()) {
                    if (!first) {
                        parameters.append(", ");
                    }

                    parameters.append(parameter.getKey())
                            .append(" = ")
                            .append(parameter.getValue());

                    first = false;
                }

                parameters.append(") ");
            }

            logger.info("Query " + _indexQuery.getQuery() + " " + parameters.toString() + "returned " + result.getResults().size() + isStale + "results (total index results: " + result.getTotalResults() + ")");
        }
    }

    public static void ensureIsAcceptable(QueryResult result, boolean waitForNonStaleResults, Stopwatch duration, InMemoryDocumentSessionOperations session) {
        if (duration == null) {
            ensureIsAcceptable(result, waitForNonStaleResults, (Duration)null, session);
        } else {
            duration.stop();
            ensureIsAcceptable(result, waitForNonStaleResults, duration.elapsed(), session);
        }
    }

    public static void ensureIsAcceptable(QueryResult result, boolean waitForNonStaleResults, Duration duration, InMemoryDocumentSessionOperations session) {
        if (waitForNonStaleResults && result.isStale()) {
            String elapsed = duration == null ? "" : " " + duration.toMillis() + " ms";
            String msg = "Waited" + elapsed + " for the query to return non stale result.";
            throw new TimeoutException(msg);
        }
    }

    public IndexQuery getIndexQuery() {
        return _indexQuery;
    }
}
