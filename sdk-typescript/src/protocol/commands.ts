import { BufferUtil } from './payloads.js';
import { CommandType, ConversationType } from './types.js';

export interface RegisterUserArgs {
  commandId: string;
  timestamp: bigint;
  callerUserId: string;
  callerDeviceId: string;
  clientMessageId: string;
  username: string;
  passwordHash: string;
  displayName: string;
  bio: string;
  avatarUrl: string;
}

export function encodeRegisterUserCommand(args: RegisterUserArgs): Uint8Array {
  const size = 2 + 16 + 8
    + BufferUtil.stringByteLength(args.callerUserId)
    + BufferUtil.stringByteLength(args.callerDeviceId)
    + BufferUtil.stringByteLength(args.clientMessageId)
    + BufferUtil.stringByteLength(args.username)
    + BufferUtil.stringByteLength(args.passwordHash)
    + BufferUtil.stringByteLength(args.displayName)
    + BufferUtil.stringByteLength(args.bio)
    + BufferUtil.stringByteLength(args.avatarUrl);

  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint16(offset, CommandType.REGISTER_USER, false);
  offset += 2;
  offset = BufferUtil.writeUUID(view, offset, args.commandId);
  view.setBigInt64(offset, args.timestamp, false);
  offset += 8;
  offset = BufferUtil.writeString(view, offset, args.callerUserId);
  offset = BufferUtil.writeString(view, offset, args.callerDeviceId);
  offset = BufferUtil.writeString(view, offset, args.clientMessageId);
  offset = BufferUtil.writeString(view, offset, args.username);
  offset = BufferUtil.writeString(view, offset, args.passwordHash);
  offset = BufferUtil.writeString(view, offset, args.displayName);
  offset = BufferUtil.writeString(view, offset, args.bio);
  offset = BufferUtil.writeString(view, offset, args.avatarUrl);
  return new Uint8Array(buf);
}

export interface CreateConversationArgs {
  commandId: string;
  timestamp: bigint;
  callerUserId: string;
  callerDeviceId: string;
  clientMessageId: string;
  conversationId: string;
  convType: ConversationType;
  title: string;
  memberUserIds: string[];
}

export function encodeCreateConversationCommand(args: CreateConversationArgs): Uint8Array {
  let size = 2 + 16 + 8
    + BufferUtil.stringByteLength(args.callerUserId)
    + BufferUtil.stringByteLength(args.callerDeviceId)
    + BufferUtil.stringByteLength(args.clientMessageId)
    + 16
    + 1
    + BufferUtil.stringByteLength(args.title)
    + 2;

  for (const m of args.memberUserIds) {
    size += BufferUtil.stringByteLength(m);
  }

  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint16(offset, CommandType.CREATE_CONVERSATION, false);
  offset += 2;
  offset = BufferUtil.writeUUID(view, offset, args.commandId);
  view.setBigInt64(offset, args.timestamp, false);
  offset += 8;
  offset = BufferUtil.writeString(view, offset, args.callerUserId);
  offset = BufferUtil.writeString(view, offset, args.callerDeviceId);
  offset = BufferUtil.writeString(view, offset, args.clientMessageId);
  offset = BufferUtil.writeUUID(view, offset, args.conversationId);
  view.setUint8(offset++, args.convType === ConversationType.DIRECT ? 0x01 : 0x02);
  offset = BufferUtil.writeString(view, offset, args.title);
  view.setUint16(offset, args.memberUserIds.length, false);
  offset += 2;
  for (const m of args.memberUserIds) {
    offset = BufferUtil.writeString(view, offset, m);
  }
  return new Uint8Array(buf);
}

export interface SendMessageArgs {
  commandId: string;
  timestamp: bigint;
  callerUserId: string;
  callerDeviceId: string;
  clientMessageId: string;
  messageId: string;
  conversationId: string;
  content: string;
}

export function encodeSendMessageCommand(args: SendMessageArgs): Uint8Array {
  const size = 2 + 16 + 8
    + BufferUtil.stringByteLength(args.callerUserId)
    + BufferUtil.stringByteLength(args.callerDeviceId)
    + BufferUtil.stringByteLength(args.clientMessageId)
    + 16
    + 16
    + BufferUtil.stringByteLength(args.content)
    + 1; // hasAttachment flag (0)

  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint16(offset, CommandType.SEND_MESSAGE, false);
  offset += 2;
  offset = BufferUtil.writeUUID(view, offset, args.commandId);
  view.setBigInt64(offset, args.timestamp, false);
  offset += 8;
  offset = BufferUtil.writeString(view, offset, args.callerUserId);
  offset = BufferUtil.writeString(view, offset, args.callerDeviceId);
  offset = BufferUtil.writeString(view, offset, args.clientMessageId);
  offset = BufferUtil.writeUUID(view, offset, args.messageId);
  offset = BufferUtil.writeUUID(view, offset, args.conversationId);
  offset = BufferUtil.writeString(view, offset, args.content);
  view.setUint8(offset++, 0); // no attachment
  return new Uint8Array(buf);
}

export interface AddReactionArgs {
  commandId: string;
  timestamp: bigint;
  callerUserId: string;
  callerDeviceId: string;
  clientMessageId: string;
  conversationId: string;
  messageId: string;
  emoji: string;
}

export function encodeAddReactionCommand(args: AddReactionArgs): Uint8Array {
  const size = 2 + 16 + 8
    + BufferUtil.stringByteLength(args.callerUserId)
    + BufferUtil.stringByteLength(args.callerDeviceId)
    + BufferUtil.stringByteLength(args.clientMessageId)
    + 16
    + 16
    + BufferUtil.stringByteLength(args.emoji);

  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint16(offset, CommandType.ADD_REACTION, false);
  offset += 2;
  offset = BufferUtil.writeUUID(view, offset, args.commandId);
  view.setBigInt64(offset, args.timestamp, false);
  offset += 8;
  offset = BufferUtil.writeString(view, offset, args.callerUserId);
  offset = BufferUtil.writeString(view, offset, args.callerDeviceId);
  offset = BufferUtil.writeString(view, offset, args.clientMessageId);
  offset = BufferUtil.writeUUID(view, offset, args.conversationId);
  offset = BufferUtil.writeUUID(view, offset, args.messageId);
  offset = BufferUtil.writeString(view, offset, args.emoji);
  return new Uint8Array(buf);
}

export interface SocialCommandArgs {
  commandType: CommandType.FRIEND_USER | CommandType.UNFRIEND_USER | CommandType.BLOCK_USER | CommandType.UNBLOCK_USER;
  commandId: string;
  timestamp: bigint;
  callerUserId: string;
  callerDeviceId: string;
  clientMessageId: string;
  targetUserId: string;
}

export function encodeSocialCommand(args: SocialCommandArgs): Uint8Array {
  const size = 2 + 16 + 8
    + BufferUtil.stringByteLength(args.callerUserId)
    + BufferUtil.stringByteLength(args.callerDeviceId)
    + BufferUtil.stringByteLength(args.clientMessageId)
    + BufferUtil.stringByteLength(args.targetUserId);

  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint16(offset, args.commandType, false);
  offset += 2;
  offset = BufferUtil.writeUUID(view, offset, args.commandId);
  view.setBigInt64(offset, args.timestamp, false);
  offset += 8;
  offset = BufferUtil.writeString(view, offset, args.callerUserId);
  offset = BufferUtil.writeString(view, offset, args.callerDeviceId);
  offset = BufferUtil.writeString(view, offset, args.clientMessageId);
  offset = BufferUtil.writeString(view, offset, args.targetUserId);
  return new Uint8Array(buf);
}
