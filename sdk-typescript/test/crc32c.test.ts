import test from 'node:test';
import assert from 'node:assert';
import { CRC32C } from '../src/protocol/crc32c.js';

test('CRC32C computes correct standard Castagnoli checksums', () => {
  // Empty
  assert.strictEqual(CRC32C.compute(new Uint8Array(0)), 0);

  // Standard "123456789" vector for CRC32C is 0xE3069283
  const input = new TextEncoder().encode('123456789');
  const crc = CRC32C.compute(input);
  assert.strictEqual(crc, 0xE3069283);
});
