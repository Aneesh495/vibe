import React, { useState } from 'react';
import { ConversationType, StoredConversation, VibeClient } from '@vibe/sdk';

interface ChatSidebarProps {
  client: VibeClient;
  conversations: StoredConversation[];
  activeConvId: string | null;
  onSelect: (conv: StoredConversation) => void;
  onRefresh: () => void;
}

export const ChatSidebar: React.FC<ChatSidebarProps> = ({
  client,
  conversations,
  activeConvId,
  onSelect,
  onRefresh,
}) => {
  const [search, setSearch] = useState('');

  const handleCreateDirect = async () => {
    const user = prompt('Enter username for direct chat:');
    if (user && user.trim()) {
      try {
        await client.createConversation(ConversationType.DIRECT, user.trim(), [user.trim()]);
        onRefresh();
      } catch (e: any) {
        alert('Failed to create direct chat: ' + e.message);
      }
    }
  };

  const handleCreateGroup = async () => {
    const title = prompt('Enter group name:');
    if (!title || !title.trim()) return;

    const membersStr = prompt('Enter member usernames or IDs (comma-separated):');
    const members = (membersStr || '').split(',').map(s => s.trim()).filter(Boolean);

    try {
      await client.createConversation(ConversationType.GROUP, title.trim(), members);
      onRefresh();
    } catch (e: any) {
      alert('Failed to create group: ' + e.message);
    }
  };

  const filtered = conversations.filter(c =>
    c.title.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <aside style={{
      width: 300, minWidth: 300, background: 'var(--bg-primary)',
      borderRight: '1px solid var(--border-color)', display: 'flex', flexDirection: 'column'
    }}>
      <div style={{ padding: '16px 12px 8px 12px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ fontSize: 18, fontWeight: 700 }}>Messages</h2>
          <div style={{ display: 'flex', gap: 6 }}>
            <button onClick={handleCreateDirect} className="btn-secondary" style={{ padding: '4px 8px', fontSize: 11 }}>+ Direct</button>
            <button onClick={handleCreateGroup} className="btn-secondary" style={{ padding: '4px 8px', fontSize: 11 }}>+ Group</button>
          </div>
        </div>

        <input
          placeholder="Search conversations..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          style={{ width: '100%', fontSize: 13, padding: '6px 10px' }}
        />
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '4px 8px' }}>
        {filtered.length === 0 ? (
          <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
            No conversations yet
          </div>
        ) : (
          filtered.map(conv => {
            const isActive = conv.conversationId === activeConvId;
            return (
              <div
                key={conv.conversationId}
                onClick={() => onSelect(conv)}
                style={{
                  padding: '10px 12px',
                  borderRadius: 6,
                  cursor: 'pointer',
                  marginBottom: 4,
                  background: isActive ? 'var(--bg-tertiary)' : 'transparent',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  transition: 'background 0.1s ease',
                }}
              >
                <div style={{ fontSize: 20 }}>
                  {conv.type === ConversationType.DIRECT ? '👤' : '👥'}
                </div>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: 13, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {conv.title}
                    </span>
                    <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                      #{conv.lastSeq.toString()}
                    </span>
                  </div>
                </div>

                {conv.unreadCount > 0 && (
                  <span className="badge" style={{ background: 'var(--accent-primary)', color: 'white' }}>
                    {conv.unreadCount}
                  </span>
                )}
              </div>
            );
          })
        )}
      </div>
    </aside>
  );
};
