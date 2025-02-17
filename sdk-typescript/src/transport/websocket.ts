import { Frame, FrameDecoder, FrameEncoder } from '../protocol/frame.js';

export type FrameHandler = (frame: Frame) => void;
export type StateChangeHandler = (state: 'CONNECTING' | 'OPEN' | 'CLOSING' | 'CLOSED') => void;

export interface TransportMetrics {
  txFrames: number;
  rxFrames: number;
  txBytes: number;
  rxBytes: number;
  connectedAt: number;
}

export class VibeWebSocketTransport {
  private ws: WebSocket | null = null;
  private readonly decoder = new FrameDecoder();
  private reconnectAttempt = 0;
  private shouldReconnect = true;
  private reconnectTimer: any = null;

  public readonly metrics: TransportMetrics = {
    txFrames: 0,
    rxFrames: 0,
    txBytes: 0,
    rxBytes: 0,
    connectedAt: 0,
  };

  constructor(
    public readonly url: string,
    private readonly onFrame: FrameHandler,
    private readonly onStateChange: StateChangeHandler
  ) {}

  public connect(): void {
    this.shouldReconnect = true;
    this.initSocket();
  }

  private initSocket(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.onStateChange('CONNECTING');
    try {
      this.ws = new WebSocket(this.url);
      this.ws.binaryType = 'arraybuffer';

      this.ws.onopen = () => {
        this.reconnectAttempt = 0;
        this.metrics.connectedAt = Date.now();
        this.onStateChange('OPEN');
      };

      this.ws.onmessage = (event: MessageEvent) => {
        if (event.data instanceof ArrayBuffer) {
          const bytes = new Uint8Array(event.data);
          this.metrics.rxBytes += bytes.length;
          this.decoder.append(bytes);

          let frame: Frame | null;
          while ((frame = this.decoder.nextFrame()) !== null) {
            this.metrics.rxFrames++;
            this.onFrame(frame);
          }
        }
      };

      this.ws.onclose = () => {
        this.onStateChange('CLOSED');
        this.decoder.reset();
        if (this.shouldReconnect) {
          this.scheduleReconnect();
        }
      };

      this.ws.onerror = (err) => {
        console.warn('Vibe WebSocket error:', err);
      };
    } catch (e) {
      console.error('Failed to create WebSocket:', e);
      if (this.shouldReconnect) {
        this.scheduleReconnect();
      }
    }
  }

  public send(frame: Frame): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return false;
    }

    try {
      const bytes = FrameEncoder.encode(frame);
      this.ws.send(bytes.buffer);
      this.metrics.txFrames++;
      this.metrics.txBytes += bytes.length;
      return true;
    } catch (e) {
      console.error('Error sending frame:', e);
      return false;
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;
    this.reconnectAttempt++;
    const delay = Math.min(500 * Math.pow(1.5, this.reconnectAttempt - 1), 15000);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.initSocket();
    }, delay);
  }

  public close(): void {
    this.shouldReconnect = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.onStateChange('CLOSED');
  }

  public isOpen(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }
}
