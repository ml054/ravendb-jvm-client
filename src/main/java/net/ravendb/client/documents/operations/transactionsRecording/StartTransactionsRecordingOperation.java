package net.ravendb.client.documents.operations.transactionsRecording;

import com.fasterxml.jackson.core.JsonGenerator;
import net.ravendb.client.documents.conventions.DocumentConventions;

import net.ravendb.client.http.ServerNode;
import net.ravendb.client.http.VoidRavenCommand;
import net.ravendb.client.json.ContentProviderHttpEntity;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.serverwide.operations.IVoidMaintenanceOperation;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;

import java.io.IOException;

public class StartTransactionsRecordingOperation implements IVoidMaintenanceOperation {

    private final String _filePath;

    public StartTransactionsRecordingOperation(String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("FilePath cannot be null");
        }
        _filePath = filePath;
    }

    @Override
    public VoidRavenCommand getCommand(DocumentConventions conventions) {
        return new StartTransactionsRecordingCommand(conventions, _filePath);
    }

    private static class StartTransactionsRecordingCommand extends VoidRavenCommand {
        private final String _filePath;
        private final DocumentConventions _conventions;

        public StartTransactionsRecordingCommand(DocumentConventions conventions, String filePath) {
            _filePath = filePath;
            _conventions = conventions;
        }

        @Override
        public HttpRequestBase createRequest(ServerNode node, Reference<String> url) {
            url.value = node.getUrl() + "/databases/" + node.getDatabase() + "/admin/transactions/start-recording";

            HttpPost request = new HttpPost();
            request.setEntity(new ContentProviderHttpEntity(outputStream -> {
                try (JsonGenerator generator = createSafeJsonGenerator(outputStream)) {
                    generator.writeStartObject();
                    generator.writeStringField("File", _filePath);
                    generator.writeEndObject();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, ContentType.APPLICATION_JSON, _conventions));
            return request;
        }
    }
}
