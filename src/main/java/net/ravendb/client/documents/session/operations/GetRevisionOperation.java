package net.ravendb.client.documents.session.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Defaults;
import net.ravendb.client.Constants;
import net.ravendb.client.documents.commands.GetRevisionsCommand;
import net.ravendb.client.documents.session.DocumentInfo;
import net.ravendb.client.documents.session.InMemoryDocumentSessionOperations;
import net.ravendb.client.json.JsonArrayResult;
import net.ravendb.client.json.MetadataAsDictionary;

import java.util.*;

public class GetRevisionOperation {
    private final InMemoryDocumentSessionOperations _session;

    private JsonArrayResult _result;
    private final GetRevisionsCommand _command;

    public GetRevisionOperation(InMemoryDocumentSessionOperations session, String id, Integer start, Integer pageSize) {
        this(session, id, start, pageSize, false);
    }

    public GetRevisionOperation(InMemoryDocumentSessionOperations session, String id, Integer start, Integer pageSize, boolean metadataOnly) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        _session = session;
        _command = new GetRevisionsCommand(id, start, pageSize, metadataOnly);
    }

    public GetRevisionOperation(InMemoryDocumentSessionOperations session, String id, Date before) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        _session = session;
        _command = new GetRevisionsCommand(id, before);
    }


    public GetRevisionOperation(InMemoryDocumentSessionOperations session, String changeVector) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        _session = session;
        _command = new GetRevisionsCommand(changeVector);
    }

    public GetRevisionOperation(InMemoryDocumentSessionOperations session, String[] changeVector) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        _session = session;
        _command = new GetRevisionsCommand(changeVector);
    }

    public GetRevisionsCommand createRequest() {
        if (_command.getChangeVectors() != null) {
            return _session.checkIfAllChangeVectorsAreAlreadyIncluded(_command.getChangeVectors()) ? null : _command;
        }

        if (_command.getChangeVector() != null) {
            return _session.checkIfAllChangeVectorsAreAlreadyIncluded(new String[]{ _command.getChangeVector() }) ? null : _command;
        }

        if (_command.getBefore() != null) {
            return _session.checkIfRevisionByDateTimeBeforeAlreadyIncluded(_command.getId(), _command.getBefore()) ? null : _command;
        }

        return _command;
    }

    public void setResult(JsonArrayResult result) {
        _result = result;
    }

    public GetRevisionsCommand getCommand() {
        return _command;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRevision(Class<T> clazz, ObjectNode document) {
        if (document == null) {
            return Defaults.defaultValue(clazz);
        }

        ObjectNode metadata = null;
        String id = null;
        if (document.has(Constants.Documents.Metadata.KEY)) {
            metadata = (ObjectNode) document.get(Constants.Documents.Metadata.KEY);
            JsonNode idNode = metadata.get(Constants.Documents.Metadata.ID);
            if (idNode != null) {
                id = idNode.asText();
            }
        }

        String changeVector = null;
        if (metadata != null && metadata.has(Constants.Documents.Metadata.CHANGE_VECTOR)) {
            JsonNode changeVectorNode = metadata.get(Constants.Documents.Metadata.CHANGE_VECTOR);
            if (changeVectorNode != null) {
                changeVector = changeVectorNode.asText();
            }
        }

        T entity = (T)_session.getEntityToJson().convertToEntity(clazz, id, document, !this._session.noTracking);
        DocumentInfo documentInfo = new DocumentInfo();
        documentInfo.setId(id);
        documentInfo.setChangeVector(changeVector);
        documentInfo.setDocument(document);
        documentInfo.setMetadata(metadata);
        documentInfo.setEntity(entity);
        _session.documentsByEntity.put(entity, documentInfo);

        _session.onAfterConversionToEntityInvoke(id, document, entity);

        return entity;
    }

    public <T> List<T> getRevisionsFor(Class<T> clazz) {
        int resultsCount = _result.getResults().size();
        ArrayList<T> results = new ArrayList<>(resultsCount);
        for (int i = 0; i < resultsCount; i++) {
            ObjectNode document = (ObjectNode) _result.getResults().get(i);
            results.add(getRevision(clazz, document));
        }

        return results;
    }

    public List<MetadataAsDictionary> getRevisionsMetadataFor() {
        int resultsCount = _result.getResults().size();
        ArrayList<MetadataAsDictionary> results = new ArrayList<>(resultsCount);
        for (int i = 0; i < resultsCount; i++) {
            ObjectNode document = (ObjectNode) _result.getResults().get(i);

            ObjectNode metadata = null;
            if (document.has(Constants.Documents.Metadata.KEY)) {
                metadata = (ObjectNode) document.get(Constants.Documents.Metadata.KEY);
            }

            results.add(new MetadataAsDictionary(metadata));
        }

        return results;
    }

    public <T> T getRevision(Class<T> clazz) {
        if (_result == null) {

            DocumentInfo revision;

            if (_command.getChangeVectors() != null) {
                for (String changeVector : _command.getChangeVectors()) {
                    revision = _session.includeRevisionsByChangeVector.get(changeVector);
                    if (revision != null) {
                        return getRevision(clazz, revision.getDocument());
                    }
                }
            }

            if (_command.getChangeVector() != null && _session.includeRevisionsByChangeVector != null) {
                revision = _session.includeRevisionsByChangeVector.get(_command.getChangeVector());
                if (revision != null) {
                    return getRevision(clazz, revision.getDocument());
                }
            }

            if (_command.getBefore() != null && _session.includeRevisionsIdByDateTimeBefore != null) {
                Map<Date, DocumentInfo> dictionaryDateTimeToDocument = _session.includeRevisionsIdByDateTimeBefore.get(_command.getId());
                if (dictionaryDateTimeToDocument != null) {
                    revision = dictionaryDateTimeToDocument.get(_command.getBefore());
                    if (revision != null) {
                        return getRevision(clazz, revision.getDocument());
                    }
                }
            }

            return Defaults.defaultValue(clazz);
        }

        ObjectNode document = (ObjectNode) _result.getResults().get(0);
        return getRevision(clazz, document);
    }

    public <T> Map<String, T> getRevisions(Class<T> clazz) {
        Map<String, T> results = new TreeMap<>(String::compareToIgnoreCase);

        if (_result == null) {
            for (String changeVector : _command.getChangeVectors()) {
                DocumentInfo revision = _session.includeRevisionsByChangeVector.get(changeVector);
                if (revision != null) {
                    results.put(changeVector, getRevision(clazz, revision.getDocument()));
                }
            }
            return results;
        }

        for (int i = 0; i < _command.getChangeVectors().length; i++) {
            String changeVector = _command.getChangeVectors()[i];
            if (changeVector == null) {
                continue;
            }
            JsonNode jsonNode = _result.getResults().get(i);
            ObjectNode objectNode = jsonNode.isNull() ? null : (ObjectNode) jsonNode;
            results.put(changeVector, getRevision(clazz, objectNode));
        }

        return results;
    }

}
