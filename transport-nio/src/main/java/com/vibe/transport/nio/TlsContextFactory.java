package com.vibe.transport.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Factory for creating configured TLS 1.3 / 1.2 SSLContext instances and generating
 * local development PKCS12 keystores.
 */
public final class TlsContextFactory {

    private static final Logger log = LoggerFactory.getLogger(TlsContextFactory.class);
    private static final String TLS_PROTOCOL = "TLSv1.3";

    private TlsContextFactory() {
    }

    /**
     * Creates an SSLContext from given KeyStore and TrustStore.
     */
    public static SSLContext createSSLContext(KeyStore keyStore, char[] keyPassword, KeyStore trustStore) {
        try {
            KeyManager[] keyManagers = null;
            if (keyStore != null) {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, keyPassword);
                keyManagers = kmf.getKeyManagers();
            }

            TrustManager[] trustManagers = null;
            if (trustStore != null) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                trustManagers = tmf.getTrustManagers();
            }

            SSLContext sslContext = SSLContext.getInstance(TLS_PROTOCOL);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSLContext with " + TLS_PROTOCOL, e);
        }
    }

    /**
     * Loads a PKCS12 KeyStore from a file path.
     */
    public static KeyStore loadKeyStore(Path keyStorePath, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(keyStorePath.toFile())) {
                ks.load(fis, password);
            }
            return ks;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load KeyStore from " + keyStorePath, e);
        }
    }

    /**
     * Generates a self-signed development PKCS12 keystore and truststore using JDK's keytool.
     *
     * @param targetDir directory where keystore.p12 and truststore.p12 will be created
     * @param password keystore password
     * @return generated server KeyStore
     */
    public static KeyStore generateDevKeyStore(Path targetDir, String password) {
        try {
            Files.createDirectories(targetDir);
            Path serverP12 = targetDir.resolve("server.p12");
            Path certFile = targetDir.resolve("server.cer");
            Path clientTrustP12 = targetDir.resolve("truststore.p12");

            if (Files.exists(serverP12)) {
                return loadKeyStore(serverP12, password.toCharArray());
            }

            // 1. Generate server keypair & certificate with SAN for localhost and 127.0.0.1
            runKeytool(
                    "-genkeypair",
                    "-alias", "vibe-server",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "365",
                    "-keystore", serverP12.toAbsolutePath().toString(),
                    "-storetype", "PKCS12",
                    "-storepass", password,
                    "-keypass", password,
                    "-dname", "CN=localhost, OU=Engineering, O=Vibe, L=San Francisco, ST=CA, C=US",
                    "-ext", "SAN=dns:localhost,ip:127.0.0.1"
            );

            // 2. Export certificate
            runKeytool(
                    "-exportcert",
                    "-alias", "vibe-server",
                    "-keystore", serverP12.toAbsolutePath().toString(),
                    "-storetype", "PKCS12",
                    "-storepass", password,
                    "-file", certFile.toAbsolutePath().toString()
            );

            // 3. Import certificate into client truststore
            runKeytool(
                    "-importcert",
                    "-noprompt",
                    "-alias", "vibe-server",
                    "-keystore", clientTrustP12.toAbsolutePath().toString(),
                    "-storetype", "PKCS12",
                    "-storepass", password,
                    "-file", certFile.toAbsolutePath().toString()
            );

            log.info("Generated development TLS certificates in {}", targetDir);
            return loadKeyStore(serverP12, password.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate dev TLS certificates", e);
        }
    }

    private static void runKeytool(String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String keytoolPath = Path.of(javaHome, "bin", "keytool").toString();
        String[] cmd = new String[args.length + 1];
        cmd[0] = keytoolPath;
        System.arraycopy(args, 0, cmd, 1, args.length);

        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new RuntimeException("keytool command failed (exit code " + exitCode + "): " + output);
        }
    }
}
