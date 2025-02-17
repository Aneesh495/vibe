package com.vibe.domain.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vibe.domain.entity.Conversation;
import com.vibe.domain.entity.Message;
import com.vibe.domain.entity.SocialGraph;
import com.vibe.domain.entity.User;
import com.vibe.domain.idempotency.IdempotencyRecord;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes and deserializes deterministic domain snapshots.
 */
public final class SnapshotCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SnapshotCodec() {
    }

    public record DomainSnapshot(
            long lastAppliedIndex,
            List<User.UserSnapshot> users,
            List<Conversation.ConversationSnapshot> conversations,
            List<Message.MessageSnapshot> messages,
            SocialGraph.SocialGraphSnapshot socialGraph,
            List<IdempotencyRecord> idempotencyRecords
    ) {
    }

    public static void writeSnapshot(DomainStateMachine stateMachine, OutputStream out) throws Exception {
        List<User.UserSnapshot> userSnapshots = new ArrayList<>();
        stateMachine.allUsers().values().forEach(u -> userSnapshots.add(u.toSnapshot()));

        List<Conversation.ConversationSnapshot> convSnapshots = new ArrayList<>();
        stateMachine.allConversations().values().forEach(c -> convSnapshots.add(c.toSnapshot()));

        List<Message.MessageSnapshot> messageSnapshots = new ArrayList<>();
        for (Conversation c : stateMachine.allConversations().values()) {
            List<Message> msgs = stateMachine.getMessages(c.conversationId(), 1L, 100_000);
            msgs.forEach(m -> messageSnapshots.add(m.toSnapshot()));
        }

        DomainSnapshot snapshot = new DomainSnapshot(
                stateMachine.lastAppliedIndex(),
                userSnapshots,
                convSnapshots,
                messageSnapshots,
                stateMachine.socialGraph().toSnapshot(),
                new ArrayList<>(stateMachine.idempotencyTracker().allRecords().values())
        );

        MAPPER.writeValue(out, snapshot);
    }

    public static byte[] serialize(DomainStateMachine stateMachine) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        writeSnapshot(stateMachine, baos);
        return baos.toByteArray();
    }

    public static DomainSnapshot readSnapshot(InputStream in) throws Exception {
        return MAPPER.readValue(in, DomainSnapshot.class);
    }

    public static DomainSnapshot deserialize(byte[] data) throws Exception {
        return readSnapshot(new java.io.ByteArrayInputStream(data));
    }
}
