package com.vibe.domain.state;

import com.vibe.domain.command.*;
import com.vibe.domain.entity.*;
import com.vibe.domain.event.*;
import com.vibe.domain.idempotency.IdempotencyKey;
import com.vibe.domain.idempotency.IdempotencyRecord;
import com.vibe.domain.idempotency.IdempotencyTracker;
import com.vibe.protocol.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic domain state machine.
 *
 * <p>Applied sequentially per authoritative log commit. Assigns strictly monotonic
 * per-conversation sequence numbers, enforces authorization and block rules,
 * maintains idempotency deduplication, and emits domain events for delivery fanout.
 */
public final class DomainStateMachine {

    private static final Logger log = LoggerFactory.getLogger(DomainStateMachine.class);

    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Message>> conversationMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Message> messagesById = new ConcurrentHashMap<>();

    private final SocialGraph socialGraph = new SocialGraph();
    private final IdempotencyTracker idempotencyTracker = new IdempotencyTracker();
    private final com.vibe.domain.search.ConversationSearchIndex searchIndex = new com.vibe.domain.search.ConversationSearchIndex();

    private final AtomicLong lastAppliedIndex = new AtomicLong(0L);

    public DomainStateMachine() {
    }

    public com.vibe.domain.search.ConversationSearchIndex searchIndex() {
        return searchIndex;
    }

    public long lastAppliedIndex() {
        return lastAppliedIndex.get();
    }

    public void setLastAppliedIndex(long index) {
        lastAppliedIndex.set(index);
    }

    /**
     * Applies a committed command to the state machine in authoritative log order.
     *
     * @param command committed domain command
     * @return CommandExecutionResult containing assigned sequence and emitted event
     */
    public synchronized CommandExecutionResult apply(DomainCommand command) {
        Objects.requireNonNull(command, "DomainCommand must not be null");

        // 1. Check idempotency deduplication cache
        IdempotencyKey key = new IdempotencyKey(
                command.callerUserId(), command.callerDeviceId(), command.clientMessageId()
        );
        IdempotencyTracker.CheckResult check = idempotencyTracker.check(key, command.contentHash());
        if (check.status() == IdempotencyTracker.Status.IDEMPOTENT_REPLAY) {
            IdempotencyRecord rec = check.record();
            log.debug("Idempotent replay detected for key: {}", key);
            return CommandExecutionResult.success(rec.assignedSeq(), rec.resultPayload(), null);
        } else if (check.status() == IdempotencyTracker.Status.CONFLICT) {
            log.warn("Idempotency conflict detected for key: {}", key);
            return CommandExecutionResult.failure(
                    ErrorCode.DOMAIN_IDEMPOTENCY_CONFLICT,
                    "ClientMessageId already used with different content"
            );
        }

        // 2. Dispatch command to deterministic mutation handler
        CommandExecutionResult result = dispatch(command);

        // 3. Cache successful results in idempotency tracker
        if (result.isSuccess() && key.isIdempotencyEnabled()) {
            IdempotencyRecord record = new IdempotencyRecord(
                    key,
                    result.assignedSeq(),
                    result.event() != null ? result.event().eventId() : UUID.randomUUID(),
                    command.contentHash(),
                    result.payload(),
                    System.currentTimeMillis()
            );
            idempotencyTracker.record(record);
        }

        return result;
    }

