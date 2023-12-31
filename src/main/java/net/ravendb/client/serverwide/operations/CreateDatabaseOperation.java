package net.ravendb.client.serverwide.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.ravendb.client.Constants;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.primitives.ExceptionsUtils;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.serverwide.DatabaseRecord;
import net.ravendb.client.serverwide.DatabaseTopology;
import net.ravendb.client.serverwide.operations.builder.IDatabaseRecordBuilderInitializer;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

public class CreateDatabaseOperation implements IServerOperation<DatabasePutResult> {

    private final DatabaseRecord databaseRecord;
    private final int replicationFactor;

    public CreateDatabaseOperation(DatabaseRecord databaseRecord) {
        this.databaseRecord = databaseRecord;
        DatabaseTopology topology = databaseRecord.getTopology();
        if (topology != null) {
            this.replicationFactor = topology.getReplicationFactor() > 0 ? topology.getReplicationFactor() : 1;
        } else {
            this.replicationFactor = 1;
        }
    }

    public CreateDatabaseOperation(Consumer<IDatabaseRecordBuilderInitializer> builder) {
        if (builder == null) {
            throw new IllegalArgumentException("Builder cannot be null");
        }

        IDatabaseRecordBuilderInitializer instance = DatabaseRecordBuilder.create();
        builder.accept(instance);

        this.databaseRecord = instance.toDatabaseRecord();
        this.replicationFactor = this.databaseRecord.getTopology() != null
                && this.databaseRecord.getTopology().getReplicationFactor() > 0
                ? this.databaseRecord.getTopology().getReplicationFactor()
                : 1;
    }

    public CreateDatabaseOperation(DatabaseRecord databaseRecord, int replicationFactor) {
        this.databaseRecord = databaseRecord;
        this.replicationFactor = replicationFactor;
    }

    @Override
    public RavenCommand<DatabasePutResult> getCommand(DocumentConventions conventions) {
        return new CreateDatabaseCommand(conventions, databaseRecord, replicationFactor);
    }

    public static class CreateDatabaseCommand extends RavenCommand<DatabasePutResult> implements IRaftCommand {
        private final DocumentConventions conventions;
        private final DatabaseRecord databaseRecord;
        private final int replicationFactor;
        private final Long etag;
        private final String databaseName;

        public CreateDatabaseCommand(DocumentConventions conventions, DatabaseRecord databaseRecord, int replicationFactor) {
            this(conventions, databaseRecord, replicationFactor, null);
        }

        public CreateDatabaseCommand(DocumentConventions conventions, DatabaseRecord databaseRecord, int replicationFactor, Long etag) {
            super(DatabasePutResult.class);
            this.conventions = conventions;
            this.databaseRecord = databaseRecord;
            this.replicationFactor = replicationFactor;
            this.etag = etag;
            this.databaseName = Optional.ofNullable(databaseRecord).map(x -> x.getDatabaseName()).orElseThrow(() -> new IllegalArgumentException("Database name is required"));
        }

        @Override
        public HttpRequestBase createRequest(ServerNode node, Reference<String> url) {
            url.value = node.getUrl() + "/admin/databases?name=" + databaseName;

            url.value += "&replicationFactor=" + replicationFactor;

            try {
                String databaseDocument = mapper.writeValueAsString(databaseRecord);
                HttpPut request = new HttpPut();
                request.setEntity(new StringEntity(databaseDocument, ContentType.APPLICATION_JSON));


                if (etag != null) {
                    request.addHeader(Constants.Headers.ETAG,"\"" + etag + "\"");
                }

                return request;
            } catch (JsonProcessingException e) {
                throw ExceptionsUtils.unwrapException(e);
            }
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                throwInvalidResponse();
            }

            result = mapper.readValue(response, DatabasePutResult.class);
        }

        @Override
        public boolean isReadRequest() {
            return false;
        }

        @Override
        public String getRaftUniqueRequestId() {
            return RaftIdGenerator.newId();
        }
    }
}
