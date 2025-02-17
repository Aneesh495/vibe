package com.vibe.storage.local;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.entity.Message;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDurabilityAdapterTest {

    @Test
    void testEndToEndDurabilitySnapshotAndReplay(@TempDir Path tempDir) throws Exception {
        DomainStateMachine sm1 = new DomainStateMachine();
        LocalDurabilityAdapter adapter1 = new LocalDurabilityAdapter(tempDir, sm1, 1024L, 1L, 10);
        adapter1.start();

        // 1. Register Alice and Bob
        RegisterUserCommand regAlice = new RegisterUserCommand(
                UUID.randomUUID(), 1000L, "u-alice", "d1", "c1",
                "alice", "hashA", "Alice", "Bio A", "a.png"
        );
        RegisterUserCommand regBob = new RegisterUserCommand(
                UUID.randomUUID(), 1001L, "u-bob", "d1", "c2",
                "bob", "hashB", "Bob", "Bio B", "b.png"
        );
        adapter1.executeCommand(regAlice, true).get(5, TimeUnit.SECONDS);
        adapter1.executeCommand(regBob, true).get(5, TimeUnit.SECONDS);

        // 2. Create conversation
        UUID convId = UUID.randomUUID();
        CreateConversationCommand createConv = new CreateConversationCommand(
                UUID.randomUUID(), 1002L, "u-alice", "d1", "c3",
                convId, ConversationType.DIRECT, "Direct", List.of("u-alice", "u-bob")
        );
        adapter1.executeCommand(createConv, true).get(5, TimeUnit.SECONDS);

        // 3. Send message 1
        SendMessageCommand msg1 = new SendMessageCommand(
                UUID.randomUUID(), 1003L, "u-alice", "d1", "c4",
                UUID.randomUUID(), convId, "Hello Bob", null
        );
        CommandExecutionResult res1 = adapter1.executeCommand(msg1, true).get(5, TimeUnit.SECONDS);
        assertThat(res1.assignedSeq()).isEqualTo(1L);

        // 4. Take Snapshot at this point
        Path snapPath = adapter1.takeSnapshot();
        assertThat(snapPath).exists();

        // 5. Send message 2 after snapshot (stored only in WAL uncheckpointed)
        SendMessageCommand msg2 = new SendMessageCommand(
                UUID.randomUUID(), 1004L, "u-bob", "d1", "c5",
                UUID.randomUUID(), convId, "Hello Alice, received!", null
        );
        CommandExecutionResult res2 = adapter1.executeCommand(msg2, true).get(5, TimeUnit.SECONDS);
        assertThat(res2.assignedSeq()).isEqualTo(2L);

        // 6. Graceful shutdown
        adapter1.close();

        // 7. Restart new server instance against same data directory
        DomainStateMachine sm2 = new DomainStateMachine();
        LocalDurabilityAdapter adapter2 = new LocalDurabilityAdapter(tempDir, sm2, 1024L, 1L, 10);
        adapter2.start();

        // 8. Verify restored state
        assertThat(sm2.getUserById("u-alice")).isNotNull();
        assertThat(sm2.getUserById("u-bob")).isNotNull();
        assertThat(sm2.getConversation(convId)).isNotNull();

        List<Message> msgs = sm2.getMessages(convId, 1L, 10);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).content()).isEqualTo("Hello Bob");
        assertThat(msgs.get(0).seq()).isEqualTo(1L);
        assertThat(msgs.get(1).content()).isEqualTo("Hello Alice, received!");
        assertThat(msgs.get(1).seq()).isEqualTo(2L);

        adapter2.close();
    }
}
