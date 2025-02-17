import React, { useState } from 'react';
import { VibeClient } from '@vibe/sdk';

interface AuthModalProps {
  onSuccess: (client: VibeClient, username: string) => void;
}

export const AuthModal: React.FC<AuthModalProps> = ({ onSuccess }) => {
  const [isRegister, setIsRegister] = useState(false);
  const [host, setHost] = useState(window.location.hostname || '127.0.0.1');
  const [port, setPort] = useState('8080'); // Netty WebSocket bridge port
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [bio, setBio] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setStatus('Username and password are required');
      return;
    }

    setBusy(true);
    setStatus('Connecting to WebSocket bridge...');

    try {
      const wsUrl = `ws://${host}:${port}/ws`;
      const client = new VibeClient({ wsUrl });
      client.connect();

      // Wait briefly for transport open
      await new Promise(r => setTimeout(r, 600));

      if (isRegister) {
        setStatus('Registering new user...');
        const res = await client.register(username.trim(), password.trim(), displayName.trim() || username.trim(), bio.trim());
        if (res.status === 0) {
          onSuccess(client, username.trim());
        } else {
          setStatus(`Registration rejected: ${res.errorMessage}`);
        }
      } else {
        setStatus('Authenticating...');
        const res = await client.login(username.trim(), password.trim());
        if (res.status === 0) {
          onSuccess(client, username.trim());
        } else {
          setStatus(`Login failed: ${res.errorMessage}`);
        }
      }
    } catch (err: any) {
      setStatus(`Connection error: ${err.message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(15, 17, 21, 0.85)',
      backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100
    }}>
      <div className="card" style={{ width: 400, maxWidth: '90%' }}>
        <div style={{ textAlign: 'center', marginBottom: 20 }}>
          <h1 style={{ fontSize: 28, color: 'var(--accent-primary)', fontWeight: 800 }}>⚡ VIBE</h1>
          <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>Distributed Reactive Messenger</p>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 8 }}>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Host</label>
              <input value={host} onChange={e => setHost(e.target.value)} style={{ width: '100%' }} />
            </div>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-secondary)' }}>WS Port</label>
              <input value={port} onChange={e => setPort(e.target.value)} style={{ width: '100%' }} />
            </div>
          </div>

          <div>
            <label style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Username</label>
            <input value={username} onChange={e => setUsername(e.target.value)} style={{ width: '100%' }} autoFocus />
          </div>

          <div>
            <label style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Password</label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)} style={{ width: '100%' }} />
          </div>

          {isRegister && (
            <>
              <div>
                <label style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Display Name</label>
                <input value={displayName} onChange={e => setDisplayName(e.target.value)} style={{ width: '100%' }} />
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Bio</label>
                <input value={bio} onChange={e => setBio(e.target.value)} style={{ width: '100%' }} />
              </div>
            </>
          )}

          {status && (
            <div style={{ fontSize: 12, color: status.includes('failed') || status.includes('error') || status.includes('rejected') ? 'var(--status-danger)' : 'var(--status-info)', textAlign: 'center' }}>
              {status}
            </div>
          )}

          <button type="submit" className="btn-primary" disabled={busy} style={{ marginTop: 8, padding: 10 }}>
            {busy ? 'Connecting...' : isRegister ? 'Create Account' : 'Sign In'}
          </button>

          <button
            type="button"
            onClick={() => { setIsRegister(!isRegister); setStatus(null); }}
            style={{ background: 'transparent', color: 'var(--text-muted)', fontSize: 12, marginTop: 4 }}
          >
            {isRegister ? 'Already have an account? Sign in' : "Don't have an account? Sign up"}
          </button>
        </form>
      </div>
    </div>
  );
};
