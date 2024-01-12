package net.ravendb.client.http;

import net.ravendb.client.primitives.UseSharpEnum;

import java.util.ArrayList;
import java.util.Map;

public class ServerNode {

    @UseSharpEnum
    public enum Role {
        NONE,
        PROMOTABLE,
        MEMBER,
        REHAB
    }

    private String url;
    private String database;
    private String clusterTag;
    private Role serverRole;

    public ServerNode() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getClusterTag() {
        return clusterTag;
    }

    public void setClusterTag(String clusterTag) {
        this.clusterTag = clusterTag;
    }

    public Role getServerRole() {
        return serverRole;
    }

    public void setServerRole(Role serverRole) {
        this.serverRole = serverRole;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServerNode that = (ServerNode) o;

        if (url != null ? !url.equals(that.url) : that.url != null) return false;
        return database != null ? database.equals(that.database) : that.database == null;
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + (database != null ? database.hashCode() : 0);
        return result;
    }

    private int _lastServerVersionCheck = 0;

    private String lastServerVersion;

    public String getLastServerVersion() {
        return lastServerVersion;
    }

    public boolean shouldUpdateServerVersion() {
        if (lastServerVersion == null || _lastServerVersionCheck > 100) {
            return true;
        }

        _lastServerVersionCheck++;
        return false;
    }

    public void updateServerVersion(String serverVersion) {
        this.lastServerVersion = serverVersion;
        _lastServerVersionCheck = 0;

        _supportsAtomicClusterWrites = false;

        if (serverVersion != null) {
            String[] tokens = serverVersion.split("\\.");
            try {
                int major = Integer.parseInt(tokens[0]);
                int minor = Integer.parseInt(tokens[1]);

                if (major > 5 || (major == 5 && minor >= 2)) {
                    _supportsAtomicClusterWrites = true;
                }
            } catch (NumberFormatException ignore) {
            }
        }
    }

    public void discardServerVersion() {
        lastServerVersion = null;
        _lastServerVersionCheck = 0;
    }

    public static Topology createFrom(ClusterTopology topology, long etag) {
        Topology newTopology = new Topology();
        newTopology.setEtag(etag);
        newTopology.setNodes(new ArrayList<>());
        newTopology.setPromotables(new ArrayList<>());

        if (topology == null) {
            return newTopology;
        }

        for (Map.Entry<String, String> kvp : topology.getMembers().entrySet()) {
            ServerNode serverNode = new ServerNode();
            serverNode.setUrl(kvp.getValue());
            serverNode.setClusterTag(kvp.getKey());
            serverNode.setServerRole(Role.MEMBER);
            newTopology.getNodes().add(serverNode);
        }

        for (Map.Entry<String, String> kvp : topology.getWatchers().entrySet()) {
            ServerNode serverNode = new ServerNode();
            serverNode.setUrl(kvp.getValue());
            serverNode.setClusterTag(kvp.getKey());
            serverNode.setServerRole(Role.MEMBER);
            newTopology.getNodes().add(serverNode);
        }

        return newTopology;
    }

    private boolean _supportsAtomicClusterWrites;

    public boolean isSupportsAtomicClusterWrites() {
        return _supportsAtomicClusterWrites;
    }

    public void setSupportsAtomicClusterWrites(boolean supportsAtomicClusterWrites) {
        _supportsAtomicClusterWrites = supportsAtomicClusterWrites;
    }
}
