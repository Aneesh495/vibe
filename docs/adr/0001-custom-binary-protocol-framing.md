# ADR 0001: Custom Binary Protocol Framing and Integrity Verification

## Status
Accepted

## Context
The legacy application utilized a delimiter-delimited text wire format (`SocialServer.java:637`), where newlines and semicolons delimited fields. This protocol suffered from several critical vulnerabilities:
1. Field injection: messages containing delimiters corrupted frame boundaries or injected arbitrary command arguments.
2. Incomplete frames: TCP stream fragmentation was handled by blocking `readLine()` calls rather than stateful framing.
3. Lack of payload integrity verification: network bit rot or corrupted socket transfers passed unnoticed.
4. Unbounded payload parsing: lack of an upfront header size check exposed the server to denial-of-service memory exhaustion.

## Decision
We implemented a compact 24-byte binary wire frame protocol with upfront size declaration, typed frames, and CRC32C checksum verification.

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Magic (0x56494245 "VIBE")                            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   Version     |   Frame Type  |             Flags             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   Correlation ID (Part 1, High 32)            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   Correlation ID (Part 2, Low 32)             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Payload Length (Bytes)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Header CRC32C Checksum                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Payload Bytes (Variable)                |
|                              ...                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Key Technical Aspects
1. **Magic Number Verification**: Incoming connections must begin with `0x56494245` ("VIBE"); invalid connections are rejected immediately before allocating payload buffers.
2. **Hardware-Accelerated CRC32C**: The 4-byte CRC32C validates the 20 preceding header bytes using Castagnoli polynomial routines supported by hardware intrinsics on modern x86/ARM processors.
3. **Incremental Non-Blocking Decoder**: `FrameDecoder` is a stateful state machine (`READING_HEADER` vs `READING_PAYLOAD`) that accumulates partial chunks without copying until a complete frame is available.
4. **Bounded Frame Sizing**: Maximum frame payload is hard-limited to 16 MiB (`ProtocolConstants.MAX_FRAME_SIZE`). Oversized frames trigger immediate socket termination.

## Consequences
- Positive: Immune to payload delimiter injection; handles arbitrary binary data and unicode without escaping overhead.
- Positive: Low memory allocation footprint via direct `ByteBuffer` slices.
- Negative: Browser clients cannot speak raw TCP directly; requires a lightweight WebSocket RFC 6455 translation bridge (`WebSocketBridge.java`).
