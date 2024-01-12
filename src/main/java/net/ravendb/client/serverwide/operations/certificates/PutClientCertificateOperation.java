package net.ravendb.client.serverwide.operations.certificates;

import com.fasterxml.jackson.core.JsonGenerator;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.http.VoidRavenCommand;
import net.ravendb.client.json.ContentProviderHttpEntity;
import net.ravendb.client.primitives.SharpEnum;
import net.ravendb.client.serverwide.operations.IVoidServerOperation;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ContentType;

import java.util.Map;

public class PutClientCertificateOperation implements IVoidServerOperation {

    private final String _certificate;
    private final Map<String, DatabaseAccess> _permissions;
    private final String _name;
    private final SecurityClearance _clearance;
    private final String _twoFactorAuthenticationKey;

    public PutClientCertificateOperation(String name, String certificate, Map<String, DatabaseAccess> permissions, SecurityClearance clearance) {
        this(name, certificate, permissions, clearance, null);
    }

    public PutClientCertificateOperation(String name, String certificate, Map<String, DatabaseAccess> permissions, SecurityClearance clearance, String twoFactorAuthenticationKey) {
        if (certificate == null) {
            throw new IllegalArgumentException("Certificate cannot be null");
        }

        if (permissions == null) {
            throw new IllegalArgumentException("Permissions cannot be null");
        }

        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        _certificate = certificate;
        _permissions = permissions;
        _name = name;
        _clearance = clearance;
        _twoFactorAuthenticationKey = twoFactorAuthenticationKey;
    }

    @Override
    public VoidRavenCommand getCommand(DocumentConventions conventions) {
        return new PutClientCertificateCommand(conventions, _name, _certificate, _permissions, _clearance, _twoFactorAuthenticationKey);
    }

    private static class PutClientCertificateCommand extends VoidRavenCommand implements IRaftCommand {
        private final DocumentConventions _conventions;

        private final String _certificate;
        private final Map<String, DatabaseAccess> _permissions;
        private final String _name;
        private final SecurityClearance _clearance;
        private final String _twoFactorAuthenticationKey;


        public PutClientCertificateCommand(DocumentConventions conventions,
                                           String name,
                                           String certificate,
                                           Map<String, DatabaseAccess> permissions,
                                           SecurityClearance clearance,
                                           String twoFactorAuthenticationKey) {
            if (certificate == null) {
                throw new IllegalArgumentException("Certificate cannot be null");
            }
            if (permissions == null) {
                throw new IllegalArgumentException("Permissions cannot be null");
            }

            _conventions = conventions;
            _certificate = certificate;
            _permissions = permissions;
            _name = name;
            _clearance = clearance;
            _twoFactorAuthenticationKey = twoFactorAuthenticationKey;
        }

        @Override
        public boolean isReadRequest() {
            return false;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/admin/certificates";

            HttpPut request = new HttpPut(url);

            request.setEntity(new ContentProviderHttpEntity(outputStream -> {
                try (JsonGenerator generator = createSafeJsonGenerator(outputStream)) {

                    generator.writeStartObject();
                    generator.writeStringField("Name", _name);
                    generator.writeStringField("Certificate", _certificate);
                    generator.writeStringField("SecurityClearance", SharpEnum.value(_clearance));
                    if (_twoFactorAuthenticationKey != null) {
                        generator.writeStringField("TwoFactorAuthenticationKey", _twoFactorAuthenticationKey);
                    }

                    generator.writeFieldName("Permissions");
                    generator.writeStartObject();

                    for (Map.Entry<String, DatabaseAccess> kvp : _permissions.entrySet()) {
                        generator.writeStringField(kvp.getKey(), SharpEnum.value(kvp.getValue()));
                    }
                    generator.writeEndObject();
                    generator.writeEndObject();
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
