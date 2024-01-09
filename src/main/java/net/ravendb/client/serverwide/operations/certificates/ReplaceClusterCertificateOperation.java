package net.ravendb.client.serverwide.operations.certificates;

import com.fasterxml.jackson.core.JsonGenerator;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.http.VoidRavenCommand;
import net.ravendb.client.json.ContentProviderHttpEntity;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.serverwide.operations.IVoidServerOperation;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;

import java.io.IOException;

public class ReplaceClusterCertificateOperation implements IVoidServerOperation {

    private final byte[] _certBytes;
    private final boolean _replaceImmediately;

    public ReplaceClusterCertificateOperation(byte[] certBytes, boolean replaceImmediately) {
        if (certBytes == null) {
            throw new IllegalArgumentException("CertBytes cannot be null");
        }

        _certBytes = certBytes;
        _replaceImmediately = replaceImmediately;
    }

    @Override
    public VoidRavenCommand getCommand(DocumentConventions conventions) {
        return new ReplaceClusterCertificateCommand(conventions, _certBytes, _replaceImmediately);
    }

    private static class ReplaceClusterCertificateCommand extends VoidRavenCommand implements IRaftCommand {
        private final DocumentConventions _conventions;
        private final byte[] _certBytes;
        private final boolean _replaceImmediately;

        public ReplaceClusterCertificateCommand(DocumentConventions conventions, byte[] certBytes, boolean replaceImmediately) {
            if (certBytes == null) {
                throw new IllegalArgumentException("CertBytes cannot be null");
            }

            _conventions = conventions;
            _certBytes = certBytes;
            _replaceImmediately = replaceImmediately;
        }

        @Override
        public boolean isReadRequest() {
            return false;
        }

        @Override
        public HttpRequestBase createRequest(ServerNode node, Reference<String> url) {
            url.value = node.getUrl() + "/admin/certificates/replace-cluster-cert?replaceImmediately=" + (_replaceImmediately  ? "true" : "false");

            HttpPost request = new HttpPost();
            request.setEntity(new ContentProviderHttpEntity(outputStream -> {
                try (JsonGenerator generator = createSafeJsonGenerator(outputStream)) {
                    generator.writeStartObject();
                    generator.writeFieldName("Certificate");
                    generator.writeString(Base64.encodeBase64String(_certBytes));
                    generator.writeEndObject();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, ContentType.APPLICATION_JSON, _conventions));
            return request;
        }

        @Override
        public String getRaftUniqueRequestId() {
            return RaftIdGenerator.newId();
        }
    }
}
