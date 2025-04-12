import React, { useState } from 'react';
import { ConversationType, StoredConversation, StoredMessage, VibeClient } from '@vibe/sdk';

interface ChatDetailsSidebarProps {
  client: VibeClient;
  conversation: StoredConversation | null;
  currentUserId: string;
  onClose?: () => void;
  onLeave?: () => void;
}

export const ChatDetailsSidebar: React.FC<ChatDetailsSidebarProps> = ({
  client,
  conversation,
  currentUserId,
  onClose,
  onLeave,
}) => {
  const [inChatSearch, setInChatSearch] = useState('');

  if (!conversation) {
    return (
      <aside style={{
        width: 280, minWidth: 280, background: 'var(--bg-primary)',
        borderLeft: '1px solid var(--border-color)', display: 'flex',
        alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', fontSize: 13
      }}>
        Select a conversation to inspect details
      </aside>
    );
  }

  const messages: StoredMessage[] = client.storage.getMessages(conversation.conversationId);
  const sharedAttachments = messages.filter(m => !!m.attachmentId);

  return (
    <aside style={{
      width: 280, minWidth: 280, background: 'var(--bg-primary)',
      borderLeft: '1px solid var(--border-color)', display: 'flex', flexDirection: 'column',
      height: '100%', overflowY: 'auto'
    }}>
      {/* Sidebar Header */}
      <div style={{
        padding: '16px', borderBottom: '1px solid var(--border-color)',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center'
      }}>
        <h3 style={{ fontSize: 16, fontWeight: 700 }}>Details</h3>
        {onClose && (
          <button onClick={onClose} className="btn-secondary" style={{ padding: '2px 8px', fontSize: 12 }}>
            ✕
          </button>
        )}
      </div>

      <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* Conversation Info */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <div style={{ fontSize: 28 }}>{conversation.type === ConversationType.DIRECT ? '👤' : '👥'}</div>
          <span style={{ fontSize: 15, fontWeight: 700 }}>{conversation.title}</span>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {conversation.type === ConversationType.DIRECT ? 'Direct Message' : 'Group Channel'}
          </span>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'monospace' }}>
            ID: {conversation.conversationId.substring(0, 13)}...
          </span>
        </div>

        {/* In-chat search */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)' }}>
            Search in Conversation
          </span>
          <input
            placeholder="Search messages..."
            value={inChatSearch}
            onChange={e => setInChatSearch(e.target.value)}
            style={{ width: '100%', fontSize: 12, padding: '6px 10px' }}
          />
        </div>

        {/* Members */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)' }}>
            Members
          </span>
          <div style={{
            background: 'var(--bg-secondary)', borderRadius: 6, padding: '8px 10px',
            display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>👤 @{currentUserId} (You)</span>
              <span style={{ color: 'var(--status-success)', fontSize: 11 }}>● Online</span>
            </div>
          </div>
        </div>

        {/* Shared Attachments */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)' }}>
            Shared Files ({sharedAttachments.length})
          </span>
          <div style={{
            background: 'var(--bg-secondary)', borderRadius: 6, padding: '8px 10px',
            display: 'flex', flexDirection: 'column', gap: 8, fontSize: 12, maxHeight: 160, overflowY: 'auto'
          }}>
            {sharedAttachments.length === 0 ? (
              <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>No shared files yet</span>
            ) : (
              sharedAttachments.map(m => (
                <div key={m.messageId} style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span style={{ fontWeight: 600, color: 'var(--status-info)' }}>
                    📎 {m.attachmentName || 'Attachment'}
                  </span>
                  <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                    {Math.round((m.attachmentSize || 0) / 1024)} KB • {new Date(m.timestamp).toLocaleDateString()}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Conversation Actions */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
          <button
            onClick={() => {
              if (confirm(`Leave conversation "${conversation.title}"?`)) {
                onLeave?.();
              }
            }}
            style={{
              background: 'rgba(239, 68, 68, 0.15)', color: 'var(--status-danger)',
              border: '1px solid var(--status-danger)', borderRadius: 6,
              padding: '8px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer'
            }}
          >
            Leave Conversation
          </button>
        </div>
      </div>
    </aside>
  );
};
