import { CommandType, ConversationType, MessageDeliveryStatus } from '../protocol/types.js';

export interface StoredConversation {
  conversationId: string;
  type: ConversationType;
  title: string;
  createdAt: number;
  lastSeq: bigint;
  unreadCount: number;
}

export interface StoredMessage {
  messageId: string;
  conversationId: string;
  senderId: string;
  seqNumber: bigint;
  content: string;
  timestamp: number;
  status: MessageDeliveryStatus;
  attachmentId?: string;
  attachmentName?: string;
  attachmentSize?: number;
  replyToMessageId?: string;
  reactions?: Record<string, string[]>;
}

export interface OutboxItem {
  clientMessageId: string;
  commandType: CommandType;
  conversationId: string | null;
  payload: Uint8Array;
  createdAt: number;
  retryCount: number;
}

export class VibeClientStorage {
  private conversations = new Map<string, StoredConversation>();
  private messages = new Map<string, StoredMessage[]>();
  private outbox: OutboxItem[] = [];

  constructor(private readonly storagePrefix = 'vibe_') {
    this.loadFromLocalStorage();
  }

  private loadFromLocalStorage(): void {
    if (typeof localStorage === 'undefined') return;
    try {
      const convs = localStorage.getItem(`${this.storagePrefix}conversations`);
      if (convs) {
        const parsed = JSON.parse(convs);
        for (const c of parsed) {
          this.conversations.set(c.conversationId, {
            ...c,
            lastSeq: BigInt(c.lastSeq),
          });
        }
      }
    } catch (e) {
      console.warn('Failed to load local storage:', e);
    }
  }

  private saveToLocalStorage(): void {
    if (typeof localStorage === 'undefined') return;
    try {
      const convList = Array.from(this.conversations.values()).map(c => ({
        ...c,
        lastSeq: c.lastSeq.toString(),
      }));
      localStorage.setItem(`${this.storagePrefix}conversations`, JSON.stringify(convList));
    } catch (e) {
      console.warn('Failed to save to local storage:', e);
    }
  }

  public saveConversation(conv: StoredConversation): void {
    this.conversations.set(conv.conversationId, conv);
    this.saveToLocalStorage();
  }

  public getConversation(id: string): StoredConversation | undefined {
    return this.conversations.get(id);
  }

  public getAllConversations(): StoredConversation[] {
    return Array.from(this.conversations.values()).sort((a, b) => Number(b.lastSeq - a.lastSeq));
  }

  public saveMessage(msg: StoredMessage): void {
    const list = this.messages.get(msg.conversationId) || [];
    const idx = list.findIndex(m => m.messageId === msg.messageId);
    if (idx >= 0) {
      list[idx] = msg;
    } else {
      list.push(msg);
      list.sort((a, b) => Number(a.seqNumber - b.seqNumber));
    }
    this.messages.set(msg.conversationId, list);
  }

  public getMessages(conversationId: string): StoredMessage[] {
    return this.messages.get(conversationId) || [];
  }

  public enqueueOutbox(item: OutboxItem): void {
    this.outbox.push(item);
  }

  public getPendingOutbox(): OutboxItem[] {
    return [...this.outbox];
  }

  public removeOutbox(clientMessageId: string): void {
    this.outbox = this.outbox.filter(item => item.clientMessageId !== clientMessageId);
  }
}
