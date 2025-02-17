import { ErrorCode, CommandType } from './types.js';

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

export class BufferUtil {
  public static writeString(view: DataView, offset: number, str: string): number {
    const bytes = textEncoder.encode(str);
    view.setUint16(offset, bytes.length, false);
    offset += 2;
    new Uint8Array(view.buffer, view.byteOffset + offset, bytes.length).set(bytes);
    return offset + bytes.length;
  }

  public static readString(view: DataView, offset: number): { value: string; nextOffset: number } {
    const len = view.getUint16(offset, false);
    offset += 2;
    const bytes = new Uint8Array(view.buffer, view.byteOffset + offset, len);
    const value = textDecoder.decode(bytes);
    return { value, nextOffset: offset + len };
  }

  public static stringByteLength(str: string): number {
    return 2 + textEncoder.encode(str).length;
  }

  public static writeUUID(view: DataView, offset: number, uuidStr: string): number {
    const clean = uuidStr.replace(/-/g, '');
    const msb = BigInt('0x' + clean.slice(0, 16));
    const lsb = BigInt('0x' + clean.slice(16, 32));
    view.setBigInt64(offset, msb, false);
    view.setBigInt64(offset + 8, lsb, false);
    return offset + 16;
  }

  public static readUUID(view: DataView, offset: number): { value: string; nextOffset: number } {
    const msb = view.getBigInt64(offset, false);
    const lsb = view.getBigInt64(offset + 8, false);
    const hexMsb = (msb < 0n ? msb + 0x10000000000000000n : msb).toString(16).padStart(16, '0');
    const hexLsb = (lsb < 0n ? lsb + 0x10000000000000000n : lsb).toString(16).padStart(16, '0');
    const raw = hexMsb + hexLsb;
    const value = `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20, 32)}`;
    return { value, nextOffset: offset + 16 };
  }

  public static writeBytes(view: DataView, offset: number, bytes: Uint8Array): number {
    view.setUint32(offset, bytes.length, false);
    offset += 4;
    new Uint8Array(view.buffer, view.byteOffset + offset, bytes.length).set(bytes);
    return offset + bytes.length;
  }

  public static readBytes(view: DataView, offset: number): { value: Uint8Array; nextOffset: number } {
    const len = view.getUint32(offset, false);
    offset += 4;
    const slice = new Uint8Array(view.buffer, view.byteOffset + offset, len);
    const copy = new Uint8Array(slice);
    return { value: copy, nextOffset: offset + len };
  }

  public static bytesLength(bytes: Uint8Array): number {
    return 4 + bytes.length;
  }
}

// 0x01 HANDSHAKE
export interface HandshakePayload {
  clientVersionNumber: number;
  clientVersionString: string;
  deviceId: string;
  flags: number;
}

export function encodeHandshake(h: HandshakePayload): Uint8Array {
  const size = 2 + BufferUtil.stringByteLength(h.clientVersionString) + BufferUtil.stringByteLength(h.deviceId) + 4;
  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint16(offset, h.clientVersionNumber, false);
  offset += 2;
  offset = BufferUtil.writeString(view, offset, h.clientVersionString);
  offset = BufferUtil.writeString(view, offset, h.deviceId);
  view.setUint32(offset, h.flags, false);
  return new Uint8Array(buf);
}

// 0x03 AUTHENTICATE
export interface AuthenticatePayload {
  authMethod: number; // 0x01: password, 0x02: token
  username: string;
  credential: string;
  deviceId: string;
}

export function encodeAuthenticate(a: AuthenticatePayload): Uint8Array {
  const size = 1 + BufferUtil.stringByteLength(a.username) + BufferUtil.stringByteLength(a.credential) + BufferUtil.stringByteLength(a.deviceId);
  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint8(offset++, a.authMethod);
  offset = BufferUtil.writeString(view, offset, a.username);
  offset = BufferUtil.writeString(view, offset, a.credential);
  offset = BufferUtil.writeString(view, offset, a.deviceId);
  return new Uint8Array(buf);
}

// 0x04 AUTH_RESULT
export interface AuthResultPayload {
  status: number;
  userId: string;
  token: string;
  tokenExpiresAt: bigint;
  errorCode: number;
  errorMessage: string;
}

export function decodeAuthResult(bytes: Uint8Array): AuthResultPayload {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 0;
  const status = view.getUint8(offset++);
  const { value: userId, nextOffset: o1 } = BufferUtil.readString(view, offset);
  const { value: token, nextOffset: o2 } = BufferUtil.readString(view, o1);
  const tokenExpiresAt = view.getBigInt64(o2, false);
  const errorCode = view.getUint16(o2 + 8, false);
  const { value: errorMessage } = BufferUtil.readString(view, o2 + 10);
  return { status, userId, token, tokenExpiresAt, errorCode, errorMessage };
}

