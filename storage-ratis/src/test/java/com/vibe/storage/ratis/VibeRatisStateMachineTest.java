package com.vibe.storage.ratis;

import com.vibe.domain.command.DomainCommandCodec;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.entity.User;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.CommandExecutionResultCodec;
import com.vibe.domain.state.DomainStateMachine;
import org.apache.ratis.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.proto.RaftProtos.StateMachineLogEntryProto;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class VibeRatisStateMachineTest {

    @Test
    void testApplyTransactionAndDecodeResult(@TempDir Path tempDir) throws Exception {
        DomainStateMachine domainStateMachine = new DomainStateMachine();
        VibeRatisStateMachine ratisStateMachine = new VibeRatisStateMachine(domainStateMachine);

        UUID commandId = UUID.randomUUID();
        String userId = "user-123";
        RegisterUserCommand cmd = new RegisterUserCommand(
                commandId,
                System.currentTimeMillis(),
                userId,
                "dev-1",
                "client-msg-1",
                "alice",
                "$2a$10$hashedpasswordplaceholder",
                "Alice",
                "Bio",
                "avatar.png"
        );

        ByteBuffer encodedCmd = DomainCommandCodec.encode(cmd);
        ByteString byteString = ByteString.copyFrom(encodedCmd);

        LogEntryProto logEntry = LogEntryProto.newBuilder()
                .setTerm(1)
                .setIndex(42)
                .setStateMachineLogEntry(
                        StateMachineLogEntryProto.newBuilder()
                                .setLogData(byteString)
                                .build()
                )
                .build();

        TransactionContext trx = TransactionContext.newBuilder()
                .setStateMachine(ratisStateMachine)
                .setLogEntry(logEntry)
                .build();

        CompletableFuture<Message> future = ratisStateMachine.applyTransaction(trx);
        Message replyMessage = future.get();

        byte[] replyBytes = replyMessage.getContent().toByteArray();
        CommandExecutionResult result = CommandExecutionResultCodec.decode(ByteBuffer.wrap(replyBytes));

        assertThat(result.isSuccess()).isTrue();
        assertThat(domainStateMachine.lastAppliedIndex()).isEqualTo(42L);

        User registered = domainStateMachine.getUserById(userId);
        assertThat(registered).isNotNull();
        assertThat(registered.username()).isEqualTo("alice");
    }
}
