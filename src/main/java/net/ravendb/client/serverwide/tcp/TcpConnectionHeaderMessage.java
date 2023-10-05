package net.ravendb.client.serverwide.tcp;

import com.fasterxml.jackson.core.JsonGenerator;
import net.ravendb.client.documents.operations.replication.DetailedReplicationHubAccess;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.primitives.SharpEnum;
import net.ravendb.client.primitives.UseSharpEnum;

import java.io.IOException;
import java.util.*;

public class TcpConnectionHeaderMessage {

    @UseSharpEnum
    public enum OperationTypes {
        NONE,
        DROP,
        SUBSCRIPTION,
        REPLICATION,
        CLUSTER,
        HEARTBEATS,
        PING,
        TEST_CONNECTION
    }

    private String databaseName;
    private String sourceNodeTag;

    private String serverId;
    private OperationTypes operation;
    private int operationVersion;
    private String info;
    private AuthorizationInfo authorizeInfo;
    private LicensedFeatures licensedFeatures;
    private DetailedReplicationHubAccess replicationHubAccess;

    public AuthorizationInfo getAuthorizeInfo() {
        return authorizeInfo;
    }

    public void setAuthorizeInfo(AuthorizationInfo authorizeInfo) {
        this.authorizeInfo = authorizeInfo;
    }

    public LicensedFeatures getLicensedFeatures() {
        return licensedFeatures;
    }