// 0x06 COMMAND
export interface CommandPayload {
  commandType: CommandType;
  clientMessageId: string;
  conversationId: string | null;
  body: Uint8Array;
}

export function encodeCommand(c: CommandPayload): Uint8Array {
  const size = 1 + BufferUtil.stringByteLength(c.clientMessageId) + 1 + (c.conversationId ? 16 : 0) + BufferUtil.bytesLength(c.body);
  const buf = new ArrayBuffer(size);
  const view = new DataView(buf);
  let offset = 0;
  view.setUint8(offset++, c.commandType);
  offset = BufferUtil.writeString(view, offset, c.clientMessageId);
  if (c.conversationId) {
    view.setUint8(offset++, 1);
    offset = BufferUtil.writeUUID(view, offset, c.conversationId);
  } else {
    view.setUint8(offset++, 0);
  }
  offset = BufferUtil.writeBytes(view, offset, c.body);
  return new Uint8Array(buf);
}

// 0x07 COMMAND_RESULT
export interface CommandResultPayload {
  status: number;
  assignedSeq: bigint;
  committedTimestamp: bigint;
  errorCode: number;
  payload: Uint8Array;
}

export function decodeCommandResult(bytes: Uint8Array): CommandResultPayload {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 0;
  const status = view.getUint8(offset++);
  const assignedSeq = view.getBigInt64(offset, false);
  offset += 8;
  const committedTimestamp = view.getBigInt64(offset, false);
  offset += 8;
  const errorCode = view.getUint16(offset, false);
  offset += 2;
  const { value: payload } = BufferUtil.readBytes(view, offset);
  return { status, assignedSeq, committedTimestamp, errorCode, payload };
}

// 0x08 SUBSCRIBE
export interface SubscribePayload {
  conversationId: string;
  lastKnownSeq: bigint;
  limit: number;
}

export function encodeSubscribe(s: SubscribePayload): Uint8Array {
  const buf = new ArrayBuffer(16 + 8 + 4);
  const view = new DataView(buf);
  let offset = BufferUtil.writeUUID(view, 0, s.conversationId);
  view.setBigInt64(offset, s.lastKnownSeq, false);
  offset += 8;
  view.setUint32(offset, s.limit, false);
  return new Uint8Array(buf);
}

// 0x09 CONVERSATION_EVENT
export interface ConversationEventPayload {
  conversationId: string;
  seqNumber: bigint;
  eventId: string;
  eventType: number;
  senderUserId: string;
  timestamp: bigint;
  payload: Uint8Array;
}

export function decodeConversationEvent(bytes: Uint8Array): ConversationEventPayload {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 0;
  const { value: conversationId, nextOffset: o1 } = BufferUtil.readUUID(view, offset);
  const seqNumber = view.getBigInt64(o1, false);
  offset = o1 + 8;
  const { value: eventId, nextOffset: o2 } = BufferUtil.readUUID(view, offset);
  const eventType = view.getUint16(o2, false);
  offset = o2 + 2;
  const { value: senderUserId, nextOffset: o3 } = BufferUtil.readString(view, offset);
  const timestamp = view.getBigInt64(o3, false);
  offset = o3 + 8;
  const { value: payload } = BufferUtil.readBytes(view, offset);
  return { conversationId, seqNumber, eventId, eventType, senderUserId, timestamp, payload };
}

// 0x0A ACKNOWLEDGE
export interface AcknowledgePayload {
  conversationId: string;
  ackType: number; // 0x01: delivered, 0x02: read
  upToSeq: bigint;
  ackTimestamp: bigint;
}

export function encodeAcknowledge(a: AcknowledgePayload): Uint8Array {
  const buf = new ArrayBuffer(16 + 1 + 8 + 8);
  const view = new DataView(buf);
  let offset = BufferUtil.writeUUID(view, 0, a.conversationId);
  view.setUint8(offset++, a.ackType);
  view.setBigInt64(offset, a.upToSeq, false);
  offset += 8;
  view.setBigInt64(offset, a.ackTimestamp, false);
  return new Uint8Array(buf);
}

// 0x0C HEARTBEAT
export function encodeHeartbeat(timestamp: bigint, isPing = true): Uint8Array {
  const buf = new ArrayBuffer(8 + 1);
  const view = new DataView(buf);
  view.setBigInt64(0, timestamp, false);
  view.setUint8(8, isPing ? 1 : 0);
  return new Uint8Array(buf);
}

// 0x0F ERROR
export interface ErrorPayload {
  errorCode: ErrorCode;
  message: string;
  details: Uint8Array;
}

export function decodeError(bytes: Uint8Array): ErrorPayload {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const errorCode = view.getUint16(0, false) as ErrorCode;
  const { value: message, nextOffset } = BufferUtil.readString(view, 2);
  const { value: details } = BufferUtil.readBytes(view, nextOffset);
  return { errorCode, message, details };
}
