package net.ravendb.client.documents.session.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.ravendb.client.Constants;
import net.ravendb.client.documents.commands.batches.ClusterWideBatchCommand;
import net.ravendb.client.documents.commands.batches.CommandType;
import net.ravendb.client.documents.commands.batches.SingleNodeBatchCommand;
import net.ravendb.client.documents.operations.PatchStatus;
import net.ravendb.client.documents.session.*;
import net.ravendb.client.exceptions.ClientVersionMismatchException;
import net.ravendb.client.extensions.JsonExtensions;
import net.ravendb.client.json.BatchCommandResult;
import net.ravendb.client.primitives.Tuple;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class BatchOperation {

    private final InMemoryDocumentSessionOperations _session;

    public BatchOperation(InMemoryDocumentSessionOperations session) {
        this._session = session;
    }

    private List<Object> _entities;
    private int _sessionCommandsCount;
    private int _allCommandsCount;
    private InMemoryDocumentSessionOperations.SaveChangesData.ActionsToRunOnSuccess _onSuccessfulRequest;

    private Map<String, DocumentInfo> _modifications;

    public SingleNodeBatchCommand createRequest() {
        InMemoryDocumentSessionOperations.SaveChangesData result = _session.prepareForSaveChanges();
        _onSuccessfulRequest = result.getOnSuccess();
        _sessionCommandsCount = result.getSessionCommands().size();
        result.getSessionCommands().addAll(result.getDeferredCommands());

        _session.validateClusterTransaction(result);

        _allCommandsCount = result.getSessionCommands().size();

        if (_allCommandsCount == 0) {
            return null;
        }

        _session.incrementRequestCount();

        _entities = result.getEntities();

        if (_session.getTransactionMode() == TransactionMode.CLUSTER_WIDE) {
            return new ClusterWideBatchCommand(
                    _session.getConventions(),
                    result.getSessionCommands(),
                    result.getOptions(),
                    _session.disableAtomicDocumentWritesInClusterWideTransaction);
        }

        return new SingleNodeBatchCommand(_session.getConventions(), result.getSessionCommands(), result.getOptions());
    }

    public void setResult(BatchCommandResult result) {

        Function<ObjectNode, CommandType> getCommandType = batchResult -> {
            JsonNode type = batchResult.get("Type");

            if (type == null || !type.isTextual()) {
                return CommandType.NONE;
            }

            String typeAsString = type.asText();

            CommandType commandType = CommandType.parseCSharpValue(typeAsString);
            return commandType;
        };

        if (result.getResults() == null) {
            throwOnNullResults();
            return;
        }

        _onSuccessfulRequest.clearSessionStateAfterSuccessfulSaveChanges();

        if (_session.getTransactionMode() == TransactionMode.CLUSTER_WIDE) {
            if (result.getTransactionIndex() <= 0) {
                throw new ClientVersionMismatchException("Cluster transaction was send to a node that is not supporting it. " +
                        "So it was executed ONLY on the requested node on " + _session.getRequestExecutor().getUrl());
            }
        }

        for (int i = 0; i < _sessionCommandsCount; i++) {
            ObjectNode batchResult = (ObjectNode) result.getResults().get(i);
            if (batchResult == null) {
                continue;
            }

            CommandType type = getCommandType.apply(batchResult);

            switch (type) {
                case PUT:
                    handlePut(i, batchResult, false);
                    break;
                case FORCE_REVISION_CREATION:
                    handleForceRevisionCreation(batchResult);
                    break;
                case DELETE:
                    handleDelete(batchResult);
                    break;
                case COMPARE_EXCHANGE_PUT:
                    handleCompareExchangePut(batchResult);
                    break;
                case COMPARE_EXCHANGE_DELETE:
                    handleCompareExchangeDelete(batchResult);
                    break;
                default:
                    throw new IllegalStateException("Command " + type + " is not supported");
            }
        }

        for (int i = _sessionCommandsCount; i < _allCommandsCount; i++) {
            ObjectNode batchResult = (ObjectNode) result.getResults().get(i);
            if (batchResult == null) {
                continue;
            }

            CommandType type = getCommandType.apply(batchResult);

            switch (type) {
                case PUT:
                    handlePut(i, batchResult, true);
                    break;
                case DELETE:
                    handleDelete(batchResult);
                    break;
                case PATCH:
                    handlePatch(batchResult);
                    break;
                case ATTACHMENT_PUT:
                    handleAttachmentPut(batchResult);
                    break;
                case ATTACHMENT_DELETE:
                    handleAttachmentDelete(batchResult);
                    break;
                case ATTACHMENT_MOVE:
                    handleAttachmentMove(batchResult);
                    break;
                case ATTACHMENT_COPY:
                    handleAttachmentCopy(batchResult);
                    break;
                case COMPARE_EXCHANGE_PUT:
                case COMPARE_EXCHANGE_DELETE:
                case FORCE_REVISION_CREATION:
                    break;
                case COUNTERS:
                    handleCounters(batchResult);
                    break;
                case TIME_SERIES:
                case TIME_SERIES_WITH_INCREMENTS:
                    //TODO: RavenDB-13474 add to time series cache
                    break;
                case TIME_SERIES_COPY:
                    break;
                case BATCH_PATCH:
                    break;
                default:
                    throw new IllegalStateException("Command " + type + " is not supported");
            }
        }
        finalizeResult();
    }

    private void finalizeResult() {
        if (_modifications == null || _modifications.isEmpty()) {
            return;
        }

        for (Map.Entry<String, DocumentInfo> kvp : _modifications.entrySet()) {
            String id = kvp.getKey();
            DocumentInfo documentInfo = kvp.getValue();

            applyMetadataModifications(id, documentInfo);
        }
    }

    private void applyMetadataModifications(String id, DocumentInfo documentInfo) {
        documentInfo.setMetadataInstance(null);

        documentInfo.setMetadata(documentInfo.getMetadata().deepCopy());

        documentInfo.getMetadata().set(Constants.Documents.Metadata.CHANGE_VECTOR,
                documentInfo.getMetadata().textNode(documentInfo.getChangeVector()));

        ObjectNode documentCopy = documentInfo.getDocument().deepCopy();
        documentCopy.set(Constants.Documents.Metadata.KEY, documentInfo.getMetadata());

        documentInfo.setDocument(documentCopy);
    }

    private DocumentInfo getOrAddModifications(String id, DocumentInfo documentInfo, boolean applyModifications) {
        if (_modifications == null) {
            _modifications = new TreeMap<>(String::compareToIgnoreCase);
        }

        DocumentInfo modifiedDocumentInfo = _modifications.get(id);
        if (modifiedDocumentInfo != null) {
            if (applyModifications) {
                applyMetadataModifications(id, modifiedDocumentInfo);
            }
        } else {
            _modifications.put(id, modifiedDocumentInfo = documentInfo);
        }

        return modifiedDocumentInfo;
    }

    private void handleCompareExchangePut(ObjectNode batchResult) {
        handleCompareExchangeInternal(CommandType.COMPARE_EXCHANGE_PUT, batchResult);
    }

    private void handleCompareExchangeDelete(ObjectNode batchResult) {
        handleCompareExchangeInternal(CommandType.COMPARE_EXCHANGE_DELETE, batchResult);
    }

    private void handleCompareExchangeInternal(CommandType commandType, ObjectNode batchResult) {
        TextNode key = (TextNode) batchResult.get("Key");
        if (key == null || key.isNull()) {
            throwMissingField(commandType, "Key");
        }

        NumericNode index = (NumericNode) batchResult.get("Index");
        if (index == null || index.isNull()) {
            throwMissingField(commandType, "Index");
        }

        ClusterTransactionOperationsBase clusterSession = _session.getClusterSession();
        clusterSession.updateState(key.asText(), index.asLong());
    }

    private void handleAttachmentCopy(ObjectNode batchResult) {
        handleAttachmentPutInternal(batchResult, CommandType.ATTACHMENT_COPY, "Id", "Name", "DocumentChangeVector");
    }

    private void handleAttachmentMove(ObjectNode batchResult) {
        handleAttachmentDeleteInternal(batchResult, CommandType.ATTACHMENT_MOVE, "Id", "Name", "DocumentChangeVector");
        handleAttachmentPutInternal(batchResult, CommandType.ATTACHMENT_MOVE, "DestinationId", "DestinationName", "DocumentChangeVector");
    }

    private void handleAttachmentDelete(ObjectNode batchResult) {
        handleAttachmentDeleteInternal(batchResult, CommandType.ATTACHMENT_DELETE, Constants.Documents.Metadata.ID, "Name", "DocumentChangeVector");
    }

    private void handleAttachmentDeleteInternal(ObjectNode batchResult, CommandType type, String idFieldName, String attachmentNameFieldName, String documentChangeVectorFieldName) {
        String id = getStringField(batchResult, type, idFieldName);

        DocumentInfo sessionDocumentInfo = _session.documentsById.getValue(id);
        if (sessionDocumentInfo == null) {
            return;
        }

        DocumentInfo documentInfo = getOrAddModifications(id, sessionDocumentInfo, true);

        String documentChangeVector = getStringField(batchResult, type, documentChangeVectorFieldName, false);
        if (documentChangeVector != null) {
            documentInfo.setChangeVector(documentChangeVector);
        }

        JsonNode attachmentsJson = documentInfo.getMetadata().get(Constants.Documents.Metadata.ATTACHMENTS);
        if (attachmentsJson == null || attachmentsJson.isNull() || attachmentsJson.isEmpty()) {
            return;
        }

        String name = getStringField(batchResult, type, attachmentNameFieldName);

        ArrayNode attachments = JsonExtensions.getDefaultMapper().createArrayNode();
        documentInfo.getMetadata().set(Constants.Documents.Metadata.ATTACHMENTS, attachments);

        for (int i = 0; i < attachmentsJson.size(); i++) {
            ObjectNode attachment = (ObjectNode) attachmentsJson.get(i);
            String attachmentName = getStringField(attachment, type, "Name");
            if (attachmentName.equals(name)) {
                continue;
            }

            attachments.add(attachment);
        }
    }

    private void handleAttachmentPut(ObjectNode batchResult) {
        handleAttachmentPutInternal(batchResult, CommandType.ATTACHMENT_PUT, "Id", "Name", "DocumentChangeVector");
    }

    private void handleAttachmentPutInternal(ObjectNode batchResult, CommandType type, String idFieldName, String attachmentNameFieldName, String documentChangeVectorFieldName) {
        String id = getStringField(batchResult, type, idFieldName);

        DocumentInfo sessionDocumentInfo = _session.documentsById.getValue(id);
        if (sessionDocumentInfo == null) {
            return;
        }

        DocumentInfo documentInfo = getOrAddModifications(id, sessionDocumentInfo, false);

        String documentChangeVector = getStringField(batchResult, type, documentChangeVectorFieldName, false);
        if (documentChangeVector != null) {
            documentInfo.setChangeVector(documentChangeVector);
        }

        ObjectMapper mapper = JsonExtensions.getDefaultMapper();
        ArrayNode attachments = (ArrayNode) documentInfo.getMetadata().get(Constants.Documents.Metadata.ATTACHMENTS);
        if (attachments == null) {
            attachments = mapper.createArrayNode();
            documentInfo.getMetadata().set(Constants.Documents.Metadata.ATTACHMENTS, attachments);
        }

        ObjectNode dynamicNode = mapper.createObjectNode();
        attachments.add(dynamicNode);
        dynamicNode.put("ChangeVector", getStringField(batchResult, type, "ChangeVector"));
        dynamicNode.put("ContentType", getStringField(batchResult, type, "ContentType"));
        dynamicNode.put("Hash", getStringField(batchResult, type, "Hash"));
        dynamicNode.put("Name", getStringField(batchResult, type, "Name"));
        dynamicNode.put("Size", getLongField(batchResult, type, "Size"));
    }

    private void handlePatch(ObjectNode batchResult) {

        JsonNode patchStatus = batchResult.get("PatchStatus");
        if (patchStatus == null || patchStatus.isNull()) {
            throwMissingField(CommandType.PATCH, "PatchStatus");
        }

        PatchStatus status = JsonExtensions.getDefaultMapper().convertValue(patchStatus, PatchStatus.class);

        switch (status) {
            case CREATED:
            case PATCHED:
                ObjectNode document = (ObjectNode) batchResult.get("ModifiedDocument");
                if (document == null) {
                    return;
                }

                String id = getStringField(batchResult, CommandType.PUT, "Id");

                DocumentInfo sessionDocumentInfo = _session.documentsById.getValue(id);
                if (sessionDocumentInfo == null) {
                    return;
                }

                DocumentInfo documentInfo = getOrAddModifications(id, sessionDocumentInfo, true);

                String changeVector = getStringField(batchResult, CommandType.PATCH, "ChangeVector");
                String lastModified = getStringField(batchResult, CommandType.PATCH, "LastModified");

                documentInfo.setChangeVector(changeVector);

                documentInfo.getMetadata().put(Constants.Documents.Metadata.ID, id);
                documentInfo.getMetadata().put(Constants.Documents.Metadata.CHANGE_VECTOR, changeVector);
                documentInfo.getMetadata().put(Constants.Documents.Metadata.LAST_MODIFIED, lastModified);

                documentInfo.setDocument(document);
                applyMetadataModifications(id, documentInfo);

                if (documentInfo.getEntity() != null) {
                    _session.getEntityToJson().populateEntity(documentInfo.getEntity(), id, documentInfo.getDocument());
                    AfterSaveChangesEventArgs afterSaveChangesEventArgs = new AfterSaveChangesEventArgs(_session, documentInfo.getId(), documentInfo.getEntity());
                    _session.onAfterSaveChangesInvoke(afterSaveChangesEventArgs);
                }

                break;
        }
    }

    private void handleDelete(ObjectNode batchReslt) {
        handleDeleteInternal(batchReslt, CommandType.DELETE);
    }

    private void handleDeleteInternal(ObjectNode batchResult, CommandType type) {
        String id = getStringField(batchResult, type, "Id");

        DocumentInfo documentInfo = _session.documentsById.getValue(id);
        if (documentInfo == null) {
            return;
        }

        _session.documentsById.remove(id);

        if (documentInfo.getEntity() != null) {
            _session.documentsByEntity.remove(documentInfo.getEntity());
            _session.deletedEntities.remove(documentInfo.getEntity());
        }
    }

    private void handleForceRevisionCreation(ObjectNode batchResult) {
        // When forcing a revision for a document that does Not have any revisions yet then the HasRevisions flag is added to the document.
        // In this case we need to update the tracked entities in the session with the document new change-vector.

        if (!getBooleanField(batchResult, CommandType.FORCE_REVISION_CREATION, "RevisionCreated")) {
            // no forced revision was created...nothing to update.
            return;
        }

        String id = getStringField(batchResult, CommandType.FORCE_REVISION_CREATION, Constants.Documents.Metadata.ID);
        String changeVector = getStringField(batchResult, CommandType.FORCE_REVISION_CREATION, Constants.Documents.Metadata.CHANGE_VECTOR);

        DocumentInfo documentInfo = _session.documentsById.getValue(id);
        if (documentInfo == null) {
            return;
        }

        documentInfo.setChangeVector(changeVector);

        handleMetadataModifications(documentInfo, batchResult, id, changeVector);

        AfterSaveChangesEventArgs afterSaveChangesEventArgs = new AfterSaveChangesEventArgs(_session, documentInfo.getId(), documentInfo.getEntity());
        _session.onAfterSaveChangesInvoke(afterSaveChangesEventArgs);
    }

    private void handlePut(int index, ObjectNode batchResult, boolean isDeferred) {
        Object entity = null;
        DocumentInfo documentInfo = null;

        if (!isDeferred) {
            entity = _entities.get(index);

            documentInfo = _session.documentsByEntity.get(entity);
            if (documentInfo == null) {
                return;
            }
        }

        String id = getStringField(batchResult, CommandType.PUT, Constants.Documents.Metadata.ID);
        String changeVector = getStringField(batchResult, CommandType.PUT, Constants.Documents.Metadata.CHANGE_VECTOR);

        if (isDeferred) {
            DocumentInfo sessionDocumentInfo = _session.documentsById.getValue(id);
            if (sessionDocumentInfo == null) {
                return;
            }

            documentInfo = getOrAddModifications(id, sessionDocumentInfo, true);
            entity = documentInfo.getEntity();
        }

        handleMetadataModifications(documentInfo, batchResult, id, changeVector);

        _session.documentsById.add(documentInfo);

        if (entity != null) {
            _session.getGenerateEntityIdOnTheClient().trySetIdentity(entity, id);
        }

        AfterSaveChangesEventArgs afterSaveChangesEventArgs = new AfterSaveChangesEventArgs(_session, documentInfo.getId(), documentInfo.getEntity());
        _session.onAfterSaveChangesInvoke(afterSaveChangesEventArgs);
    }

    private void handleMetadataModifications(DocumentInfo documentInfo, ObjectNode batchResult, String id, String changeVector) {
        Iterator<String> fieldsIterator = batchResult.fieldNames();

        while (fieldsIterator.hasNext()) {
            String propertyName = fieldsIterator.next();

            if ("Type".equals(propertyName)) {
                continue;
            }

            documentInfo.getMetadata().set(propertyName, batchResult.get(propertyName));
        }

        documentInfo.setId(id);
        documentInfo.setChangeVector(changeVector);

        applyMetadataModifications(id, documentInfo);
    }

    private void handleCounters(ObjectNode batchResult) {

        String docId = getStringField(batchResult, CommandType.COUNTERS, "Id");

        ObjectNode countersDetail = (ObjectNode) batchResult.get("CountersDetail");
        if (countersDetail == null) {
            throwMissingField(CommandType.COUNTERS, "CountersDetail");
        }

        ArrayNode counters = (ArrayNode) countersDetail.get("Counters");
        if (counters == null) {
            throwMissingField(CommandType.COUNTERS, "Counters");
        }

        Tuple<Boolean, Map<String, Long>> cache = _session.getCountersByDocId().get(docId);
        if (cache == null) {
            cache = Tuple.create(false, new TreeMap<>(String::compareToIgnoreCase));
            _session.getCountersByDocId().put(docId, cache);
        }

        String changeVector = getStringField(batchResult, CommandType.COUNTERS, "DocumentChangeVector", false);
        if (changeVector != null) {
            DocumentInfo documentInfo = _session.documentsById.getValue(docId);
            if (documentInfo != null) {
                documentInfo.setChangeVector(changeVector);
            }
        }

        for (JsonNode counter : counters) {
            JsonNode name = counter.get("CounterName");
            JsonNode value = counter.get("TotalValue");

            if (name != null && !name.isNull() && value != null && !value.isNull()) {
                cache.second.put(name.asText(), value.longValue());
            }
        }
    }

    private static String getStringField(ObjectNode json, CommandType type, String fieldName) {
        return getStringField(json, type, fieldName, true);
    }

    private static String getStringField(ObjectNode json, CommandType type, String fieldName, boolean throwOnMissing) {
        JsonNode jsonNode = json.get(fieldName);
        if ((jsonNode == null || jsonNode.isNull()) && throwOnMissing) {
            throwMissingField(type, fieldName);
        }

        return jsonNode.asText();
    }

    private static Long getLongField(ObjectNode json, CommandType type, String fieldName) {
        JsonNode jsonNode = json.get(fieldName);
        if (jsonNode == null || !jsonNode.isNumber()) {
            throwMissingField(type, fieldName);
        }

        return jsonNode.asLong();
    }

    private static boolean getBooleanField(ObjectNode json, CommandType type, String fieldName) {
        JsonNode jsonNode = json.get(fieldName);
        if (jsonNode == null || !jsonNode.isBoolean()) {
            throwMissingField(type, fieldName);
        }

        return jsonNode.asBoolean();
    }

    private static void throwInvalidValue(String arg, String fieldName) {
        throw new IllegalArgumentException("'" + arg + "' is not a valid value for field " + fieldName);
    }

    private static void throwMissingField(CommandType type, String fieldName) {
        throw new IllegalStateException(type + " response is invalid. Field '" + fieldName + "' is missing.");
    }

    private static void throwOnNullResults() {
        throw new IllegalStateException("Received empty response from the server. This is not supposed to happen and is likely a bug.");
    }

}
