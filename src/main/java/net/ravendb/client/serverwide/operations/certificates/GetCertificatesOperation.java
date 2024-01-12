package net.ravendb.client.serverwide.operations.certificates;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.serverwide.operations.IServerOperation;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.io.IOException;

public class GetCertificatesOperation implements IServerOperation<CertificateDefinition[]> {

    private final int _start;
    private final int _pageSize;

    public GetCertificatesOperation(int start, int pageSize) {
        _start = start;
        _pageSize = pageSize;
    }

    @Override
    public RavenCommand<CertificateDefinition[]> getCommand(DocumentConventions conventions) {
        return new GetCertificatesCommand(_start, _pageSize);
    }

    private static class GetCertificatesCommand extends RavenCommand<CertificateDefinition[]> {
        private final int _start;
        private final int _pageSize;

        public GetCertificatesCommand(int start, int pageSize) {
            super(CertificateDefinition[].class);
            
            _start = start;
            _pageSize = pageSize;
        }

        @Override
        public boolean isReadRequest() {
            return false;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/admin/certificates?start=" + _start + "&pageSize=" + _pageSize;
            return new HttpGet(url);
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                return;
            }

            GetCertificatesResponse certificates = mapper.readValue(response, GetCertificatesResponse.class);
            result = certificates.getResults().toArray(new CertificateDefinition[0]);
        }
    }

}
