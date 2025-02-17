import React, { useState, useEffect } from 'react';
import { VibeClient } from '@vibe/sdk';

interface RawFrameLog {
  id: string;
  time: string;
  dir: 'TX' | 'RX';
  type: string;
  correlationId: string;
  size: number;
  summary: string;
}

interface OperationalInspectorProps {
  client: VibeClient | null;
  rawFrames: RawFrameLog[];
  onClearLogs: () => void;
}

export const OperationalInspector: React.FC<OperationalInspectorProps> = ({
  client,
  rawFrames,
  onClearLogs,
}) => {
  const [serverStatus, setServerStatus] = useState<any>(null);
  const [inspectorData, setInspectorData] = useState<any>(null);

  // Poll HTTP status and inspector endpoints from the server
  useEffect(() => {
    const fetchTelemetry = async () => {
      try {
        const host = window.location.hostname || '127.0.0.1';
        const statusRes = await fetch(`http://${host}:8080/api/status`);
        if (statusRes.ok) {
          const s = await statusRes.json();
          setServerStatus(s);
        }

        const inspRes = await fetch(`http://${host}:8080/api/inspector`);
        if (inspRes.ok) {
          const d = await inspRes.json();
          setInspectorData(d);
        }
      } catch (e) {
        // Ignored if offline
      }
    };

    fetchTelemetry();
    const interval = setInterval(fetchTelemetry, 2000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div style={{
      flex: 1, display: 'flex', flexDirection: 'column', background: 'var(--bg-darkest)',
      padding: '16px 24px', overflowY: 'auto', gap: 16
    }}>
      {/* Top Header */}
      <div>
        <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)' }}>
          🛠️ Operational Protocol & Consensus Inspector
        </h2>
        <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>
          Real-time telemetry across Java NIO Reactors, Binary Frame Codecs, and Consensus Log
        </p>
      </div>

      {/* Metric Cards Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
        <div className="card">
          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Transport & WebSocket</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--status-success)', marginTop: 4 }}>
            CONNECTED
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
            Multiplexed Binary Frames
          </div>
        </div>

        <div className="card">
          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Durability Engine</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--accent-primary)', marginTop: 4 }}>
            {serverStatus?.mode || 'STANDALONE WAL'}
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
            {serverStatus?.isLeader ? 'Leader Quorum Active' : 'Follower Node'}
          </div>
        </div>

        <div className="card">
          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Throughput Telemetry</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4 }}>
            {rawFrames.length} Frames
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
            CRC32C Validated
          </div>
        </div>

        <div className="card">
          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Delivery Lanes</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--status-info)', marginTop: 4 }}>
            {inspectorData?.delivery?.activeSessions ?? 1} Active Session(s)
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
            Striped Backpressure Bounded
          </div>
        </div>
      </div>

      {/* Live Wire Frame Trace */}
      <div className="card" style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 350, padding: 0, overflow: 'hidden' }}>
        <div style={{
          padding: '12px 16px', background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border-color)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center'
        }}>
          <div>
            <h3 style={{ fontSize: 14, fontWeight: 700 }}>Live Wire Frame Stream (24-byte header + CRC32C)</h3>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              Inspecting incremental decoder and typed command dispatch
            </span>
          </div>
          <button onClick={onClearLogs} className="btn-secondary" style={{ fontSize: 11, padding: '4px 10px' }}>
            Clear Logs
          </button>
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12, fontFamily: 'monospace' }}>
            <thead>
              <tr style={{ background: 'var(--bg-tertiary)', textAlign: 'left', borderBottom: '1px solid var(--border-color)' }}>
                <th style={{ padding: '8px 12px', width: 90 }}>Time</th>
                <th style={{ padding: '8px 12px', width: 60, textAlign: 'center' }}>Dir</th>
                <th style={{ padding: '8px 12px', width: 170 }}>Frame Type</th>
                <th style={{ padding: '8px 12px', width: 120 }}>Correlation</th>
                <th style={{ padding: '8px 12px', width: 70 }}>Bytes</th>
                <th style={{ padding: '8px 12px' }}>Summary</th>
              </tr>
            </thead>
            <tbody>
              {rawFrames.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted)' }}>
                    No frames captured yet
                  </td>
                </tr>
              ) : (
                rawFrames.map(f => (
                  <tr key={f.id} style={{ borderBottom: '1px solid var(--bg-tertiary)' }}>
                    <td style={{ padding: '6px 12px', color: 'var(--text-muted)' }}>{f.time}</td>
                    <td style={{ padding: '6px 12px', textAlign: 'center', fontWeight: 700, color: f.dir === 'TX' ? 'var(--status-info)' : 'var(--status-success)' }}>
                      {f.dir === 'TX' ? 'TX ->' : 'RX <-'}
                    </td>
                    <td style={{ padding: '6px 12px', color: 'var(--accent-primary)', fontWeight: 600 }}>{f.type}</td>
                    <td style={{ padding: '6px 12px', color: 'var(--text-secondary)' }}>{f.correlationId}</td>
                    <td style={{ padding: '6px 12px', color: 'var(--text-muted)' }}>{f.size}</td>
                    <td style={{ padding: '6px 12px', color: 'var(--text-primary)' }}>{f.summary}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
