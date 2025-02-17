import test from 'node:test';
import assert from 'node:assert';
import { Frame, FrameDecoder, FrameEncoder, MAGIC } from '../src/protocol/frame.js';
import { FrameType } from '../src/protocol/types.js';

test('Frame encode and decode roundtrip', () => {
  const payload = new TextEncoder().encode('Hello Vibe Binary Protocol!');
  const frame = Frame.create(FrameType.COMMAND, 1001n, payload);

  assert.strictEqual(frame.header.magic, MAGIC);
  assert.strictEqual(frame.header.type, FrameType.COMMAND);
  assert.strictEqual(frame.header.correlationId, 1001n);
  assert.strictEqual(frame.header.payloadLength, payload.length);
  assert.strictEqual(frame.verifyChecksum(), true);

  const encoded = FrameEncoder.encode(frame);
  assert.strictEqual(encoded.length, 24 + payload.length);

  const decoder = new FrameDecoder();
  decoder.append(encoded);
  const decoded = decoder.nextFrame();

  assert.notStrictEqual(decoded, null);
  assert.strictEqual(decoded!.header.type, FrameType.COMMAND);
  assert.strictEqual(decoded!.header.correlationId, 1001n);
  assert.strictEqual(decoded!.header.payloadLength, payload.length);
  assert.strictEqual(new TextDecoder().decode(decoded!.payload), 'Hello Vibe Binary Protocol!');
});

test('Incremental chunk decoding handles split packets', () => {
  const payload = new TextEncoder().encode('Streaming across TCP packet boundaries');
  const frame = Frame.create(FrameType.CONVERSATION_EVENT, 42n, payload);
  const encoded = FrameEncoder.encode(frame);

  const decoder = new FrameDecoder();

  // Feed 10 bytes at a time
  let decoded: Frame | null = null;
  for (let i = 0; i < encoded.length; i += 10) {
    const chunk = encoded.slice(i, Math.min(i + 10, encoded.length));
    decoder.append(chunk);
    const f = decoder.nextFrame();
    if (f) {
      decoded = f;
    }
  }

  assert.notStrictEqual(decoded, null);
  assert.strictEqual(decoded!.header.correlationId, 42n);
  assert.strictEqual(new TextDecoder().decode(decoded!.payload), 'Streaming across TCP packet boundaries');
});
