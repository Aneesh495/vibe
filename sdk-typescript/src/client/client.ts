import { Frame } from '../protocol/frame.js';
import {
  AuthResultPayload,
  CommandPayload,
  CommandResultPayload,
  ConversationEventPayload,
  decodeAuthResult,
  decodeCommandResult,
  decodeConversationEvent,
  decodeError,
  encodeAcknowledge,
  encodeAuthenticate,
  encodeCommand,
  encodeHandshake,
  encodeHeartbeat,
  encodeSubscribe,
} from '../protocol/payloads.js';
import {
  encodeCreateConversationCommand,
  encodeRegisterUserCommand,
  encodeSendMessageCommand,
  encodeAddReactionCommand,
} from '../protocol/commands.js';
import { CommandType, ConversationType, EventType, FrameType, MessageDeliveryStatus } from '../protocol/types.js';
import { VibeClientStorage } from '../storage/storage.js';
import { VibeWebSocketTransport } from '../transport/websocket.js';

export interface VibeClientConfig {
  wsUrl: string;
  deviceId?: string;
  clientVersion?: string;
  heartbeatIntervalMs?: number;
  ackTimeoutMs?: number;
}

export type VibeEventMap = {
  stateChange: (state: string) => void;
  authenticated: (userId: string, token: string) => void;
  message: (event: ConversationEventPayload, content: string) => void;
  reaction: (convId: string, msgId: string, emoji: string, userId: string) => void;
  typing: (convId: string, userId: string, typing: boolean) => void;
  error: (code: number, message: string) => void;
  rawFrame: (dir: 'TX' | 'RX', type: string, correlationId: string, size: number, summary: string) => void;
};

export class VibeClient {
  public readonly config: Required<VibeClientConfig>;
  private readonly transport: VibeWebSocketTransport;
  public readonly storage = new VibeClientStorage();

  private correlationSeq = 1n;
  private pendingRequests = new Map<bigint, { resolve: (f: Frame) => void; reject: (err: any) => void; timer: any }>();
  private listeners: { [K in keyof VibeEventMap]?: VibeEventMap[K][] } = {};

  private authenticatedUserId: string | null = null;
  private sessionToken: string | null = null;
  private heartbeatTimer: any = null;

  constructor(config: VibeClientConfig) {
    this.config = {
      wsUrl: config.wsUrl,
      deviceId: config.deviceId || `web-${Math.random().toString(36).substring(2, 9)}`,
      clientVersion: config.clientVersion || 'vibe-web-sdk-2.0.0',
      heartbeatIntervalMs: config.heartbeatIntervalMs || 15000,
      ackTimeoutMs: config.ackTimeoutMs || 10000,
    };

    this.transport = new VibeWebSocketTransport(
      this.config.wsUrl,
      this.handleIncomingFrame.bind(this),
      this.handleStateChange.bind(this)
    );
  }

  public on<K extends keyof VibeEventMap>(event: K, listener: VibeEventMap[K]): void {
    if (!this.listeners[event]) {
      this.listeners[event] = [];
    }
    this.listeners[event]!.push(listener);
  }

  public emit<K extends keyof VibeEventMap>(event: K, ...args: Parameters<VibeEventMap[K]>): void {
    const list = this.listeners[event];
    if (list) {
      for (const fn of list) {
        (fn as any)(...args);
      }
    }
  }

  public connect(): void {
    this.transport.connect();
    if (!this.heartbeatTimer) {
      this.heartbeatTimer = setInterval(() => this.sendHeartbeat(), this.config.heartbeatIntervalMs);
    }
  }

  private handleStateChange(state: 'CONNECTING' | 'OPEN' | 'CLOSING' | 'CLOSED'): void {
    this.emit('stateChange', state);
    this.emit('rawFrame', 'TX', 'STATE_CHANGED', '0', 0, `WebSocket transport state -> ${state}`);

    if (state === 'OPEN') {
      this.sendHandshake();
    } else if (state === 'CLOSED') {
      this.failPendingRequests(new Error('WebSocket connection closed'));
    }
  }

  private sendHandshake(): void {
    const cid = this.nextCorrelationId();
    const payload = encodeHandshake({
      clientVersionNumber: 1,
      clientVersionString: this.config.clientVersion,
      deviceId: this.config.deviceId,
      flags: 0,
    });
    const frame = Frame.create(FrameType.HANDSHAKE, cid, payload);
    this.send(frame, cid).then(ack => {
      this.emit('rawFrame', 'RX', 'HANDSHAKE_ACK', cid.toString(), ack.payload.length, 'Handshake accepted by server');
    }).catch(e => console.warn('Handshake failed:', e));
  }

  private sendHeartbeat(): void {
    if (this.transport.isOpen()) {
      const cid = this.nextCorrelationId();
      const payload = encodeHeartbeat(BigInt(Date.now()));
      const frame = Frame.create(FrameType.HEARTBEAT, cid, payload);
      this.transport.send(frame);
    }
  }

