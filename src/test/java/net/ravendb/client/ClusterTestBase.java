package net.ravendb.client;

import com.fasterxml.jackson.databind.JsonNode;
import net.ravendb.client.documents.DocumentStore;
import net.ravendb.client.documents.IDocumentStore;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.driver.RavenServerLocator;
import net.ravendb.client.driver.RavenTestDriver;
import net.ravendb.client.http.ClusterRequestExecutor;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.infrastructure.AdminJsConsoleOperation;
import net.ravendb.client.primitives.CleanCloseable;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.serverwide.DatabaseRecord;
import net.ravendb.client.serverwide.commands.GetClusterTopologyCommand;
import net.ravendb.client.serverwide.operations.CreateDatabaseOperation;
import net.ravendb.client.serverwide.operations.DatabasePutResult;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ClusterTestBase extends RavenTestDriver {

    private static class TestCloudServiceLocator extends RavenServerLocator {
        @Override
        public String[] getCommandArguments() {
            return new String[] {
                    "--ServerUrl=http://127.0.0.1:0",
                    "--Features.Availability=Experimental"
            };
        }
    }

    private AtomicInteger dbCounter = new AtomicInteger(1);

    protected String getDatabaseName() {
        return "db_" + dbCounter.incrementAndGet();
    }

    private RavenServerLocator locator = new TestCloudServiceLocator();

    protected ClusterController createRaftCluster(int numberOfNodes) throws Exception {

        ClusterController cluster = new ClusterController();
        cluster.nodes = new ArrayList<>();

        String[] allowedNodeTags = new String[] { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z" };

        int leaderIndex = 0;
        String leaderNodeTag = allowedNodeTags[leaderIndex];

        for (int i = 0; i < numberOfNodes; i++) {
            Reference<Process> processReference = new Reference<>();
            IDocumentStore store = runServerInternal(locator, processReference, null);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> killProcess(processReference.value)));

            ClusterNode clusterNode = new ClusterNode();
            clusterNode.serverProcess = processReference.value;
            clusterNode.store = store;
            clusterNode.url = store.getUrls()[0];
            clusterNode.nodeTag = allowedNodeTags[i];
            clusterNode.leader = i == leaderIndex;

            cluster.nodes.add(clusterNode);
        }

        cluster.executeJsScript(leaderNodeTag,
                "server.ServerStore.EnsureNotPassive(null, \"" + leaderNodeTag + "\");");

        if (numberOfNodes > 1) {
            // add nodes to cluster
            for (int i = 0; i < numberOfNodes; i++) {
                if (i == leaderIndex) {
                    continue;
                }

                String nodeTag = allowedNodeTags[i];
                String url = cluster.nodes.get(i).url;
                cluster.executeJsScript(leaderNodeTag,
                        "server.ServerStore.ValidateFixedPort = false;" +
                                "server.ServerStore.AddNodeToClusterAsync(\"" + url + "\", \"" + nodeTag + "\", false, false, server.ServerStore.ServerShutdown).Wait();");

                cluster.executeJsScript(nodeTag,
                        "server.ServerStore.WaitForTopology(0, server.ServerStore.ServerShutdown).Wait();");
            }
        }

        return cluster;
    }

    public static class ClusterController implements CleanCloseable {
        public List<ClusterNode> nodes;

        public JsonNode executeJsScript(String nodeTag, String script) {
            ClusterNode targetNode = getNodeByTag(nodeTag);

            try (IDocumentStore store = new DocumentStore(targetNode.url, null)) {
                store.getConventions().setDisableTopologyUpdates(true);
                store.initialize();

                return store.maintenance().server().send(new AdminJsConsoleOperation(script));
            }
        }

        public JsonNode executeJsScriptRaw(String nodeTag, String script) throws Exception {
            ClusterNode targetNode = getNodeByTag(nodeTag);

            AdminJsConsoleOperation jsConsole = new AdminJsConsoleOperation(script);
            RavenCommand<JsonNode> command = jsConsole.getCommand(new DocumentConventions());

            Reference<String> urlRef = new Reference<>();
            ServerNode serverNode = new ServerNode();
            serverNode.setUrl(targetNode.getUrl());
            HttpRequestBase request = command.createRequest(serverNode, urlRef);
            request.setURI(new URI(urlRef.value));

            try (DocumentStore store = new DocumentStore(targetNode.url, "_")) {
                store.initialize();

                CloseableHttpClient httpClient = store.getRequestExecutor().getHttpClient();

                CloseableHttpResponse response = command.send(httpClient, request);

                if (response.getEntity() != null) {
                    return store.getConventions().getEntityMapper().readTree(response.getEntity().getContent());
                }

                return null;
            }
        }

        public ClusterNode getNodeByTag(String nodeTag) {
            return nodes
                    .stream()
                    .filter(x -> nodeTag.equals(x.nodeTag))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unable to find node with tag: " + nodeTag));
        }

        public String getCurrentLeader(IDocumentStore store) {
            GetClusterTopologyCommand command = new GetClusterTopologyCommand();
            store.getRequestExecutor().execute(command);

            return command.getResult().getLeader();
        }

        public void disposeServer(String nodeTag) {
            try {
                executeJsScriptRaw(nodeTag, "server.Dispose()");
            } catch (Exception e) {
                // we likely throw as server won't be able to respond
            }
        }

        public ClusterNode getInitialLeader() {
            return nodes
                    .stream()
                    .filter(x -> x.leader)
                    .findFirst()
                    .orElse(null);
        }

        public void createDatabase(DatabaseRecord databaseRecord, int replicationFactor, String leaderUrl) {
            try (IDocumentStore store = new DocumentStore(leaderUrl, databaseRecord.getDatabaseName())) {
                store.initialize();

                DatabasePutResult putResult = store.maintenance().server().send(new CreateDatabaseOperation(databaseRecord, replicationFactor));

                for (ClusterNode node : nodes) {
                    executeJsScript(node.nodeTag, "server.ServerStore.Cluster.WaitForIndexNotification(\"" + putResult.getRaftCommandIndex() + "\").Wait()");
                }
            }
        }

        @Override
        public void close() {
            for (ClusterNode node : nodes) {
                try {
                    node.serverProcess.destroyForcibly();
                } catch (Exception e) {
                    //ignore
                }
            }
        }
    }

    public static class ClusterNode {
        private String nodeTag;
        private String url;
        private boolean leader;
        private IDocumentStore store;
        private Process serverProcess;

        public String getNodeTag() {
            return nodeTag;
        }

        public void setNodeTag(String nodeTag) {
            this.nodeTag = nodeTag;
        }

        public Process getServerProcess() {
            return serverProcess;
        }

        public void setServerProcess(Process serverProcess) {
            this.serverProcess = serverProcess;
        }

        public IDocumentStore getStore() {
            return store;
        }

        public void setStore(IDocumentStore store) {
            this.store = store;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isLeader() {
            return leader;
        }

        public void setLeader(boolean leader) {
            this.leader = leader;
        }
    }
}

