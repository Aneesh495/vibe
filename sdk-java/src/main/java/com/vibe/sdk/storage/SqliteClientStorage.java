package com.vibe.sdk.storage;

import com.vibe.domain.entity.ConversationType;
import com.vibe.protocol.payload.CommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite-backed local cache and durable outbox for the Vibe Client SDK.
 */
public final class SqliteClientStorage implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SqliteClientStorage.class);

    private final Path dbPath;
    private final Connection connection;

    public SqliteClientStorage(Path storageDir, String databaseName) throws SQLException, IOException {
        Files.createDirectories(storageDir);
        this.dbPath = storageDir.resolve(databaseName + ".db");
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        this.connection = DriverManager.getConnection(url);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }

        initSchema();
        log.info("Initialized SQLite client storage at {}", dbPath);
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversations (
                    conversation_id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_seq INTEGER NOT NULL DEFAULT 0,
                    unread_count INTEGER NOT NULL DEFAULT 0
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    message_id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    sender_id TEXT NOT NULL,
                    seq_number INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    attachment_id TEXT,
                    attachment_name TEXT,
                    attachment_size INTEGER,
                    FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE
                );
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_messages_conv_seq
                ON messages(conversation_id, seq_number);
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS outbox (
                    client_message_id TEXT PRIMARY KEY,
                    command_type TEXT NOT NULL,
                    conversation_id TEXT,
                    payload BLOB NOT NULL,
                    created_at INTEGER NOT NULL,
                    retry_count INTEGER NOT NULL DEFAULT 0
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contacts (
                    user_id TEXT PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE,
                    display_name TEXT NOT NULL,
                    bio TEXT,
                    avatar_url TEXT,
                    is_friend INTEGER NOT NULL DEFAULT 0,
                    is_blocked INTEGER NOT NULL DEFAULT 0
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_state (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
            """);
        }
    }

    // --- Conversations ---

    public synchronized void saveConversation(StoredConversation conv) throws SQLException {
        String sql = """
            INSERT INTO conversations (conversation_id, type, title, created_at, last_seq, unread_count)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(conversation_id) DO UPDATE SET
                title = excluded.title,
                last_seq = MAX(conversations.last_seq, excluded.last_seq),
                unread_count = excluded.unread_count;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, conv.conversationId().toString());
            ps.setString(2, conv.type().name());
            ps.setString(3, conv.title());
            ps.setLong(4, conv.createdAt());
            ps.setLong(5, conv.lastSeq());
            ps.setInt(6, conv.unreadCount());
            ps.executeUpdate();
        }
    }

    public synchronized Optional<StoredConversation> getConversation(UUID convId) throws SQLException {
        String sql = "SELECT * FROM conversations WHERE conversation_id = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, convId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapConversation(rs));
                }
            }
        }
        return Optional.empty();
    }

    public synchronized List<StoredConversation> getAllConversations() throws SQLException {
        List<StoredConversation> list = new ArrayList<>();
        String sql = "SELECT * FROM conversations ORDER BY last_seq DESC;";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapConversation(rs));
            }
        }
        return list;
    }

    public synchronized void updateConversationLastSeq(UUID convId, long lastSeq) throws SQLException {
        String sql = "UPDATE conversations SET last_seq = MAX(last_seq, ?) WHERE conversation_id = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, lastSeq);
            ps.setString(2, convId.toString());
            ps.executeUpdate();
        }
    }

    // --- Messages ---

    public synchronized void saveMessage(StoredMessage msg) throws SQLException {
        String sql = """
            INSERT INTO messages (
                message_id, conversation_id, sender_id, seq_number, content,
                timestamp, status, attachment_id, attachment_name, attachment_size
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(message_id) DO UPDATE SET
                seq_number = MAX(messages.seq_number, excluded.seq_number),
                status = excluded.status;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, msg.messageId().toString());
            ps.setString(2, msg.conversationId().toString());
            ps.setString(3, msg.senderId());
            ps.setLong(4, msg.seqNumber());
            ps.setString(5, msg.content());
            ps.setLong(6, msg.timestamp());
            ps.setString(7, msg.status().name());
            ps.setString(8, msg.attachmentId() != null ? msg.attachmentId().toString() : null);
            ps.setString(9, msg.attachmentName());
            ps.setLong(10, msg.attachmentSize());
            ps.executeUpdate();
        }
    }

    public synchronized List<StoredMessage> getMessages(UUID convId, int limit, long beforeSeq) throws SQLException {
        List<StoredMessage> list = new ArrayList<>();
        String sql = beforeSeq > 0
                ? "SELECT * FROM messages WHERE conversation_id = ? AND seq_number < ? ORDER BY seq_number DESC LIMIT ?;"
                : "SELECT * FROM messages WHERE conversation_id = ? ORDER BY seq_number DESC LIMIT ?;";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, convId.toString());
            if (beforeSeq > 0) {
                ps.setLong(2, beforeSeq);
                ps.setInt(3, limit);
            } else {
                ps.setInt(2, limit);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMessage(rs));
                }
            }
        }
        return list;
    }

    public synchronized void updateMessageStatus(UUID msgId, MessageDeliveryStatus status) throws SQLException {
        String sql = "UPDATE messages SET status = ? WHERE message_id = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, msgId.toString());
            ps.executeUpdate();
        }
    }

    // --- Outbox ---

    public synchronized void enqueueOutbox(OutboxItem item) throws SQLException {
        String sql = """
            INSERT INTO outbox (client_message_id, command_type, conversation_id, payload, created_at, retry_count)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(client_message_id) DO UPDATE SET
                retry_count = outbox.retry_count + 1;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, item.clientMessageId());
            ps.setString(2, item.commandType().name());
            ps.setString(3, item.conversationId() != null ? item.conversationId().toString() : null);
            ps.setBytes(4, item.payload());
            ps.setLong(5, item.createdAt());
            ps.setInt(6, item.retryCount());
            ps.executeUpdate();
        }
    }

    public synchronized List<OutboxItem> getPendingOutbox() throws SQLException {
        List<OutboxItem> list = new ArrayList<>();
        String sql = "SELECT * FROM outbox ORDER BY created_at ASC;";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String convIdStr = rs.getString("conversation_id");
                UUID convId = convIdStr != null ? UUID.fromString(convIdStr) : null;
                list.add(new OutboxItem(
                        rs.getString("client_message_id"),
                        CommandType.valueOf(rs.getString("command_type")),
                        convId,
                        rs.getBytes("payload"),
                        rs.getLong("created_at"),
                        rs.getInt("retry_count")
                ));
            }
        }
        return list;
    }

    public synchronized void removeOutbox(String clientMessageId) throws SQLException {
        String sql = "DELETE FROM outbox WHERE client_message_id = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, clientMessageId);
            ps.executeUpdate();
        }
    }

    public synchronized void incrementOutboxRetry(String clientMessageId) throws SQLException {
        String sql = "UPDATE outbox SET retry_count = retry_count + 1 WHERE client_message_id = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, clientMessageId);
            ps.executeUpdate();
        }
    }

    // --- Contacts ---

    public synchronized void saveContact(StoredContact contact) throws SQLException {
        String sql = """
            INSERT INTO contacts (user_id, username, display_name, bio, avatar_url, is_friend, is_blocked)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                username = excluded.username,
                display_name = excluded.display_name,
                bio = excluded.bio,
                avatar_url = excluded.avatar_url,
                is_friend = excluded.is_friend,
                is_blocked = excluded.is_blocked;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, contact.userId());
            ps.setString(2, contact.username());
            ps.setString(3, contact.displayName());
            ps.setString(4, contact.bio());
            ps.setString(5, contact.avatarUrl());
            ps.setInt(6, contact.isFriend() ? 1 : 0);
            ps.setInt(7, contact.isBlocked() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public synchronized List<StoredContact> getAllContacts() throws SQLException {
        List<StoredContact> list = new ArrayList<>();
        String sql = "SELECT * FROM contacts ORDER BY username ASC;";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new StoredContact(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("bio"),
                        rs.getString("avatar_url"),
                        rs.getInt("is_friend") == 1,
                        rs.getInt("is_blocked") == 1
                ));
            }
        }
        return list;
    }

    // --- Sync State ---

    public synchronized void setSyncState(String key, String value) throws SQLException {
        String sql = """
            INSERT INTO sync_state (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public synchronized Optional<String> getSyncState(String key) throws SQLException {
        String sql = "SELECT value FROM sync_state WHERE key = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("value"));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            log.info("Closed SQLite client storage at {}", dbPath);
        }
    }

    private StoredConversation mapConversation(ResultSet rs) throws SQLException {
        return new StoredConversation(
                UUID.fromString(rs.getString("conversation_id")),
                ConversationType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getLong("created_at"),
                rs.getLong("last_seq"),
                rs.getInt("unread_count")
        );
    }

    private StoredMessage mapMessage(ResultSet rs) throws SQLException {
        String attIdStr = rs.getString("attachment_id");
        UUID attId = attIdStr != null ? UUID.fromString(attIdStr) : null;
        return new StoredMessage(
                UUID.fromString(rs.getString("message_id")),
                UUID.fromString(rs.getString("conversation_id")),
                rs.getString("sender_id"),
                rs.getLong("seq_number"),
                rs.getString("content"),
                rs.getLong("timestamp"),
                MessageDeliveryStatus.valueOf(rs.getString("status")),
                attId,
                rs.getString("attachment_name"),
                rs.getLong("attachment_size")
        );
    }
}
