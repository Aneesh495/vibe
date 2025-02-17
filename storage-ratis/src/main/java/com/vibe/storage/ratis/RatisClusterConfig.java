package com.vibe.storage.ratis;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.util.NetUtils;
import org.apache.ratis.util.TimeDuration;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for an Apache Ratis node in the replicated consensus cluster.
 */
public final class RatisClusterConfig {

    public static final UUID CLUSTER_UUID = UUID.fromString("0242ac12-0002-4000-8000-000000000001");
    public static final RaftGroupId DEFAULT_GROUP_ID = RaftGroupId.valueOf(CLUSTER_UUID);

    private final RaftPeerId peerId;
    private final int port;
    private final Path storageDir;
    private final List<RaftPeer> peers;
    private final RaftGroupId groupId;

    public RatisClusterConfig(
            String peerIdStr,
            int port,
            Path storageDir,
            List<RaftPeer> peers,
            RaftGroupId groupId
    ) {
        this.peerId = RaftPeerId.valueOf(peerIdStr);
        this.port = port;
        this.storageDir = storageDir;
        this.peers = Collections.unmodifiableList(new ArrayList<>(peers));
        this.groupId = groupId != null ? groupId : DEFAULT_GROUP_ID;
    }

    public RaftPeerId peerId() {
        return peerId;
    }

    public int port() {
        return port;
    }

    public Path storageDir() {
        return storageDir;
    }

    public List<RaftPeer> peers() {
        return peers;
    }

    public RaftGroupId groupId() {
        return groupId;
    }

    public RaftGroup raftGroup() {
        return RaftGroup.valueOf(groupId, peers);
    }

    public RaftProperties createProperties() {
        RaftProperties properties = new RaftProperties();

        // Storage directory
        RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir.toFile()));

        // Port
        GrpcConfigKeys.Server.setPort(properties, port);

        // Fast election timeouts for snappy tests and failover
        RaftServerConfigKeys.Rpc.setTimeoutMin(properties, TimeDuration.valueOf(200, TimeUnit.MILLISECONDS));
        RaftServerConfigKeys.Rpc.setTimeoutMax(properties, TimeDuration.valueOf(400, TimeUnit.MILLISECONDS));
        RaftServerConfigKeys.Rpc.setRequestTimeout(properties, TimeDuration.valueOf(2000, TimeUnit.MILLISECONDS));

        // Snapshot settings
        RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(properties, true);
        RaftServerConfigKeys.Snapshot.setAutoTriggerThreshold(properties, 1000);

        return properties;
    }

    public static List<RaftPeer> buildPeers(List<String> addresses) {
        List<RaftPeer> peerList = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            String id = "node" + (i + 1);
            peerList.add(RaftPeer.newBuilder().setId(id).setAddress(addresses.get(i)).build());
        }
        return peerList;
    }
}
