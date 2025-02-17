import { CRC32C } from './crc32c.js';
import { FrameFlags, FrameType } from './types.js';

export const MAGIC = 0x56494245; // "VIBE"
export const CURRENT_VERSION = 1;
export const HEADER_LENGTH = 24;
export const MAX_PAYLOAD_LENGTH = 16 * 1024 * 1024; // 16 MiB

export interface FrameHeader {
  magic: number;
  version: number;
  type: FrameType;
  flags: number;
  correlationId: bigint;
  payloadLength: number;
  checksumCRC32C: number;
}

export class Frame {
  constructor(
    public readonly header: FrameHeader,
    public readonly payload: Uint8Array
  ) {}

  public static create(
    type: FrameType,
    correlationId: bigint | number,
    payload?: Uint8Array,
    flags = FrameFlags.NONE
  ): Frame {
    const body = payload || new Uint8Array(0);
    const crc = body.length > 0 ? CRC32C.compute(body) : 0;
    const cid = typeof correlationId === 'number' ? BigInt(correlationId) : correlationId;

    const header: FrameHeader = {
      magic: MAGIC,
      version: CURRENT_VERSION,
      type,
      flags,
      correlationId: cid,
      payloadLength: body.length,
      checksumCRC32C: crc,
    };

    return new Frame(header, body);
  }

  public verifyChecksum(): boolean {
    if (this.payload.length === 0) {
      return this.header.checksumCRC32C === 0;
    }
    const computed = CRC32C.compute(this.payload);
    return computed === this.header.checksumCRC32C;
  }
}

export class FrameEncoder {
  public static encode(frame: Frame): Uint8Array {
    const totalSize = HEADER_LENGTH + frame.payload.length;
    const buffer = new ArrayBuffer(totalSize);
    const view = new DataView(buffer);
    const uint8 = new Uint8Array(buffer);

    view.setUint32(0, frame.header.magic, false);
    view.setUint8(4, frame.header.version);
    view.setUint8(5, frame.header.type);
    view.setUint16(6, frame.header.flags, false);
    view.setBigInt64(8, frame.header.correlationId, false);
    view.setUint32(16, frame.header.payloadLength, false);
    view.setUint32(20, frame.header.checksumCRC32C, false);

    if (frame.payload.length > 0) {
      uint8.set(frame.payload, HEADER_LENGTH);
    }

    return uint8;
  }
}

export class FrameDecoder {
  private buffer: Uint8Array = new Uint8Array(0);

  public append(data: Uint8Array): void {
    if (this.buffer.length === 0) {
      this.buffer = data;
    } else {
      const merged = new Uint8Array(this.buffer.length + data.length);
      merged.set(this.buffer, 0);
      merged.set(data, this.buffer.length);
      this.buffer = merged;
    }
  }

  public nextFrame(): Frame | null {
    if (this.buffer.length < HEADER_LENGTH) {
      return null;
    }

    const view = new DataView(this.buffer.buffer, this.buffer.byteOffset, this.buffer.byteLength);
    const magic = view.getUint32(0, false);
    if (magic !== MAGIC) {
      throw new Error(`Invalid wire magic: 0x${magic.toString(16)} (expected 0x${MAGIC.toString(16)})`);
    }

    const version = view.getUint8(4);
    if (version !== CURRENT_VERSION) {
      throw new Error(`Unsupported protocol version: ${version}`);
    }

    const type = view.getUint8(5) as FrameType;
    const flags = view.getUint16(6, false);
    const correlationId = view.getBigInt64(8, false);
    const payloadLength = view.getUint32(16, false);
    const checksumCRC32C = view.getUint32(20, false);

    if (payloadLength > MAX_PAYLOAD_LENGTH) {
      throw new Error(`Payload length ${payloadLength} exceeds maximum ${MAX_PAYLOAD_LENGTH}`);
    }

    const totalNeeded = HEADER_LENGTH + payloadLength;
    if (this.buffer.length < totalNeeded) {
      return null; // Awaiting more chunks
    }

    const payload = this.buffer.slice(HEADER_LENGTH, totalNeeded);
    this.buffer = this.buffer.slice(totalNeeded);

    const header: FrameHeader = {
      magic,
      version,
      type,
      flags,
      correlationId,
      payloadLength,
      checksumCRC32C,
    };

    const frame = new Frame(header, payload);
    if (!frame.verifyChecksum()) {
      throw new Error(`CRC32C mismatch on frame type 0x${type.toString(16)}: header 0x${checksumCRC32C.toString(16)} != computed 0x${CRC32C.compute(payload).toString(16)}`);
    }

    return frame;
  }

  public reset(): void {
    this.buffer = new Uint8Array(0);
  }
}
