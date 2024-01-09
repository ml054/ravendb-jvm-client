package net.ravendb.client.documents.operations.expiration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.IMaintenanceOperation;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.json.ContentProviderHttpEntity;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;

import java.io.IOException;

public class ConfigureExpirationOperation implements IMaintenanceOperation<ConfigureExpirationOperationResult> {

    private final ExpirationConfiguration _configuration;

    public ConfigureExpirationOperation(ExpirationConfiguration configuration) {
        _configuration = configuration;
    }

    @Override
    public RavenCommand<ConfigureExpirationOperationResult> getCommand(DocumentConventions conventions) {
        return new ConfigureExpirationCommand(conventions, _configuration);
    }

    private static class ConfigureExpirationCommand extends RavenCommand<ConfigureExpirationOperationResult> implements IRaftCommand {
        private final ExpirationConfiguration _configuration;
        private final DocumentConventions _conventions;

        public ConfigureExpirationCommand(DocumentConventions conventions, ExpirationConfiguration configuration) {
            super(ConfigureExpirationOperationResult.class);

            if (configuration == null) {
                throw new IllegalArgumentException("Configuration cannot be null");
            }

            _configuration = configuration;
            _conventions = conventions;
        }

        @Override
        public boolean isReadRequest() {
            return false;
        }

        @Override
        public HttpRequestBase createRequest(ServerNode node, Reference<String> url) {
            url.value = node.getUrl() + "/databases/" + node.getDatabase() + "/admin/expiration/config";

            HttpPost request = new HttpPost();
            request.setEntity(new ContentProviderHttpEntity(outputStream -> {
                try (JsonGenerator generator = createSafeJsonGenerator(outputStream)) {
                    ObjectNode config = mapper.valueToTree(_configuration);
                    generator.writeTree(config);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, ContentType.APPLICATION_JSON, _conventions));

            return request;
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                throwInvalidResponse();
            }

            result = mapper.readValue(response, resultClass);
        }

        @Override
        public String getRaftUniqueRequestId() {
            return RaftIdGenerator.newId();
        }
    }
}
