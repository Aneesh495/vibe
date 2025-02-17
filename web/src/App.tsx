import React, { useState, useEffect } from 'react';
import { StoredConversation, VibeClient } from '@vibe/sdk';
import { AuthModal } from './components/AuthModal';
import { ChatSidebar } from './components/ChatSidebar';
import { ChatThread } from './components/ChatThread';
import { OperationalInspector } from './components/OperationalInspector';

interface RawFrameLog {
  id: string;
  time: string;
  dir: 'TX' | 'RX';
  type: string;
  correlationId: string;
  size: number;
  summary: string;
}

export const App: React.FC = () => {
  const [client, setClient] = useState<VibeClient | null>(null);
  const [currentUser, setCurrentUser] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'messenger' | 'inspector'>('messenger');

  const [conversations, setConversations] = useState<StoredConversation[]>([]);
  const [selectedConv, setSelectedConv] = useState<StoredConversation | null>(null);
  const [rawFrames, setRawFrames] = useState<RawFrameLog[]>([]);

  const handleAuthSuccess = (c: VibeClient, username: string) => {
    setClient(c);
    setCurrentUser(username);

    c.on('rawFrame', (dir, type, correlationId, size, summary) => {
      const now = new Date();
      const timeStr = `${now.toTimeString().split(' ')[0]}.${now.getMilliseconds().toString().padStart(3, '0')}`;
      const entry: RawFrameLog = {
        id: Math.random().toString(36).substring(2, 9),
        time: timeStr,
        dir,
        type,
        correlationId,
        size,
        summary,
      };
      setRawFrames(prev => [entry, ...prev.slice(0, 400)]);
    });

    c.on('message', () => {
      refreshConversations(c);
    });

    refreshConversations(c);
  };

  const refreshConversations = (c = client) => {
    if (c) {
      const list = c.storage.getAllConversations();
      setConversations([...list]);
    }
  };

  if (!client || !currentUser) {
    return <AuthModal onSuccess={handleAuthSuccess} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', width: '100vw' }}>
      {/* Top Navigation Bar */}
      <header style={{
        height: 48, background: 'var(--bg-primary)', borderBottom: '1px solid var(--border-color)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
          <span style={{ fontWeight: 800, color: 'var(--accent-primary)', fontSize: 16 }}>⚡ VIBE</span>
          <nav style={{ display: 'flex', gap: 6 }}>
            <button
              onClick={() => setActiveTab('messenger')}
              style={{
                background: activeTab === 'messenger' ? 'var(--bg-tertiary)' : 'transparent',
                color: activeTab === 'messenger' ? '#fff' : 'var(--text-secondary)',
                padding: '4px 12px', fontSize: 13
              }}
            >
              💬 Messenger
            </button>
            <button
              onClick={() => setActiveTab('inspector')}
              style={{
                background: activeTab === 'inspector' ? 'var(--bg-tertiary)' : 'transparent',
                color: activeTab === 'inspector' ? '#fff' : 'var(--text-secondary)',
                padding: '4px 12px', fontSize: 13
              }}
            >
              🛠️ Operational Inspector
            </button>
          </nav>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 12 }}>
          <span style={{ color: 'var(--text-secondary)' }}>
            Signed in as <b style={{ color: 'var(--text-primary)' }}>@{currentUser}</b>
          </span>
          <span style={{ color: 'var(--status-success)', fontWeight: 600 }}>● Connected</span>
        </div>
      </header>

      {/* Main Content Area */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {activeTab === 'messenger' ? (
          <>
            <ChatSidebar
              client={client}
              conversations={conversations}
              activeConvId={selectedConv?.conversationId || null}
              onSelect={setSelectedConv}
              onRefresh={() => refreshConversations(client)}
            />
            <ChatThread
              client={client}
              conversation={selectedConv}
              currentUserId={currentUser}
            />
          </>
        ) : (
          <OperationalInspector
            client={client}
            rawFrames={rawFrames}
            onClearLogs={() => setRawFrames([])}
          />
        )}
      </div>
    </div>
  );
};
