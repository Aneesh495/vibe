package com.vibe.transport.nio;

import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NioTlsTransportTest {

    private NioServerTransport server;
    private NioClientTransport clientTransport;

    @AfterEach
    void tearDown() {
        if (clientTransport != null) {
            clientTransport.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testTlsHandshakeAndSecureFrameEcho(@TempDir Path tempDir) throws Exception {
        String password = "vibe-test-password";
        KeyStore serverKeyStore = TlsContextFactory.generateDevKeyStore(tempDir, password);
        Path trustStorePath = tempDir.resolve("truststore.p12");
        KeyStore clientTrustStore = TlsContextFactory.loadKeyStore(trustStorePath, password.toCharArray());

        SSLContext serverSsl = TlsContextFactory.createSSLContext(serverKeyStore, password.toCharArray(), null);
        SSLContext clientSsl = TlsContextFactory.createSSLContext(null, null, clientTrustStore);

        BlockingQueue<Frame> serverReceived = new LinkedBlockingQueue<>();
        BlockingQueue<Frame> clientReceived = new LinkedBlockingQueue<>();

        server = new NioServerTransport(
                "127.0.0.1",
                0,
                2,
                (conn, frame) -> {
                    serverReceived.offer(frame);
                    conn.send(Frame.create(FrameType.COMMAND_RESULT, frame.correlationId() + 500, frame.payload()));
                },
                null,
                serverSsl,
                ForkJoinPool.commonPool()
        );
        server.start();

        clientTransport = new NioClientTransport(clientSsl, ForkJoinPool.commonPool());
        clientTransport.start();

        NioConnection clientConn = clientTransport.connect(
                "127.0.0.1",
                server.boundPort(),
                (conn, frame) -> clientReceived.offer(frame),
                null
        ).get(5, TimeUnit.SECONDS);

        assertThat(clientConn).isNotNull();

        byte[] securePayload = "Encrypted TLS 1.3 payload".getBytes(StandardCharsets.UTF_8);
        clientConn.send(Frame.create(FrameType.COMMAND, 777L, ByteBuffer.wrap(securePayload)));

        Frame sFrame = serverReceived.poll(5, TimeUnit.SECONDS);
        assertThat(sFrame).isNotNull();
        assertThat(sFrame.correlationId()).isEqualTo(777L);

        Frame cFrame = clientReceived.poll(5, TimeUnit.SECONDS);
        assertThat(cFrame).isNotNull();
        assertThat(cFrame.correlationId()).isEqualTo(1277L);

        byte[] cPayload = new byte[cFrame.payloadLength()];
        cFrame.payload().get(cPayload);
        assertThat(cPayload).isEqualTo(securePayload);
    }

    @Test
    void testUntrustedCertificateRejection(@TempDir Path tempDir, @TempDir Path otherTempDir) throws Exception {
        String password = "vibe-test-password";
        KeyStore serverKeyStore = TlsContextFactory.generateDevKeyStore(tempDir, password);

        // Generate a completely different, unrelated CA/truststore
        TlsContextFactory.generateDevKeyStore(otherTempDir, password);
        Path otherTrustStorePath = otherTempDir.resolve("truststore.p12");
        KeyStore untrustedStore = TlsContextFactory.loadKeyStore(otherTrustStorePath, password.toCharArray());

        SSLContext serverSsl = TlsContextFactory.createSSLContext(serverKeyStore, password.toCharArray(), null);
        SSLContext untrustedClientSsl = TlsContextFactory.createSSLContext(null, null, untrustedStore);

        server = new NioServerTransport(
                "127.0.0.1",
                0,
                1,
                (conn, frame) -> {},
                null,
                serverSsl,
                ForkJoinPool.commonPool()
        );
        server.start();

        clientTransport = new NioClientTransport(untrustedClientSsl, ForkJoinPool.commonPool());
        clientTransport.start();

        CompletableFuture<NioConnection> connFuture = clientTransport.connect(
                "127.0.0.1",
                server.boundPort(),
                (conn, frame) -> {},
                null
        );

        // Untrusted cert should fail connection
        assertThatThrownBy(() -> connFuture.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }
}