    public void setLicensedFeatures(LicensedFeatures licensedFeatures) {
        this.licensedFeatures = licensedFeatures;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSourceNodeTag() {
        return sourceNodeTag;
    }

    public void setSourceNodeTag(String sourceNodeTag) {
        this.sourceNodeTag = sourceNodeTag;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public OperationTypes getOperation() {
        return operation;
    }

    public void setOperation(OperationTypes operation) {
        this.operation = operation;
    }

    public int getOperationVersion() {
        return operationVersion;
    }

    public void setOperationVersion(int operationVersion) {
        this.operationVersion = operationVersion;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public static final int NUMBER_OR_RETRIES_FOR_SENDING_TCP_HEADER = 2;


    public static final int PING_BASE_LINE = -1;
    public static final int NONE_BASE_LINE = -1;
    public static final int DROP_BASE_LINE = -2;
    public static final int HEARTBEATS_BASE_LINE = 20;
    public static final int HEARTBEATS_41200 = 41_200;
    public static final int HEARTBEATS_42000 = 42_000;
    public static final int HEARTBEATS_WITH_TCP_COMPRESSION = 54_000;
    public static final int SUBSCRIPTION_BASE_LINE = 40;
    public static final int SUBSCRIPTION_INCLUDES = 41_400;
    public static final int SUBSCRIPTION_COUNTER_INCLUDES = 50_000;
    public static final int SUBSCRIPTION_TIME_SERIES_INCLUDES = 51_000;
    public static final int TCP_CONNECTIONS_WITH_COMPRESSION = 53_000;
    public static final int TEST_CONNECTION_BASE_LINE = 50;

    public static final int HEARTBEATS_TCP_VERSION = HEARTBEATS_WITH_TCP_COMPRESSION;
    public static final int SUBSCRIPTION_TCP_VERSION = TCP_CONNECTIONS_WITH_COMPRESSION;
    public static final int TEST_CONNECTION_TCP_VERSION = TEST_CONNECTION_BASE_LINE;

    public static class SupportedFeatures {
        public final int protocolVersion;

        public SupportedFeatures(int version) {
            protocolVersion = version;
        }

        protected SupportedFeatures(SupportedFeatures source) {
            protocolVersion = source.protocolVersion;
            ping = source.ping;
            none = source.none;
            drop = source.drop;
            subscription = source.subscription;
            heartbeats = source.heartbeats;
            testConnection = source.testConnection;
            dataCompression = source.dataCompression;
        }

        public static class PingFeatures {
            public boolean baseLine = true;
        }

        public static class NoneFeatures {
            public boolean baseLine = true;
        }

        public static class DropFeatures {
            public boolean baseLine = true;
        }

        public static class SubscriptionFeatures {
            public boolean baseLine = true;
            public boolean includes;
            public boolean counterIncludes;
            public boolean timeSeriesIncludes;
        }

        public static class HeartbeatsFeatures {
            public boolean baseLine = true;
            public boolean sendChangesOnly;
            public boolean includeServerInfo;
        }

        public static class TestConnectionFeatures {
            public boolean baseLine = true;
        }

        public PingFeatures ping;
        public NoneFeatures none;
        public DropFeatures drop;
        public SubscriptionFeatures subscription;
        public HeartbeatsFeatures heartbeats;
        public TestConnectionFeatures testConnection;

        public boolean dataCompression;
    }

    private static final Map<OperationTypes, List<Integer>> operationsToSupportedProtocolVersions = new HashMap<>();
    private static final Map<OperationTypes, Map<Integer, SupportedFeatures>> supportedFeaturesByProtocol = new HashMap<>();

    static {
        operationsToSupportedProtocolVersions.put(OperationTypes.PING, Collections.singletonList(PING_BASE_LINE));
        operationsToSupportedProtocolVersions.put(OperationTypes.NONE, Collections.singletonList(NONE_BASE_LINE));
        operationsToSupportedProtocolVersions.put(OperationTypes.DROP, Collections.singletonList(DROP_BASE_LINE));
        operationsToSupportedProtocolVersions.put(OperationTypes.SUBSCRIPTION, Arrays.asList(TCP_CONNECTIONS_WITH_COMPRESSION, SUBSCRIPTION_TIME_SERIES_INCLUDES, SUBSCRIPTION_COUNTER_INCLUDES, SUBSCRIPTION_INCLUDES, SUBSCRIPTION_BASE_LINE));
        operationsToSupportedProtocolVersions.put(OperationTypes.HEARTBEATS, Arrays.asList(HEARTBEATS_TCP_VERSION, HEARTBEATS_42000, HEARTBEATS_41200, HEARTBEATS_BASE_LINE));
        operationsToSupportedProtocolVersions.put(OperationTypes.TEST_CONNECTION, Collections.singletonList(TEST_CONNECTION_BASE_LINE));

        Map<Integer, SupportedFeatures> pingFeaturesMap = new HashMap<>();
        supportedFeaturesByProtocol.put(OperationTypes.PING, pingFeaturesMap);
        SupportedFeatures pingFeatures = new SupportedFeatures(PING_BASE_LINE);
        pingFeatures.ping = new SupportedFeatures.PingFeatures();
        pingFeaturesMap.put(PING_BASE_LINE, pingFeatures);

        Map<Integer, SupportedFeatures> noneFeaturesMap = new HashMap<>();
        supportedFeaturesByProtocol.put(OperationTypes.NONE, noneFeaturesMap);
        SupportedFeatures noneFeatures = new SupportedFeatures(NONE_BASE_LINE);
        noneFeatures.none = new SupportedFeatures.NoneFeatures();
        noneFeaturesMap.put(NONE_BASE_LINE, noneFeatures);

        Map<Integer, SupportedFeatures> dropFeaturesMap = new HashMap<>();
        supportedFeaturesByProtocol.put(OperationTypes.DROP, dropFeaturesMap);
        SupportedFeatures dropFeatures = new SupportedFeatures(DROP_BASE_LINE);
        dropFeatures.drop = new SupportedFeatures.DropFeatures();
        dropFeaturesMap.put(DROP_BASE_LINE, dropFeatures);

        Map<Integer, SupportedFeatures> subscriptionFeaturesMap = new HashMap<>();
        supportedFeaturesByProtocol.put(OperationTypes.SUBSCRIPTION, subscriptionFeaturesMap);
        SupportedFeatures subscriptionFeatures = new SupportedFeatures(SUBSCRIPTION_BASE_LINE);
        subscriptionFeatures.subscription = new SupportedFeatures.SubscriptionFeatures();
        subscriptionFeaturesMap.put(SUBSCRIPTION_BASE_LINE, subscriptionFeatures);

        SupportedFeatures subscriptions41400Features = new SupportedFeatures(SUBSCRIPTION_INCLUDES);
        subscriptions41400Features.subscription = new SupportedFeatures.SubscriptionFeatures();
        subscriptions41400Features.subscription.includes = true;
        subscriptionFeaturesMap.put(SUBSCRIPTION_INCLUDES, subscriptions41400Features);

        SupportedFeatures subscriptions50000Features = new SupportedFeatures(SUBSCRIPTION_COUNTER_INCLUDES);
        subscriptions50000Features.subscription = new SupportedFeatures.SubscriptionFeatures();
        subscriptions50000Features.subscription.includes = true;
        subscriptions50000Features.subscription.counterIncludes = true;
        subscriptionFeaturesMap.put(SUBSCRIPTION_COUNTER_INCLUDES, subscriptions50000Features);

        SupportedFeatures subscriptions51000Features = new SupportedFeatures(SUBSCRIPTION_TIME_SERIES_INCLUDES);
        subscriptions51000Features.subscription = new SupportedFeatures.SubscriptionFeatures();
        subscriptions51000Features.subscription.includes = true;
        subscriptions51000Features.subscription.counterIncludes = true;
        subscriptions51000Features.subscription.timeSeriesIncludes = true;
        subscriptionFeaturesMap.put(SUBSCRIPTION_TIME_SERIES_INCLUDES, subscriptions51000Features);

        SupportedFeatures subscriptions5300Features = new SupportedFeatures(TCP_CONNECTIONS_WITH_COMPRESSION);
        subscriptions5300Features.dataCompression = true;
        subscriptions51000Features.subscription = new SupportedFeatures.SubscriptionFeatures();
        subscriptions51000Features.subscription.includes = true;
        subscriptions51000Features.subscription.counterIncludes = true;
        subscriptions51000Features.subscription.timeSeriesIncludes = true;

        subscriptionFeaturesMap.put(TCP_CONNECTIONS_WITH_COMPRESSION, subscriptions5300Features);

        Map<Integer, SupportedFeatures> heartbeatsFeaturesMap = new HashMap<>();
        supportedFeaturesByProtocol.put(OperationTypes.HEARTBEATS, heartbeatsFeaturesMap);
        SupportedFeatures heartbeatsFeatures = new SupportedFeatures(HEARTBEATS_BASE_LINE);
        heartbeatsFeatures.heartbeats = new SupportedFeatures.HeartbeatsFeatures();
        heartbeatsFeaturesMap.put(HEARTBEATS_BASE_LINE, heartbeatsFeatures);

        SupportedFeatures heartbeats41200Features = new SupportedFeatures(HEARTBEATS_41200);
        heartbeats41200Features.heartbeats = new SupportedFeatures.HeartbeatsFeatures();
        heartbeats41200Features.heartbeats.sendChangesOnly = true;
        heartbeatsFeaturesMap.put(HEARTBEATS_41200, heartbeats41200Features);

        SupportedFeatures heartbeats42000Features = new SupportedFeatures(HEARTBEATS_42000);
        heartbeats42000Features.heartbeats = new SupportedFeatures.HeartbeatsFeatures();
        heartbeats42000Features.heartbeats.sendChangesOnly = true;
        heartbeats42000Features.heartbeats.includeServerInfo = true;
        heartbeatsFeaturesMap.put(HEARTBEATS_42000, heartbeats42000Features);

        SupportedFeatures heartbeatsWithTcpCompressionFeatures = new SupportedFeatures(HEARTBEATS_WITH_TCP_COMPRESSION);
        heartbeatsWithTcpCompressionFeatures.heartbeats = new SupportedFeatures.HeartbeatsFeatures();
        heartbeatsWithTcpCompressionFeatures.heartbeats.sendChangesOnly = true;
        heartbeatsWithTcpCompressionFeatures.heartbeats.includeServerInfo = true;
        heartbeatsWithTcpCompressionFeatures.dataCompression = true;
        heartbeatsFeaturesMap.put(HEARTBEATS_WITH_TCP_COMPRESSION, heartbeatsWithTcpCompressionFeatures);

        Map<Integer, SupportedFeatures> testConnectionFeaturesMap = new HashMap<>();
        supportedFeaturesByProtocol.put(OperationTypes.TEST_CONNECTION, testConnectionFeaturesMap);
        SupportedFeatures testConnectionFeatures = new SupportedFeatures(TEST_CONNECTION_BASE_LINE);
        testConnectionFeatures.testConnection = new SupportedFeatures.TestConnectionFeatures();
        testConnectionFeaturesMap.put(TEST_CONNECTION_BASE_LINE, testConnectionFeatures);

        // validate
        OperationTypes[] operations = {
                OperationTypes.CLUSTER,
                OperationTypes.DROP,
                OperationTypes.HEARTBEATS,
                OperationTypes.NONE,
                OperationTypes.PING,
                OperationTypes.REPLICATION,
                OperationTypes.SUBSCRIPTION,
                OperationTypes.TEST_CONNECTION
        };
    }

    @UseSharpEnum
    public enum SupportedStatus {
        OUT_OF_RANGE,
        NOT_SUPPORTED,
        SUPPORTED
    }

    public static class NegotiationResponse {
        public int version;
        public LicensedFeatures licensedFeatures;
    }

    public static SupportedStatus operationVersionSupported(OperationTypes operationType, int version, Reference<Integer> currentRef) {
        currentRef.value = -1;
        List<Integer> supportedProtocols = operationsToSupportedProtocolVersions.get(operationType);
        if (supportedProtocols == null) {
            throw new IllegalStateException("This is a bug. Probably you forgot to add '" + operationType + "' operation to the operationsToSupportedProtocolVersions map");
        }

        for (int i = 0; i < supportedProtocols.size(); i++) {
            currentRef.value = supportedProtocols.get(i);
            if (currentRef.value == version) {
                return SupportedStatus.SUPPORTED;
            }

            if (currentRef.value < version) {
                return SupportedStatus.NOT_SUPPORTED;
            }
        }

        return SupportedStatus.OUT_OF_RANGE;
    }

    public static int getOperationTcpVersion(OperationTypes operationType, int index) {
        // we don't check the if the index go out of range, since this is expected and means that we don't have
        switch (operationType) {
            case PING:
            case NONE:
                return -1;
            case DROP:
                return -2;
            case SUBSCRIPTION:
            case REPLICATION:
            case CLUSTER:
            case HEARTBEATS:
            case TEST_CONNECTION:
                return operationsToSupportedProtocolVersions.get(operationType).get(index);
            default:
                throw new IllegalArgumentException("operationType");
        }
    }

    public static SupportedFeatures getSupportedFeaturesFor(OperationTypes type, int protocolVersion) {
        SupportedFeatures features = supportedFeaturesByProtocol.get(type).get(protocolVersion);
        if (features == null) {
            throw new IllegalArgumentException(type + " in protocol " + protocolVersion + " was not found in the features set");
        }
        return features;
    }

    public static class AuthorizationInfo {
        @UseSharpEnum
        public enum AuthorizeMethod {
            SERVER,
            PULL_REPLICATION,
            PUSH_REPLICATION
        }

        public AuthorizeMethod authorizeAs;
        public String authorizationFor;

        public void toJson(JsonGenerator generator) throws IOException {
            generator.writeStartObject();
            generator.writeStringField("AuthorizeAs", SharpEnum.value(authorizeAs));
            generator.writeStringField("AuthorizationFor", authorizationFor);
            generator.writeEndObject();
        }

    }
}