  public async register(username: string, password: string, displayName: string, bio = '', avatarUrl = ''): Promise<AuthResultPayload> {
    const clientMsgId = `reg-${Math.random().toString(36).substring(2, 9)}`;
    const userId = `u-${Math.random().toString(36).substring(2, 9)}`;
    const cmdBody = encodeRegisterUserCommand({
      commandId: this.randomUUID(),
      timestamp: BigInt(Date.now()),
      callerUserId: userId,
      callerDeviceId: this.config.deviceId,
      clientMessageId: clientMsgId,
      username,
      passwordHash: password,
      displayName,
      bio,
      avatarUrl,
    });

    const cmdPayload = encodeCommand({
      commandType: CommandType.REGISTER_USER,
      clientMessageId: clientMsgId,
      conversationId: null,
      body: cmdBody,
    });

    const cid = this.nextCorrelationId();
    const frame = Frame.create(FrameType.COMMAND, cid, cmdPayload);
    const resp = await this.send(frame, cid);

    if (resp.header.type === FrameType.COMMAND_RESULT) {
      const res = decodeCommandResult(resp.payload);
      if (res.status === 0) {
        return this.login(username, password);
      } else {
        const errMsg = new TextDecoder().decode(res.payload);
        return { status: 1, userId: '', token: '', tokenExpiresAt: 0n, errorCode: res.errorCode, errorMessage: errMsg };
      }
    }
    throw new Error(`Unexpected response type: ${resp.header.type}`);
  }

  public async login(username: string, credential: string, isToken = false): Promise<AuthResultPayload> {
    const cid = this.nextCorrelationId();
    const payload = encodeAuthenticate({
      authMethod: isToken ? 0x02 : 0x01,
      username,
      credential,
      deviceId: this.config.deviceId,
    });
    const frame = Frame.create(FrameType.AUTHENTICATE, cid, payload);
    const resp = await this.send(frame, cid);

    if (resp.header.type === FrameType.AUTH_RESULT) {
      const result = decodeAuthResult(resp.payload);
      if (result.status === 0) {
        this.authenticatedUserId = result.userId;
        this.sessionToken = result.token;
        this.emit('authenticated', result.userId, result.token);
        this.resubscribeConversations();
        this.drainOutbox();
      }
      return result;
    }
    throw new Error(`Unexpected auth response type: ${resp.header.type}`);
  }

  public async createConversation(type: ConversationType, title: string, memberUserIds: string[]): Promise<string> {
    this.ensureAuth();
    const convId = this.randomUUID();
    const clientMsgId = `c-${Math.random().toString(36).substring(2, 9)}`;
    const members = Array.from(new Set([...memberUserIds, this.authenticatedUserId!]));

    const cmdBody = encodeCreateConversationCommand({
      commandId: this.randomUUID(),
      timestamp: BigInt(Date.now()),
      callerUserId: this.authenticatedUserId!,
      callerDeviceId: this.config.deviceId,
      clientMessageId: clientMsgId,
      conversationId: convId,
      convType: type,
      title,
      memberUserIds: members,
    });

    const cmdPayload = encodeCommand({
      commandType: CommandType.CREATE_CONVERSATION,
      clientMessageId: clientMsgId,
      conversationId: convId,
      body: cmdBody,
    });

    const cid = this.nextCorrelationId();
    const frame = Frame.create(FrameType.COMMAND, cid, cmdPayload);
    const resp = await this.send(frame, cid);

    if (resp.header.type === FrameType.COMMAND_RESULT) {
      const res = decodeCommandResult(resp.payload);
      if (res.status === 0) {
        this.storage.saveConversation({
          conversationId: convId,
          type,
          title,
          createdAt: Date.now(),
          lastSeq: 0n,
          unreadCount: 0,
        });
        this.subscribe(convId);
        return convId;
      }
      throw new Error(`Create conversation failed: ${res.errorCode}`);
    }
    throw new Error(`Unexpected frame type: ${resp.header.type}`);
  }

  public async sendMessage(conversationId: string, content: string): Promise<string> {
    this.ensureAuth();
    const messageId = this.randomUUID();
    const clientMsgId = `m-${Math.random().toString(36).substring(2, 9)}`;

    const cmdBody = encodeSendMessageCommand({
      commandId: this.randomUUID(),
      timestamp: BigInt(Date.now()),
      callerUserId: this.authenticatedUserId!,
      callerDeviceId: this.config.deviceId,
      clientMessageId: clientMsgId,
      messageId,
      conversationId,
      content,
    });

    this.storage.saveMessage({
      messageId,
      conversationId,
      senderId: this.authenticatedUserId!,
      seqNumber: 0n,
      content,
      timestamp: Date.now(),
      status: MessageDeliveryStatus.PENDING_OUTBOX,
    });

    const cmdPayload = encodeCommand({
      commandType: CommandType.SEND_MESSAGE,
      clientMessageId: clientMsgId,
      conversationId,
      body: cmdBody,
    });

    if (!this.transport.isOpen()) {
      this.storage.enqueueOutbox({
        clientMessageId: clientMsgId,
        commandType: CommandType.SEND_MESSAGE,
        conversationId,
        payload: cmdPayload,
        createdAt: Date.now(),
        retryCount: 0,
      });
      return messageId;
    }

    const cid = this.nextCorrelationId();
    const frame = Frame.create(FrameType.COMMAND, cid, cmdPayload);
    const resp = await this.send(frame, cid);

    if (resp.header.type === FrameType.COMMAND_RESULT) {
      const res = decodeCommandResult(resp.payload);
      if (res.status === 0) {
        this.storage.saveMessage({
          messageId,
          conversationId,
          senderId: this.authenticatedUserId!,
          seqNumber: res.assignedSeq,
          content,
          timestamp: Date.now(),
          status: MessageDeliveryStatus.COMMITTED,
        });
      }
    }
    return messageId;
  }

