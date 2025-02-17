import React, { useState, useEffect, useRef } from 'react';
import { MessageDeliveryStatus, StoredConversation, StoredMessage, VibeClient } from '@vibe/sdk';

interface ChatThreadProps {
  client: VibeClient;
  conversation: StoredConversation | null;
  currentUserId: string;
}

export const ChatThread: React.FC<ChatThreadProps> = ({
  client,
  conversation,
  currentUserId,
}) => {
  const [messages, setMessages] = useState<StoredMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  const loadMessages = () => {
    if (conversation) {
      const msgs = client.storage.getMessages(conversation.conversationId);
      setMessages([...msgs]);
    } else {
      setMessages([]);
    }
  };

  useEffect(() => {
    loadMessages();
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [conversation?.conversationId]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || !conversation || sending) return;

    const text = input.trim();
    setInput('');
    setSending(true);

    try {
      await client.sendMessage(conversation.conversationId, text);
      loadMessages();
    } catch (e: any) {
      console.error('Failed to send:', e);
    } finally {
      setSending(false);
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  };

  if (!conversation) {
    return (
      <div style={{
        flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--text-muted)', fontSize: 14
      }}>
        Select a conversation from the left to start messaging
      </div>
    );
  }

  return (
    <main style={{ flex: 1, display: 'flex', flexDirection: 'column', background: 'var(--bg-darkest)' }}>
      {/* Header */}
      <div style={{
        padding: '12px 20px', background: 'var(--bg-primary)',
        borderBottom: '1px solid var(--border-color)', display: 'flex', alignItems: 'center', gap: 12
      }}>
        <div style={{ fontSize: 24 }}>{conversation.type === 'DIRECT' ? '👤' : '👥'}</div>
        <div>
          <h3 style={{ fontSize: 15, fontWeight: 700 }}>{conversation.title}</h3>
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            Lane Cursor: #{conversation.lastSeq.toString()} • Monotonic Fanout
          </span>
        </div>
      </div>

      {/* Message Feed */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {messages.map(msg => {
          const isSelf = msg.senderId === currentUserId;
          const statusIcon = msg.status === MessageDeliveryStatus.COMMITTED ? '✓'
            : msg.status === MessageDeliveryStatus.DELIVERED ? '✓✓'
            : msg.status === MessageDeliveryStatus.READ ? '✓✓'
            : '⏳';

          return (
            <div
              key={msg.messageId}
              style={{
                alignSelf: isSelf ? 'flex-end' : 'flex-start',
                maxWidth: '65%',
                display: 'flex',
                flexDirection: 'column',
                gap: 2,
              }}
            >
              <span style={{ fontSize: 10, color: 'var(--text-muted)', alignSelf: isSelf ? 'flex-end' : 'flex-start' }}>
                {isSelf ? 'You' : msg.senderId} • #{msg.seqNumber.toString()}
              </span>

              <div
                style={{
                  background: isSelf ? 'var(--bg-tertiary)' : 'var(--bg-secondary)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 10,
                  padding: '8px 14px',
                  color: isSelf ? '#fff' : 'var(--text-primary)',
                  fontSize: 13,
                  lineHeight: 1.4,
                  wordBreak: 'break-word',
                }}
              >
                {msg.content}
              </div>

              <div style={{ display: 'flex', gap: 4, justifyContent: isSelf ? 'flex-end' : 'flex-start', alignItems: 'center' }}>
                <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                  {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
                {isSelf && (
                  <span style={{ fontSize: 10, color: msg.status === MessageDeliveryStatus.READ ? 'var(--status-info)' : 'var(--text-muted)' }}>
                    {statusIcon}
                  </span>
                )}
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {/* Input row */}
      <form onSubmit={handleSend} style={{
        padding: '12px 16px', background: 'var(--bg-primary)',
        borderTop: '1px solid var(--border-color)', display: 'flex', gap: 10
      }}>
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder="Type a message..."
          style={{ flex: 1 }}
        />
        <button type="submit" className="btn-primary" disabled={sending || !input.trim()}>
          {sending ? 'Sending...' : 'Send'}
        </button>
      </form>
    </main>
  );
};
