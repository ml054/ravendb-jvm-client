package net.ravendb.client.http;

import java.util.HashMap;
import java.util.Map;

public class ClusterTopology {

    private String lastNodeId;
    private String topologyId;
    private long etag;

    private Map<String, String> members;
    private Map<String, String> promotables;
    private Map<String, String> watchers;

    public boolean contains(String node) {
        if (members != null && members.containsKey(node)) {
            return true;
        }
        if (promotables != null && promotables.containsKey(node)) {
            return true;
        }

        return watchers != null && watchers.containsKey(node);
    }

    public String getUrlFromTag(String tag) {
        if (tag == null) {
            return null;
        }

        if (members != null && members.containsKey(tag)) {
            return members.get(tag);
        }

        if (promotables != null && promotables.containsKey(tag)) {
            return promotables.get(tag);
        }

        if (watchers != null && watchers.containsKey(tag)) {
            return watchers.get(tag);
        }

        return null;
    }

    public Map<String, String> getAllNodes() {
        Map<String, String> result = new HashMap<>();
        if (members != null) {
            result.putAll(members);
        }

        if (promotables != null) {
            result.putAll(promotables);
        }

        if (watchers != null) {
            result.putAll(watchers);
        }

        return result;
    }

    public String getLastNodeId() {
        return lastNodeId;
    }

    public void setLastNodeId(String lastNodeId) {
        this.lastNodeId = lastNodeId;
    }

    public String getTopologyId() {
        return topologyId;
    }

    public void setTopologyId(String topologyId) {
        this.topologyId = topologyId;
    }

    public Map<String, String> getMembers() {
        return members;
    }

    public void setMembers(Map<String, String> members) {
        this.members = members;
    }

    public Map<String, String> getPromotables() {
        return promotables;
    }

    public void setPromotables(Map<String, String> promotables) {
        this.promotables = promotables;
    }

    public Map<String, String> getWatchers() {
        return watchers;
    }

    public void setWatchers(Map<String, String> watchers) {
        this.watchers = watchers;
    }

    public long getEtag() {
        return etag;
    }

    public void setEtag(long etag) {
        this.etag = etag;
    }
}
