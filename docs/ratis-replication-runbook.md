# Vibe Apache Ratis Replication Runbook

This operational runbook documents the deployment, configuration, leader election, log replication, failover behavior, and maintenance procedures for Vibe's 3-node Raft consensus cluster.

```mermaid
flowchart TD
    subgraph Cluster["3-Node Apache Ratis Consensus Group"]
        Node1["Node 1 (Leader, Port 9100)"]
        Node2["Node 2 (Follower, Port 9101)"]
        Node3["Node 3 (Follower, Port 9102)"]
    end

    Client["Client / SDK"] -->|Execute Command| Node1
    Node1 -->|gRPC AppendEntries| Node2
    Node1 -->|gRPC AppendEntries| Node3
    Node2 -->>|Ack| Node1
    Node3 -->>|Ack| Node1
    Node1 -->>|Commit Acknowledged| Client
```

## 1. Consensus Architecture & Roles

Vibe integrates Apache Ratis (version 3.1.2) via `RatisDurabilityAdapter`. A production cluster runs an odd number of nodes (typically 3) sharing a fixed cluster UUID (`0242ac12-0002-4000-8000-000000000001`).

- **Leader**: Accepts client write commands, coordinates log entry replication via gRPC streams, and commits transactions once acknowledged by a majority quorum (2 of 3 nodes).
- **Followers**: Receive `AppendEntries` RPCs, write entries to local Raft logs, and apply committed entries to their local `VibeRatisStateMachine`.
- **Candidate**: A follower that transitions to candidate status upon election timeout expiration and solicits votes from peers.

## 2. Configuration Parameters

| Parameter | Configuration Key | Default Value | Rationale |
| :--- | :--- | :--- | :--- |
| **Election Min Timeout** | `raft.server.rpc.timeout.min` | `200 ms` | Fast failover detection |
| **Election Max Timeout** | `raft.server.rpc.timeout.max` | `400 ms` | Prevents split votes via randomized jitter |
| **Segment Size** | `raft.server.log.segment.size.max` | `32 MiB` | Bounded log segment rotation |
| **Snapshot Threshold** | `raft.server.state.machine.threshold` | `100,000 entries` | Bounds disk log size and recovery duration |
| **gRPC Max Inbound Msg** | `raft.grpc.message.size.max` | `32 MiB` | Supports large attachment descriptors |

## 3. Failure & Failover Invariants

### Leader Crash and Election
1. If the leader node terminates or experiences network isolation, followers miss heartbeat packets within the election timeout window (200-400ms).
2. The follower whose election timer expires first increments its term, transitions to candidate, and broadcasts `RequestVote` RPCs.
3. Upon receiving votes from a majority of peers (1 vote + self = 2/3), the candidate becomes the new leader.
4. Client requests sent to non-leader nodes receive `ErrorCode.DURABILITY_LEADER_STEPDOWN` with the address of the newly elected leader.

### Follower Rejoin and Catch-Up
1. When an offline follower restarts, it initializes `RatisDurabilityAdapter` in recovery mode (`startRecover()`), avoiding storage re-formatting.
2. The leader detects the reconnected follower and streams missing log entries from its local Raft log via `AppendEntries`.
3. If the follower was offline long enough that older log entries were compacted into a snapshot, the leader streams the latest snapshot (`loadSnapshot`) before resuming incremental entry streaming.

## 4. Verification and Diagnostic Commands

### Inspect Cluster Topology
```bash
# Query the built-in HTTP inspector for live cluster status
curl http://localhost:9001/api/v1/cluster
```

### Run Multi-Node Failover Acceptance Test
```bash
# Executes leader kill, election failover, and follower catch-up test
./gradlew :storage-ratis:test --tests "com.vibe.storage.ratis.RatisReplicationInvariantsAcceptanceTest"
```

### Launch Standalone 3-Node Cluster Demo
```bash
make demo
```
