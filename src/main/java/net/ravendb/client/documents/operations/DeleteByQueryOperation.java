package net.ravendb.client.documents.operations;

import com.fasterxml.jackson.core.JsonGenerator;
import net.ravendb.client.documents.IDocumentStore;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.queries.IndexQuery;
import net.ravendb.client.documents.queries.QueryOperationOptions;
import net.ravendb.client.extensions.JsonExtensions;
import net.ravendb.client.http.HttpCache;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.json.ContentProviderHttpEntity;
import net.ravendb.client.util.TimeUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;

public class DeleteByQueryOperation implements IOperation<OperationIdResult> {

    protected IndexQuery _queryToDelete;
    private final QueryOperationOptions _options;

    public DeleteByQueryOperation(String queryToDelete) {
        this(new IndexQuery(queryToDelete));
    }

    public DeleteByQueryOperation(IndexQuery queryToDelete) {
        this(queryToDelete, null);
    }

    public DeleteByQueryOperation(IndexQuery queryToDelete, QueryOperationOptions options) {
        if (queryToDelete == null) {
            throw new IllegalArgumentException("QueryToDelete cannot be null");
        }
        _queryToDelete = queryToDelete;
        _options = options;
    }

    @Override
    public RavenCommand<OperationIdResult> getCommand(IDocumentStore store, DocumentConventions conventions, HttpCache cache) {
        return new DeleteByIndexCommand(conventions, _queryToDelete, _options);
    }

    private static class DeleteByIndexCommand extends RavenCommand<OperationIdResult> {
        private final DocumentConventions _conventions;
        private final IndexQuery _queryToDelete;
        private final QueryOperationOptions _options;

        public DeleteByIndexCommand(DocumentConventions conventions, IndexQuery queryToDelete, QueryOperationOptions options) {
            super(OperationIdResult.class);
            _conventions = conventions;
            _queryToDelete = queryToDelete;
            _options = ObjectUtils.firstNonNull(options, new QueryOperationOptions());
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            StringBuilder path = new StringBuilder(node.getUrl())
                    .append("/databases/")
                    .append(node.getDatabase())
                    .append("/queries")
                    .append("?allowStale=")
                    .append(_options.isAllowStale());

            if (_options.getMaxOpsPerSecond() != null) {
                path.append("&maxOpsPerSec=")
                        .append(_options.getMaxOpsPerSecond());
            }

            path
                .append("&details=")
                .append(_options.isRetrieveDetails());

            if (_options.getStaleTimeout() != null) {
                path.append("&staleTimeout=")
                        .append(TimeUtils.durationToTimeSpan(_options.getStaleTimeout()));
            }

            HttpDelete request = new HttpDelete(path.toString());
            request.setEntity(new ContentProviderHttpEntity(outputStream -> {
                try (JsonGenerator generator = createSafeJsonGenerator(outputStream)) {
                    JsonExtensions.writeIndexQuery(generator, _conventions, _queryToDelete);
                }
            }, ContentType.APPLICATION_JSON, _conventions));

            return request;
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                throwInvalidResponse();
            }

            result = mapper.readValue(response, OperationIdResult.class);
        }

        @Override
        public boolean isReadRequest() {
            return false;
        }
    }
}
