import test from 'node:test';
import assert from 'node:assert';
import {
  decodeAuthResult,
  decodeCommandResult,
  decodeError,
  encodeAcknowledge,
  encodeAuthenticate,
  encodeCommand,
  encodeHandshake,
  encodeHeartbeat,
  encodeSubscribe,
} from '../src/protocol/payloads.js';
import { CommandType, ErrorCode } from '../src/protocol/types.js';

test('Handshake, Authenticate, and Error payload codecs', () => {
  // Handshake
  const hsBytes = encodeHandshake({
    clientVersionNumber: 1,
    clientVersionString: 'vibe-ts-test',
    deviceId: 'dev-node-1',
    flags: 0,
  });
  assert.ok(hsBytes.length > 0);

  // Authenticate
  const authBytes = encodeAuthenticate({
    authMethod: 0x01,
    username: 'alice',
    credential: 'password123',
    deviceId: 'dev-node-1',
  });
  assert.ok(authBytes.length > 0);

  // Command
  const cmdBytes = encodeCommand({
    commandType: CommandType.SEND_MESSAGE,
    clientMessageId: 'msg-1',
    conversationId: '12345678-1234-1234-1234-123456789abc',
    body: new Uint8Array([1, 2, 3]),
  });
  assert.ok(cmdBytes.length > 0);

  // Subscribe
  const subBytes = encodeSubscribe({
    conversationId: '12345678-1234-1234-1234-123456789abc',
    lastKnownSeq: 10n,
    limit: 50,
  });
  assert.strictEqual(subBytes.length, 16 + 8 + 4);

  // Acknowledge
  const ackBytes = encodeAcknowledge({
    conversationId: '12345678-1234-1234-1234-123456789abc',
    ackType: 0x01,
    upToSeq: 25n,
    ackTimestamp: 1720000000000n,
  });
  assert.strictEqual(ackBytes.length, 16 + 1 + 8 + 8);
});
