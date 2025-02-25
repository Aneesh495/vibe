package com.vibe.domain.search;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.state.DomainStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationSearchIndexTest {

    private DomainStateMachine stateMachine;
    private ConversationSearchIndex searchIndex;

    @BeforeEach
    void setUp() {
        stateMachine = new DomainStateMachine();
        searchIndex = stateMachine.searchIndex();

        // Register users alice, bob, charlie
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), 1000L, "alice", "device1", "req1", "alice", "hash", "Alice", "", ""));
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), 1001L, "bob", "device2", "req2", "bob", "hash", "Bob", "", ""));
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), 1002L, "charlie", "device3", "req3", "charlie", "hash", "Charlie", "", ""));
    }

    @Test
    void testSearchFindsRelevantMessagesWithAuthorizationFiltering() {
        UUID groupConvId = UUID.randomUUID();
        stateMachine.apply(new CreateConversationCommand(
                UUID.randomUUID(), 1005L, "alice", "device1", "req4",
                groupConvId, ConversationType.GROUP, "Engineering", List.of("alice", "bob")
        ));

        // Alice sends distributed consensus message
        stateMachine.apply(new SendMessageCommand(
                UUID.randomUUID(), 1010L, "alice", "device1", "m1",
                UUID.randomUUID(), groupConvId, "We need to verify Raft distributed consensus and commit log quorum", null
        ));

        // Bob searches for "consensus"
        SearchQuery bobQuery = SearchQuery.of("bob", "consensus");
        SearchResult bobResult = searchIndex.search(bobQuery, stateMachine);

        assertThat(bobResult.matches()).hasSize(1);
        assertThat(bobResult.matches().get(0).senderUserId()).isEqualTo("alice");
        assertThat(bobResult.matches().get(0).contentSnippet()).contains("consensus");

        // Charlie is NOT a member of Engineering conversation, so must get 0 results
        SearchQuery charlieQuery = SearchQuery.of("charlie", "consensus");
        SearchResult charlieResult = searchIndex.search(charlieQuery, stateMachine);

        assertThat(charlieResult.matches()).isEmpty();
    }

    @Test
    void testSearchOmitsBlockedUsers() {
        UUID convId = UUID.randomUUID();
        stateMachine.apply(new CreateConversationCommand(
                UUID.randomUUID(), 1005L, "alice", "device1", "req4",
                convId, ConversationType.GROUP, "General", List.of("alice", "bob")
        ));

        stateMachine.apply(new SendMessageCommand(
                UUID.randomUUID(), 1010L, "alice", "device1", "m1",
                UUID.randomUUID(), convId, "Important announcement regarding deployment schedule", null
        ));

        // Bob blocks Alice
        stateMachine.socialGraph().blockUser("bob", "alice");

        // Bob searches
        SearchResult result = searchIndex.search(SearchQuery.of("bob", "announcement"), stateMachine);
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void testRebuildIndexesHistoricalMessagesAfterSnapshotRestore() {
        UUID convId = UUID.randomUUID();
        stateMachine.apply(new CreateConversationCommand(
                UUID.randomUUID(), 1005L, "alice", "device1", "req4",
                convId, ConversationType.GROUP, "Tech", List.of("alice", "bob")
        ));

        stateMachine.apply(new SendMessageCommand(
                UUID.randomUUID(), 1010L, "alice", "device1", "m1",
                UUID.randomUUID(), convId, "Database index optimization and query execution plans", null
        ));

        // Create snapshot and restore into new machine
        var snapshot = stateMachine.createSnapshot();
        DomainStateMachine restored = new DomainStateMachine();
        restored.restoreFromSnapshot(snapshot);

        SearchResult result = restored.searchIndex().search(SearchQuery.of("bob", "optimization"), restored);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).contentSnippet()).contains("optimization");
    }
}
