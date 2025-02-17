/**
 * CRC32C (Castagnoli) checksum implementation matching Java's java.util.zip.CRC32C.
 * Uses polynomial 0x82F63B78 with bit-reflection.
 */
const CRC32C_TABLE = new Uint32Array(256);

// Precompute lookup table
for (let i = 0; i < 256; i++) {
  let crc = i;
  for (let j = 0; j < 8; j++) {
    crc = (crc & 1) !== 0 ? (crc >>> 1) ^ 0x82F63B78 : crc >>> 1;
  }
  CRC32C_TABLE[i] = crc >>> 0;
}

export class CRC32C {
  private crc = 0xFFFFFFFF;

  public update(bytes: Uint8Array, offset = 0, length = bytes.length): void {
    let c = this.crc;
    const end = offset + length;
    for (let i = offset; i < end; i++) {
      c = (c >>> 8) ^ CRC32C_TABLE[(c ^ bytes[i]) & 0xFF];
    }
    this.crc = c;
  }

  public getValue(): number {
    return (~this.crc >>> 0);
  }

  public reset(): void {
    this.crc = 0xFFFFFFFF;
  }

  public static compute(bytes: Uint8Array, offset = 0, length = bytes.length): number {
    const c = new CRC32C();
    c.update(bytes, offset, length);
    return c.getValue();
  }
}
