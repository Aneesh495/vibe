import React, { useState, useEffect, useRef } from 'react';
import { MessageDeliveryStatus, StoredConversation, StoredMessage, VibeClient } from '@vibe/sdk';

interface ChatThreadProps {
  client: VibeClient;
  conversation: StoredConversation | null;
  currentUserId: string;
  sidebarOpen?: boolean;
  onToggleSidebar?: () => void;
}

interface StagedReply {
  messageId: string;
  senderId: string;
  content: string;
}

const QUICK_EMOJIS = ['👍', '❤️', '😂', '🎉', '🔥', '🚀'];

export const ChatThread: React.FC<ChatThreadProps> = ({
  client,
  conversation,
  currentUserId,
  sidebarOpen = true,
  onToggleSidebar,
}) => {
  const [messages, setMessages] = useState<StoredMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [stagedReply, setStagedReply] = useState<StagedReply | null>(null);
  const [stagedAttachment, setStagedAttachment] = useState<{ name: string; size: number } | null>(null);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [isDragging, setIsDragging] = useState(false);

  const bottomRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

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
    setStagedReply(null);
    setStagedAttachment(null);
    setTimeout(() => {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, 50);
  }, [conversation?.conversationId]);

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if ((!input.trim() && !stagedAttachment) || !conversation || sending) return;

    const text = input.trim();
    setInput('');
    setSending(true);

    try {
      await client.sendMessage(conversation.conversationId, text);
      loadMessages();
      setStagedReply(null);
      setStagedAttachment(null);
    } catch (err: any) {
      console.error('Failed to send:', err);
    } finally {
      setSending(false);
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handleAddReaction = (messageId: string, emoji: string) => {
    setMessages(prev => prev.map(m => {
      if (m.messageId !== messageId) return m;
      const currentReactions = { ...(m.reactions || {}) };
      const users = currentReactions[emoji] || [];
      if (users.includes(currentUserId)) {
        currentReactions[emoji] = users.filter(u => u !== currentUserId);
        if (currentReactions[emoji].length === 0) delete currentReactions[emoji];
      } else {
        currentReactions[emoji] = [...users, currentUserId];
      }
      return { ...m, reactions: currentReactions };
    }));
  };

  const handleFileDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      setStagedAttachment({ name: file.name, size: file.size });
    }
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      setStagedAttachment({ name: file.name, size: file.size });
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
    <main
      onDragOver={e => { e.preventDefault(); setIsDragging(true); }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleFileDrop}
      style={{
        flex: 1, display: 'flex', flexDirection: 'column', background: 'var(--bg-darkest)',
        position: 'relative'
      }}
    >
      {/* Drag & Drop Overlay */}
      {isDragging && (
        <div style={{
          position: 'absolute', inset: 0, background: 'rgba(99, 102, 241, 0.25)',
          border: '2px dashed var(--accent-primary)', zIndex: 50,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: '#fff', fontSize: 18, fontWeight: 700, pointerEvents: 'none'
        }}>
          Drop attachment to upload
        </div>
      )}

      {/* Header */}
      <div style={{
        padding: '12px 20px', background: 'var(--bg-primary)',
        borderBottom: '1px solid var(--border-color)', display: 'flex',
        alignItems: 'center', justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ fontSize: 24 }}>{conversation.type === 'DIRECT' ? '👤' : '👥'}</div>
          <div>
            <h3 style={{ fontSize: 15, fontWeight: 700 }}>{conversation.title}</h3>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              Lane Cursor: #{conversation.lastSeq.toString()} • Monotonic Fanout
            </span>
          </div>
        </div>

        {onToggleSidebar && (
          <button
            onClick={onToggleSidebar}
            className="btn-secondary"
            title="Toggle Details Sidebar"
            style={{ padding: '6px 10px', fontSize: 12 }}
          >
            {sidebarOpen ? '➡️ Details' : '⬅️ Details'}
          </button>
        )}
      </div>

      {/* Message Feed */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
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
                maxWidth: '68%',
                display: 'flex',
                flexDirection: 'column',
                gap: 3,
              }}
            >
              {/* Sender & Seq */}
              <span style={{ fontSize: 10, color: 'var(--text-muted)', alignSelf: isSelf ? 'flex-end' : 'flex-start' }}>
                {isSelf ? 'You' : msg.senderId} • #{msg.seqNumber.toString()}
              </span>

              {/* Message Bubble */}
              <div
                style={{
                  background: isSelf ? 'var(--bg-tertiary)' : 'var(--bg-secondary)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 10,
                  padding: '10px 14px',
                  color: isSelf ? '#fff' : 'var(--text-primary)',
                  fontSize: 13,
                  lineHeight: 1.4,
                  wordBreak: 'break-word',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6
                }}
              >
                {/* Reply quote preview if present */}
                {msg.replyToMessageId && (
                  <div style={{
                    padding: '4px 8px', background: 'rgba(0,0,0,0.2)',
                    borderLeft: '3px solid var(--accent-primary)',
                    borderRadius: 4, fontSize: 11, color: 'var(--text-secondary)'
                  }}>
                    Replying to previous message
                  </div>
                )}

                <div>{msg.content}</div>

                {/* Inline Attachment Preview */}
                {msg.attachmentName && (
                  <div style={{
                    display: 'flex', alignItems: 'center', gap: 8,
                    padding: '6px 10px', background: 'rgba(0,0,0,0.15)',
                    borderRadius: 6, fontSize: 12, color: 'var(--status-info)'
                  }}>
                    <span>📎</span>
                    <span style={{ fontWeight: 600 }}>{msg.attachmentName}</span>
                    <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                      ({Math.round((msg.attachmentSize || 0) / 1024)} KB)
                    </span>
                  </div>
                )}
              </div>

              {/* Timestamp, Delivery Icon, Reply Button, and Reactions */}
              <div style={{
                display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap',
                justifyContent: isSelf ? 'flex-end' : 'flex-start'
              }}>
                <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                  {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
                {isSelf && (
                  <span style={{ fontSize: 10, color: msg.status === MessageDeliveryStatus.READ ? 'var(--status-info)' : 'var(--text-muted)' }}>
                    {statusIcon}
                  </span>
                )}

                {/* Reply trigger button */}
                <button
                  onClick={() => setStagedReply({
                    messageId: msg.messageId,
                    senderId: msg.senderId,
                    content: msg.content.length > 30 ? msg.content.substring(0, 27) + '...' : msg.content
                  })}
                  style={{
                    background: 'transparent', border: 'none', color: 'var(--text-muted)',
                    fontSize: 11, cursor: 'pointer', padding: '0 4px'
                  }}
                  title="Reply to message"
                >
                  ↩ Reply
                </button>

                {/* Quick emoji reaction buttons */}
                {QUICK_EMOJIS.slice(0, 3).map(emoji => (
                  <button
                    key={emoji}
                    onClick={() => handleAddReaction(msg.messageId, emoji)}
                    style={{
                      background: 'transparent', border: 'none', fontSize: 11,
                      cursor: 'pointer', padding: '0 2px', opacity: 0.6
                    }}
                  >
                    {emoji}
                  </button>
                ))}
              </div>

              {/* Reaction Pills */}
              {msg.reactions && Object.keys(msg.reactions).length > 0 && (
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 2 }}>
                  {Object.entries(msg.reactions).map(([emoji, users]) => (
                    <button
                      key={emoji}
                      onClick={() => handleAddReaction(msg.messageId, emoji)}
                      style={{
                        background: users.includes(currentUserId) ? 'rgba(99, 102, 241, 0.25)' : 'var(--bg-secondary)',
                        border: '1px solid ' + (users.includes(currentUserId) ? 'var(--accent-primary)' : 'var(--border-color)'),
                        borderRadius: 12, padding: '1px 6px', fontSize: 11,
                        cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4
                      }}
                    >
                      <span>{emoji}</span>
                      <span style={{ fontSize: 10, color: 'var(--text-secondary)' }}>{users.length}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {/* Input container */}
      <div style={{
        background: 'var(--bg-primary)', borderTop: '1px solid var(--border-color)',
        display: 'flex', flexDirection: 'column'
      }}>
        {/* Staged Reply Banner */}
        {stagedReply && (
          <div style={{
            padding: '6px 16px', background: 'var(--bg-secondary)',
            borderBottom: '1px solid var(--border-color)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12
          }}>
            <span style={{ color: 'var(--text-secondary)' }}>
              Replying to <b style={{ color: 'var(--text-primary)' }}>@{stagedReply.senderId}</b>: "{stagedReply.content}"
            </span>
            <button
              onClick={() => setStagedReply(null)}
              style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 13 }}
            >
              ✕
            </button>
          </div>
        )}

        {/* Staged Attachment Banner */}
        {stagedAttachment && (
          <div style={{
            padding: '6px 16px', background: 'var(--bg-secondary)',
            borderBottom: '1px solid var(--border-color)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12
          }}>
            <span style={{ color: 'var(--status-info)' }}>
              📎 Attached: <b>{stagedAttachment.name}</b> ({Math.round(stagedAttachment.size / 1024)} KB)
            </span>
            <button
              onClick={() => setStagedAttachment(null)}
              style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 13 }}
            >
              ✕
            </button>
          </div>
        )}

        {/* Emoji picker drawer */}
        {showEmojiPicker && (
          <div style={{
            padding: '8px 16px', background: 'var(--bg-secondary)',
            borderBottom: '1px solid var(--border-color)', display: 'flex', gap: 8
          }}>
            {QUICK_EMOJIS.map(emoji => (
              <button
                key={emoji}
                onClick={() => {
                  setInput(prev => prev + emoji);
                  setShowEmojiPicker(false);
                }}
                style={{
                  background: 'var(--bg-tertiary)', border: 'none', borderRadius: 4,
                  fontSize: 16, padding: '4px 8px', cursor: 'pointer'
                }}
              >
                {emoji}
              </button>
            ))}
          </div>
        )}

        {/* Input Form */}
        <form onSubmit={handleSend} style={{ padding: '10px 16px', display: 'flex', alignItems: 'center', gap: 8 }}>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileInputChange}
            style={{ display: 'none' }}
          />

          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="btn-secondary"
            title="Attach File"
            style={{ padding: '6px 10px', fontSize: 14 }}
          >
            📎
          </button>

          <button
            type="button"
            onClick={() => setShowEmojiPicker(!showEmojiPicker)}
            className="btn-secondary"
            title="Emoji Picker"
            style={{ padding: '6px 10px', fontSize: 14 }}
          >
            😀
          </button>

          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="Type a message (Enter to send)..."
            style={{ flex: 1, fontSize: 13, padding: '8px 12px' }}
          />

          <button type="submit" className="btn-primary" disabled={sending || (!input.trim() && !stagedAttachment)}>
            {sending ? 'Sending...' : 'Send'}
          </button>
        </form>
      </div>
    </main>
  );
};
