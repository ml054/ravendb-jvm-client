package net.ravendb.client.documents.operations.analyzers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.indexes.analysis.AnalyzerDefinition;
import net.ravendb.client.documents.operations.IVoidMaintenanceOperation;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.http.VoidRavenCommand;
import net.ravendb.client.json.ContentProviderHttpEntity;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ContentType;


public class PutAnalyzersOperation implements IVoidMaintenanceOperation {
    private final AnalyzerDefinition[] _analyzersToAdd;

    public PutAnalyzersOperation(AnalyzerDefinition... analyzersToAdd) {
        if (analyzersToAdd == null || analyzersToAdd.length == 0) {
            throw new IllegalArgumentException("AnalyzersToAdd cannot be null or empty");
        }

        _analyzersToAdd = analyzersToAdd;
    }

    @Override
    public VoidRavenCommand getCommand(DocumentConventions conventions) {
        return new PutAnalyzersCommand(conventions, _analyzersToAdd);
    }

    private static class PutAnalyzersCommand extends VoidRavenCommand implements IRaftCommand {
        private final ObjectNode[] _analyzersToAdd;
        private final DocumentConventions _conventions;

        public PutAnalyzersCommand(DocumentConventions conventions, AnalyzerDefinition[] analyzersToAdd) {
            if (conventions == null) {
                throw new IllegalArgumentException("Conventions cannot be null");
            }
            if (analyzersToAdd == null) {
                throw new IllegalArgumentException("AnalyzersToAdd cannot be null");
            }

            _conventions = conventions;
            _analyzersToAdd = new ObjectNode[analyzersToAdd.length];

            for (int i = 0; i < analyzersToAdd.length; i++) {
                if (analyzersToAdd[i].getName() == null) {
                    throw new IllegalArgumentException("Name cannot be null");
                }

                _analyzersToAdd[i] = mapper.valueToTree(analyzersToAdd[i]);
            }
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/databases/" + node.getDatabase() + "/admin/analyzers";

            HttpPut request = new HttpPut(url);
            request.setEntity(new ContentProviderHttpEntity(outputStream -> {
                try (JsonGenerator generator = createSafeJsonGenerator(outputStream)) {
                    generator.writeStartObject();
                    generator.writeFieldName("Analyzers");
                    generator.writeStartArray();
                    for (ObjectNode analyzer : _analyzersToAdd) {
                        generator.writeObject(analyzer);
                    }
                    generator.writeEndArray();
                    generator.writeEndObject();
                }
            }, ContentType.APPLICATION_JSON, _conventions));

            return request;
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