    private CommandExecutionResult dispatch(DomainCommand command) {
        return switch (command.type()) {
            case REGISTER_USER -> handleRegisterUser((RegisterUserCommand) command);
            case UPDATE_PROFILE -> handleUpdateProfile((UpdateProfileCommand) command);
            case FRIEND_USER -> handleFriendUser((FriendUserCommand) command);
            case UNFRIEND_USER -> handleUnfriendUser((UnfriendUserCommand) command);
            case BLOCK_USER -> handleBlockUser((BlockUserCommand) command);
            case UNBLOCK_USER -> handleUnblockUser((UnblockUserCommand) command);
            case CREATE_CONVERSATION -> handleCreateConversation((CreateConversationCommand) command);
            case ADD_MEMBER -> handleAddMember((AddMemberCommand) command);
            case REMOVE_MEMBER -> handleRemoveMember((RemoveMemberCommand) command);
            case SEND_MESSAGE -> handleSendMessage((SendMessageCommand) command);
            case EDIT_MESSAGE -> handleEditMessage((EditMessageCommand) command);
            case DELETE_MESSAGE -> handleDeleteMessage((DeleteMessageCommand) command);
            case ADD_REACTION -> handleAddReaction((AddReactionCommand) command);
            case REMOVE_REACTION -> handleRemoveReaction((RemoveReactionCommand) command);
            default -> {
                if (command instanceof AcknowledgeReceiptCommand ackCmd) {
                    yield handleAcknowledgeReceipt(ackCmd);
                }
                yield CommandExecutionResult.failure(ErrorCode.DOMAIN_INVALID_INPUT, "Unknown command");
            }
        };
    }

