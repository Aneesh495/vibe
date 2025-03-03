package com.vibe.server.cas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentAddressedStoreTest {

    @TempDir
    Path tempDir;

    private ContentAddressedStore cas;

    @BeforeEach
    void setUp() throws IOException {
        cas = new ContentAddressedStore(tempDir, 2);
    }

    @AfterEach
    void tearDown() {
        cas.close();
    }

    @Test
    void testChunkedUploadAndSha256Verification() throws Exception {
        UUID uploadId = UUID.randomUUID();
        byte[] chunk0 = new byte[ContentAddressedStore.CHUNK_SIZE];
        Arrays.fill(chunk0, (byte) 'A');
        byte[] chunk1 = "Write ahead logs ensure crash recovery and durable commits.".getBytes();

        cas.startSession(uploadId, 2, chunk0.length + chunk1.length);

        CompletableFuture<String> f0 = cas.writeChunk(uploadId, 0, chunk0);
        assertThat(f0.get()).isNull(); // In progress

        CompletableFuture<String> f1 = cas.writeChunk(uploadId, 1, chunk1);
        String hash = f1.get(); // Completed
        assertThat(hash).isNotNull().hasSize(64);

        assertThat(cas.hasBlob(hash)).isTrue();
        Path blobPath = cas.getBlobPath(hash);
        assertThat(Files.exists(blobPath)).isTrue();
        assertThat(Files.size(blobPath)).isEqualTo(chunk0.length + chunk1.length);
    }

    @Test
    void testDeduplicationOfIdenticalBlobs() throws Exception {
        UUID upload1 = UUID.randomUUID();
        UUID upload2 = UUID.randomUUID();
        byte[] data = "Identical payload content to test CAS deduplication.".getBytes();

        cas.startSession(upload1, 1, data.length);
        String hash1 = cas.writeChunk(upload1, 0, data).get();

        cas.startSession(upload2, 1, data.length);
        String hash2 = cas.writeChunk(upload2, 0, data).get();

        assertThat(hash1).isEqualTo(hash2);
        assertThat(cas.hasBlob(hash1)).isTrue();
    }

    @Test
    void testOversizedUploadRejected() {
        UUID uploadId = UUID.randomUUID();
        long oversized = 60 * 1024 * 1024L; // 60 MiB exceeds 50 MiB limit

        assertThatThrownBy(() -> cas.startSession(uploadId, 100, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }
}
