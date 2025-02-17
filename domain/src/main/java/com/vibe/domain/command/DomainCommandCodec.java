package com.vibe.domain.command;

import com.vibe.protocol.payload.CommandType;

import java.nio.ByteBuffer;

/**
 * Universal codec for serializing and deserializing domain commands.
 */
public final class DomainCommandCodec {

    private DomainCommandCodec() {
    }

    public static ByteBuffer encode(DomainCommand command) {
        return command.encode();
    }

    public static DomainCommand decode(ByteBuffer src) {
        short code = src.getShort();
        CommandType type = CommandType.fromCode(code);

        if (code == (short) 0x0036) {
            return AcknowledgeReceiptCommand.decode(src);
        }

        if (type == null) {
            throw new IllegalArgumentException("Unknown command code: " + code);
        }

        return switch (type) {
            case REGISTER_USER -> RegisterUserCommand.decode(src);
            case UPDATE_PROFILE -> UpdateProfileCommand.decode(src);
            case FRIEND_USER -> FriendUserCommand.decode(src);
            case UNFRIEND_USER -> UnfriendUserCommand.decode(src);
            case BLOCK_USER -> BlockUserCommand.decode(src);
            case UNBLOCK_USER -> UnblockUserCommand.decode(src);
            case CREATE_CONVERSATION -> CreateConversationCommand.decode(src);
            case ADD_MEMBER -> AddMemberCommand.decode(src);
            case REMOVE_MEMBER -> RemoveMemberCommand.decode(src);
            case SEND_MESSAGE -> SendMessageCommand.decode(src);
            case EDIT_MESSAGE -> EditMessageCommand.decode(src);
            case DELETE_MESSAGE -> DeleteMessageCommand.decode(src);
            case ADD_REACTION -> AddReactionCommand.decode(src);
            case REMOVE_REACTION -> RemoveReactionCommand.decode(src);
            default -> throw new UnsupportedOperationException("Unsupported command type: " + type);
        };
    }
}
