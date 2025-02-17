package com.vibe.sdk;

import com.vibe.domain.entity.ConversationType;
import com.vibe.protocol.payload.CommandType;
import com.vibe.sdk.storage.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteClientStorageTest {

    private SqliteClientStorage storage;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        storage = new SqliteClientStorage(tempDir, "test-client");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void testConversationAndMessageStorage() throws Exception {
        UUID convId = UUID.randomUUID();
        StoredConversation conv = new StoredConversation(
                convId, ConversationType.DIRECT, "Alice & Bob",
                System.currentTimeMillis(), 0L, 0
        );
        storage.saveConversation(conv);

        Optional<StoredConversation> loadedConv = storage.getConversation(convId);
        assertThat(loadedConv).isPresent();
        assertThat(loadedConv.get().title()).isEqualTo("Alice & Bob");

        // Save messages
        UUID msg1 = UUID.randomUUID();
        UUID msg2 = UUID.randomUUID();
        storage.saveMessage(new StoredMessage(
                msg1, convId, "u-alice", 1L, "Hello Bob!",
                System.currentTimeMillis(), MessageDeliveryStatus.COMMITTED,
                null, null, 0
        ));
        storage.saveMessage(new StoredMessage(
                msg2, convId, "u-bob", 2L, "Hey Alice!",
                System.currentTimeMillis(), MessageDeliveryStatus.COMMITTED,
                null, null, 0
        ));

        List<StoredMessage> messages = storage.getMessages(convId, 10, 0);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).seqNumber()).isEqualTo(2L);
        assertThat(messages.get(1).seqNumber()).isEqualTo(1L);

        // Update status
        storage.updateMessageStatus(msg1, MessageDeliveryStatus.READ);
        List<StoredMessage> updated = storage.getMessages(convId, 10, 0);
        assertThat(updated.stream().filter(m -> m.messageId().equals(msg1)).findFirst().get().status())
                .isEqualTo(MessageDeliveryStatus.READ);
    }

    @Test
    void testOfflineOutboxQueueingAndRemoval() throws Exception {
        UUID convId = UUID.randomUUID();
        OutboxItem item1 = new OutboxItem(
                "out-1", CommandType.SEND_MESSAGE, convId,
                new byte[]{1, 2, 3}, System.currentTimeMillis(), 0
        );
        OutboxItem item2 = new OutboxItem(
                "out-2", CommandType.SEND_MESSAGE, convId,
                new byte[]{4, 5, 6}, System.currentTimeMillis() + 10, 0
        );

        storage.enqueueOutbox(item1);
        storage.enqueueOutbox(item2);

        List<OutboxItem> pending = storage.getPendingOutbox();
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).clientMessageId()).isEqualTo("out-1");

        storage.removeOutbox("out-1");
        List<OutboxItem> remaining = storage.getPendingOutbox();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).clientMessageId()).isEqualTo("out-2");
    }

    @Test
    void testContactsAndSyncState() throws Exception {
        StoredContact contact = new StoredContact(
                "u-charlie", "charlie", "Charlie Brown",
                "Peanuts", "avatar.jpg", true, false
        );
        storage.saveContact(contact);

        List<StoredContact> contacts = storage.getAllContacts();
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).displayName()).isEqualTo("Charlie Brown");

        storage.setSyncState("last_sync", "1720000000");
        assertThat(storage.getSyncState("last_sync")).contains("1720000000");
    }
}
