# Vibe social network

```mermaid
sequenceDiagram
  participant C as SocialClient Swing UI
  participant S as SocialServer
  participant D as Database/Data files
  C->>S: TCP commands auth message friend block
  S->>D: read write text persistence
  S-->>C: structured responses
```

## Components

| Module | Responsibility |
| --- | --- |
| `SocialServer.java` | Socket listener, command dispatch, persistence |
| `SocialClient.java` | Swing UI, chat threads, profile editor |
| `Database/Data/` | Append-friendly text stores for users, friends, messages |
| `ServerException/` | Typed errors surfaced to the client |

The design favors explicit command strings over HTTP so the project stays a
compact illustration of client/server state machines and file-backed storage
without pulling in a database server.