    private CommandExecutionResult handleRegisterUser(RegisterUserCommand cmd) {
        if (usersByUsername.containsKey(cmd.username().toLowerCase())) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_ALREADY_EXISTS, "Username already taken");
        }

        User user = new User(
                cmd.callerUserId(),
                cmd.username(),
                cmd.passwordHash(),
                cmd.displayName(),
                cmd.bio(),
                cmd.avatarUrl(),
                cmd.timestamp(),
                UserStatus.OFFLINE,
                cmd.timestamp()
        );

        usersByUsername.put(user.username().toLowerCase(), user);
        usersById.put(user.userId(), user);

        byte[] payload = user.userId().getBytes(StandardCharsets.UTF_8);
        return CommandExecutionResult.success(-1L, payload);
    }

    private CommandExecutionResult handleUpdateProfile(UpdateProfileCommand cmd) {
        User user = usersById.get(cmd.callerUserId());
        if (user == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_NOT_FOUND, "User not found");
        }

        if (!cmd.displayName().isEmpty()) {
            user.setDisplayName(cmd.displayName());
        }
        if (!cmd.bio().isEmpty()) {
            user.setBio(cmd.bio());
        }
        if (!cmd.avatarUrl().isEmpty()) {
            user.setAvatarUrl(cmd.avatarUrl());
        }

        return CommandExecutionResult.success(-1L, "OK".getBytes(StandardCharsets.UTF_8));
    }

    private CommandExecutionResult handleFriendUser(FriendUserCommand cmd) {
        User caller = usersById.get(cmd.callerUserId());
        User target = usersById.get(cmd.targetUserId());
        if (caller == null || target == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_NOT_FOUND, "One or both users not found");
        }

        if (socialGraph.isBlockedEitherWay(cmd.callerUserId(), cmd.targetUserId())) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_BLOCKED, "Cannot friend a blocked user");
        }

        socialGraph.addFriend(cmd.callerUserId(), cmd.targetUserId());
        return CommandExecutionResult.success(-1L, "OK".getBytes(StandardCharsets.UTF_8));
    }

    private CommandExecutionResult handleUnfriendUser(UnfriendUserCommand cmd) {
        socialGraph.removeFriend(cmd.callerUserId(), cmd.targetUserId());
        return CommandExecutionResult.success(-1L, "OK".getBytes(StandardCharsets.UTF_8));
    }

    private CommandExecutionResult handleBlockUser(BlockUserCommand cmd) {
        User target = usersById.get(cmd.targetUserId());
        if (target == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_NOT_FOUND, "Target user not found");
        }

        socialGraph.blockUser(cmd.callerUserId(), cmd.targetUserId());
        return CommandExecutionResult.success(-1L, "OK".getBytes(StandardCharsets.UTF_8));
    }

    private CommandExecutionResult handleUnblockUser(UnblockUserCommand cmd) {
        socialGraph.unblockUser(cmd.callerUserId(), cmd.targetUserId());
        return CommandExecutionResult.success(-1L, "OK".getBytes(StandardCharsets.UTF_8));
    }

    private CommandExecutionResult handleCreateConversation(CreateConversationCommand cmd) {
        if (cmd.convType() == ConversationType.DIRECT) {
            if (cmd.memberUserIds().size() != 2) {
                return CommandExecutionResult.failure(ErrorCode.DOMAIN_INVALID_INPUT, "Direct chat requires exactly 2 members");
            }
            String u1 = cmd.memberUserIds().get(0);
            String u2 = cmd.memberUserIds().get(1);

            if (socialGraph.isBlockedEitherWay(u1, u2)) {
                return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_BLOCKED, "Cannot start chat with blocked user");
            }

            // Check if direct conversation already exists between these users
            for (Conversation existing : conversations.values()) {
                if (existing.type() == ConversationType.DIRECT
                        && existing.isMember(u1) && existing.isMember(u2)) {
                    byte[] payload = existing.conversationId().toString().getBytes(StandardCharsets.UTF_8);
                    return CommandExecutionResult.success(-1L, payload);
                }
            }
        }

        Conversation conv = new Conversation(
                cmd.conversationId(),
                cmd.convType(),
                cmd.title(),
                cmd.timestamp(),
                cmd.callerUserId(),
                0L
        );

        if (cmd.convType() == ConversationType.DIRECT) {
            for (String memberId : cmd.memberUserIds()) {
                conv.addMember(ConversationMember.of(memberId, MemberRole.MEMBER));
            }
        } else {
            conv.addMember(ConversationMember.of(cmd.callerUserId(), MemberRole.OWNER));
            for (String memberId : cmd.memberUserIds()) {
                if (!memberId.equals(cmd.callerUserId())) {
                    conv.addMember(ConversationMember.of(memberId, MemberRole.MEMBER));
                }
            }
        }

        conversations.put(conv.conversationId(), conv);
        byte[] payload = conv.conversationId().toString().getBytes(StandardCharsets.UTF_8);
        return CommandExecutionResult.success(-1L, payload);
    }

    private CommandExecutionResult handleAddMember(AddMemberCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND, "Conversation not found");
        }

        if (conv.type() == ConversationType.DIRECT) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_INVALID_INPUT, "Cannot add members to direct chat");
        }

        ConversationMember caller = conv.getMember(cmd.callerUserId());
        if (caller == null || caller.role() == MemberRole.MEMBER) {
            return CommandExecutionResult.failure(ErrorCode.AUTH_FORBIDDEN, "Only OWNER or ADMIN may add members");
        }

        if (socialGraph.isBlockedEitherWay(cmd.callerUserId(), cmd.targetUserId())) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_BLOCKED, "Cannot add blocked user");
        }

        conv.addMember(ConversationMember.of(cmd.targetUserId(), cmd.role()));
        long newSeq = conv.nextSeq();

        MemberJoinedEvent event = new MemberJoinedEvent(
                UUID.randomUUID(), conv.conversationId(), newSeq, cmd.callerUserId(),
                cmd.timestamp(), cmd.targetUserId(), cmd.role()
        );

        return CommandExecutionResult.success(newSeq, "OK".getBytes(StandardCharsets.UTF_8), event);
    }

    private CommandExecutionResult handleRemoveMember(RemoveMemberCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND, "Conversation not found");
        }

        ConversationMember caller = conv.getMember(cmd.callerUserId());
        if (caller == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_NOT_A_MEMBER, "Caller is not a member");
        }

        boolean isSelf = cmd.callerUserId().equals(cmd.targetUserId());
        if (!isSelf && !caller.role().canManage(conv.getMember(cmd.targetUserId()).role())) {
            return CommandExecutionResult.failure(ErrorCode.AUTH_FORBIDDEN, "Insufficient privileges to remove member");
        }

        conv.removeMember(cmd.targetUserId());
        long newSeq = conv.nextSeq();
        return CommandExecutionResult.success(newSeq, "OK".getBytes(StandardCharsets.UTF_8));
    }

    private CommandExecutionResult handleSendMessage(SendMessageCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND, "Conversation not found");
        }

        if (!conv.isMember(cmd.callerUserId())) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_NOT_A_MEMBER, "Caller is not a member of conversation");
        }

        if (conv.type() == ConversationType.DIRECT) {
            for (String memberId : conv.members().keySet()) {
                if (!memberId.equals(cmd.callerUserId()) && socialGraph.isBlockedEitherWay(cmd.callerUserId(), memberId)) {
                    return CommandExecutionResult.failure(ErrorCode.DOMAIN_USER_BLOCKED, "Communication blocked");
                }
            }
        }

        // Strictly monotonic sequence assignment within this conversation lane
        long assignedSeq = conv.nextSeq();

        Message message = new Message(
                cmd.messageId(),
                cmd.conversationId(),
                assignedSeq,
                cmd.callerUserId(),
                cmd.clientMessageId(),
                cmd.content(),
                cmd.timestamp(),
                0L,
                false,
                cmd.attachment(),
                cmd.replyToMessageId()
        );

        conversationMessages.computeIfAbsent(conv.conversationId(), k -> new ConcurrentHashMap<>())
                .put(assignedSeq, message);
        messagesById.put(message.messageId(), message);
        searchIndex.indexMessage(message);

        MessageSentEvent event = new MessageSentEvent(
                UUID.randomUUID(), conv.conversationId(), assignedSeq, cmd.callerUserId(),
                cmd.timestamp(), message.messageId(), cmd.clientMessageId(), cmd.content(), cmd.attachment(),
                cmd.replyToMessageId()
        );

        byte[] payload = message.messageId().toString().getBytes(StandardCharsets.UTF_8);
        return CommandExecutionResult.success(assignedSeq, payload, event);
    }

    private CommandExecutionResult handleEditMessage(EditMessageCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND, "Conversation not found");
        }

        Message message = messagesById.get(cmd.messageId());
        if (message == null || !message.conversationId().equals(cmd.conversationId())) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_MESSAGE_NOT_FOUND, "Message not found");
        }

        if (!message.senderUserId().equals(cmd.callerUserId())) {
            return CommandExecutionResult.failure(ErrorCode.AUTH_FORBIDDEN, "Only message author may edit");
        }

        long assignedSeq = conv.nextSeq();
        message.edit(cmd.newContent(), cmd.timestamp());
        searchIndex.updateMessage(message.messageId(), cmd.newContent(), cmd.timestamp());

        MessageEditedEvent event = new MessageEditedEvent(
                UUID.randomUUID(), conv.conversationId(), assignedSeq, cmd.callerUserId(),
                cmd.timestamp(), message.messageId(), cmd.newContent()
        );

        return CommandExecutionResult.success(assignedSeq, "OK".getBytes(StandardCharsets.UTF_8), event);
    }

    private CommandExecutionResult handleDeleteMessage(DeleteMessageCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND, "Conversation not found");
        }

        Message message = messagesById.get(cmd.messageId());
        if (message == null || !message.conversationId().equals(cmd.conversationId())) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_MESSAGE_NOT_FOUND, "Message not found");
        }

        ConversationMember caller = conv.getMember(cmd.callerUserId());
        boolean isAuthor = message.senderUserId().equals(cmd.callerUserId());
        boolean isOwner = caller != null && caller.role() == MemberRole.OWNER;

        if (!isAuthor && !isOwner) {
            return CommandExecutionResult.failure(ErrorCode.AUTH_FORBIDDEN, "Insufficient permissions to delete message");
        }

        long assignedSeq = conv.nextSeq();
        message.delete(cmd.timestamp());
        searchIndex.removeMessage(message.messageId());

        MessageDeletedEvent event = new MessageDeletedEvent(
                UUID.randomUUID(), conv.conversationId(), assignedSeq, cmd.callerUserId(),
                cmd.timestamp(), message.messageId()
        );

        return CommandExecutionResult.success(assignedSeq, "OK".getBytes(StandardCharsets.UTF_8), event);
    }

    private CommandExecutionResult handleAddReaction(AddReactionCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null || !conv.isMember(cmd.callerUserId())) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_NOT_A_MEMBER, "Not a conversation member");
        }

        Message message = messagesById.get(cmd.messageId());
        if (message == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_MESSAGE_NOT_FOUND, "Message not found");
        }

        long assignedSeq = conv.nextSeq();
        message.addReaction(cmd.emoji(), cmd.callerUserId());

        ReactionAddedEvent event = new ReactionAddedEvent(
                UUID.randomUUID(), conv.conversationId(), assignedSeq, cmd.callerUserId(),
                cmd.timestamp(), message.messageId(), cmd.emoji()
        );

        return CommandExecutionResult.success(assignedSeq, "OK".getBytes(StandardCharsets.UTF_8), event);
    }

    private CommandExecutionResult handleRemoveReaction(RemoveReactionCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND, "Conversation not found");
        }

        Message message = messagesById.get(cmd.messageId());
        if (message == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_MESSAGE_NOT_FOUND, "Message not found");
        }

        long assignedSeq = conv.nextSeq();
        message.removeReaction(cmd.emoji(), cmd.callerUserId());

        return CommandExecutionResult.success(assignedSeq, "OK".getBytes(StandardCharsets.UTF_8));
    }

    private CommandExecutionResult handleAcknowledgeReceipt(AcknowledgeReceiptCommand cmd) {
        Conversation conv = conversations.get(cmd.conversationId());
        if (conv == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND, "Conversation not found");
        }

        ConversationMember member = conv.getMember(cmd.callerUserId());
        if (member == null) {
            return CommandExecutionResult.failure(ErrorCode.DOMAIN_NOT_A_MEMBER, "Caller is not a member");
        }

        if (cmd.ackType() == 1) { // DELIVERED
            member.setLastDeliveredSeq(cmd.upToSeq());
        } else if (cmd.ackType() == 2) { // READ
            member.setLastReadSeq(cmd.upToSeq());
        }

        long assignedSeq = conv.nextSeq();
        ReceiptAcknowledgedEvent event = new ReceiptAcknowledgedEvent(
                UUID.randomUUID(), conv.conversationId(), assignedSeq, cmd.callerUserId(),
                cmd.timestamp(), cmd.ackType(), cmd.upToSeq()
        );

        return CommandExecutionResult.success(assignedSeq, "OK".getBytes(StandardCharsets.UTF_8), event);
    }

    // Read queries

    public User getUserById(String userId) {
        return usersById.get(userId);
    }

    public User getUserByUsername(String username) {
        if (username == null) {
            return null;
        }
        return usersByUsername.get(username.toLowerCase());
    }

    public Conversation getConversation(UUID convId) {
        return conversations.get(convId);
    }

    public List<Conversation> listConversationsForUser(String userId) {
        List<Conversation> result = new ArrayList<>();
        for (Conversation c : conversations.values()) {
            if (c.isMember(userId)) {
                result.add(c);
            }
        }
        return result;
    }

    public List<Message> getMessages(UUID convId, long fromSeq, int limit) {
        Map<Long, Message> msgs = conversationMessages.get(convId);
        if (msgs == null || msgs.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> result = new ArrayList<>();
        long current = fromSeq;
        while (result.size() < limit) {
            Message m = msgs.get(current);
            if (m != null) {
                result.add(m);
            } else if (current > 100_000_000) {
                break;
            }
            current++;
            if (current > 100_000_000 || (!msgs.containsKey(current) && current > msgs.keySet().stream().max(Long::compare).orElse(0L))) {
                break;
            }
        }
        return result;
    }

    public SocialGraph socialGraph() {
        return socialGraph;
    }

    public IdempotencyTracker idempotencyTracker() {
        return idempotencyTracker;
    }

    public SnapshotCodec.DomainSnapshot createSnapshot() {
        List<User.UserSnapshot> userSnapshots = new ArrayList<>();
        allUsers().values().forEach(u -> userSnapshots.add(u.toSnapshot()));

        List<Conversation.ConversationSnapshot> convSnapshots = new ArrayList<>();
        allConversations().values().forEach(c -> convSnapshots.add(c.toSnapshot()));

        List<Message.MessageSnapshot> messageSnapshots = new ArrayList<>();
        for (Conversation c : allConversations().values()) {
            List<Message> msgs = getMessages(c.conversationId(), 1L, 100_000);
            msgs.forEach(m -> messageSnapshots.add(m.toSnapshot()));
        }

        return new SnapshotCodec.DomainSnapshot(
                lastAppliedIndex(),
                userSnapshots,
                convSnapshots,
                messageSnapshots,
                socialGraph().toSnapshot(),
                new ArrayList<>(idempotencyTracker().allRecords().values())
        );
    }

    public synchronized void restoreFromSnapshot(SnapshotCodec.DomainSnapshot snapshot) {
        restore(snapshot);
    }

    public synchronized void restore(SnapshotCodec.DomainSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        usersByUsername.clear();
        usersById.clear();
        conversations.clear();
        conversationMessages.clear();
        messagesById.clear();

        if (snapshot.users() != null) {
            for (User.UserSnapshot u : snapshot.users()) {
                User user = new User(
                        u.userId(), u.username(), u.passwordHash(), u.displayName(),
                        u.bio(), u.avatarUrl(), u.createdAt(), u.status(), u.lastSeenAt()
                );
                usersByUsername.put(user.username().toLowerCase(), user);
                usersById.put(user.userId(), user);
            }
        }

        if (snapshot.conversations() != null) {
            for (Conversation.ConversationSnapshot c : snapshot.conversations()) {
                Conversation conv = new Conversation(
                        c.conversationId(), c.type(), c.name(), c.createdAt(), c.creatorUserId(), c.currentSeq()
                );
                if (c.members() != null) {
                    for (Conversation.MemberSnapshot m : c.members()) {
                        conv.addMember(new ConversationMember(
                                m.userId(), m.role(), m.joinedAt(), m.lastDeliveredSeq(), m.lastReadSeq()
                        ));
                    }
                }
                conversations.put(conv.conversationId(), conv);
            }
        }

        if (snapshot.messages() != null) {
            for (Message.MessageSnapshot m : snapshot.messages()) {
                Message msg = new Message(
                        m.messageId(), m.conversationId(), m.seq(), m.senderUserId(),
                        m.clientMessageId(), m.content(), m.sentAt(), m.editedAt(), m.deleted(), m.attachment()
                );
                if (m.reactions() != null) {
                    m.reactions().forEach((emoji, userList) -> {
                        for (String uid : userList) {
                            msg.addReaction(emoji, uid);
                        }
                    });
                }
                conversationMessages.computeIfAbsent(msg.conversationId(), k -> new ConcurrentHashMap<>())
                        .put(msg.seq(), msg);
                messagesById.put(msg.messageId(), msg);
            }
        }

        if (snapshot.socialGraph() != null) {
            socialGraph.restore(snapshot.socialGraph());
        }

        if (snapshot.idempotencyRecords() != null) {
            idempotencyTracker.restore(snapshot.idempotencyRecords());
        }

        setLastAppliedIndex(snapshot.lastAppliedIndex());
        searchIndex.rebuild(this);
        log.info("Restored DomainStateMachine from snapshot at index {}", snapshot.lastAppliedIndex());
    }

    public Map<String, User> allUsers() {
        return Collections.unmodifiableMap(usersById);
    }

    public Map<UUID, Conversation> allConversations() {
        return Collections.unmodifiableMap(conversations);
    }
}