  public subscribe(conversationId: string, lastSeq = 0n): void {
    const cid = this.nextCorrelationId();
    const payload = encodeSubscribe({
      conversationId,
      lastKnownSeq: lastSeq,
      limit: 100,
    });
    const frame = Frame.create(FrameType.SUBSCRIBE, cid, payload);
    this.transport.send(frame);
  }

  private resubscribeConversations(): void {
    for (const conv of this.storage.getAllConversations()) {
      this.subscribe(conv.conversationId, conv.lastSeq);
    }
  }

  private handleIncomingFrame(frame: Frame): void {
    const cid = frame.header.correlationId;
    const pending = this.pendingRequests.get(cid);
    if (pending) {
      clearTimeout(pending.timer);
      this.pendingRequests.delete(cid);
      pending.resolve(frame);
    }

    this.emit('rawFrame', 'RX', FrameType[frame.header.type] || `0x${frame.header.type.toString(16)}`, cid.toString(), frame.payload.length, `Received frame`);

    switch (frame.header.type) {
      case FrameType.CONVERSATION_EVENT: {
        const event = decodeConversationEvent(frame.payload);
        if (event.eventType === EventType.MESSAGE_SENT) {
          // Decode MessageSentEvent payload (skip messageId 16, clientMessageId string, content string)
          const view = new DataView(event.payload.buffer, event.payload.byteOffset, event.payload.byteLength);
          let off = 16; // messageId
          const len1 = view.getUint16(off, false);
          off += 2 + len1; // clientMsgId
          const len2 = view.getUint16(off, false);
          off += 2;
          const content = new TextDecoder().decode(new Uint8Array(event.payload.buffer, event.payload.byteOffset + off, len2));

          this.storage.saveMessage({
            messageId: event.eventId,
            conversationId: event.conversationId,
            senderId: event.senderUserId,
            seqNumber: event.seqNumber,
            content,
            timestamp: Number(event.timestamp),
            status: MessageDeliveryStatus.DELIVERED,
          });

          // Send ACK
          const ackCid = this.nextCorrelationId();
          const ackPayload = encodeAcknowledge({
            conversationId: event.conversationId,
            ackType: 0x01, // DELIVERED
            upToSeq: event.seqNumber,
            ackTimestamp: BigInt(Date.now()),
          });
          this.transport.send(Frame.create(FrameType.ACKNOWLEDGE, ackCid, ackPayload));

          this.emit('message', event, content);
        }
        break;
      }
      case FrameType.ERROR: {
        const err = decodeError(frame.payload);
        this.emit('error', err.errorCode, err.message);
        break;
      }
    }
  }

  public send(frame: Frame, correlationId: bigint): Promise<Frame> {
    return new Promise((resolve, reject) => {
      if (!this.transport.isOpen()) {
        return reject(new Error('Transport is not open'));
      }

      const timer = setTimeout(() => {
        this.pendingRequests.delete(correlationId);
        reject(new Error(`Request ${correlationId} timed out`));
      }, this.config.ackTimeoutMs);

      this.pendingRequests.set(correlationId, { resolve, reject, timer });
      this.emit('rawFrame', 'TX', FrameType[frame.header.type] || `0x${frame.header.type.toString(16)}`, correlationId.toString(), frame.payload.length, `Sent frame`);
      this.transport.send(frame);
    });
  }

  private drainOutbox(): void {
    const items = this.storage.getPendingOutbox();
    for (const item of items) {
      const cid = this.nextCorrelationId();
      const frame = Frame.create(FrameType.COMMAND, cid, item.payload);
      this.send(frame, cid).then(() => {
        this.storage.removeOutbox(item.clientMessageId);
      }).catch(e => console.warn('Failed to drain outbox item:', e));
    }
  }

  private nextCorrelationId(): bigint {
    return this.correlationSeq++;
  }

  private failPendingRequests(err: Error): void {
    for (const { reject, timer } of this.pendingRequests.values()) {
      clearTimeout(timer);
      reject(err);
    }
    this.pendingRequests.clear();
  }

  private ensureAuth(): void {
    if (!this.authenticatedUserId) {
      throw new Error('Client is not authenticated');
    }
  }

  private randomUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  public close(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    this.transport.close();
  }
}
