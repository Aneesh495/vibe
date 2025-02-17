package com.vibe.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Main application CLI entry point for launching Vibe servers.
 */
public final class VibeServerMain {

    private static final Logger log = LoggerFactory.getLogger(VibeServerMain.class);

    public static void main(String[] args) {
        VibeServerConfig.Builder configBuilder = VibeServerConfig.builder();
        Path legacyImportPath = null;

        for (String arg : args) {
            if (arg.startsWith("--mode=")) {
                String modeStr = arg.substring("--mode=".length()).toUpperCase();
                configBuilder.mode(VibeServerConfig.ServerMode.valueOf(modeStr));
            } else if (arg.startsWith("--tcp-port=")) {
                configBuilder.tcpPort(Integer.parseInt(arg.substring("--tcp-port=".length())));
            } else if (arg.startsWith("--ws-port=")) {
                configBuilder.wsPort(Integer.parseInt(arg.substring("--ws-port=".length())));
            } else if (arg.equals("--tls")) {
                configBuilder.tlsEnabled(true);
            } else if (arg.startsWith("--storage-dir=")) {
                configBuilder.storageDir(Paths.get(arg.substring("--storage-dir=".length())));
            } else if (arg.startsWith("--node-id=")) {
                configBuilder.nodeId(arg.substring("--node-id=".length()));
            } else if (arg.startsWith("--peers=")) {
                List<String> peers = Arrays.asList(arg.substring("--peers=".length()).split(","));
                configBuilder.clusterPeers(peers);
            } else if (arg.startsWith("--import-legacy=")) {
                legacyImportPath = Paths.get(arg.substring("--import-legacy=".length()));
            }
        }

        VibeServerConfig config = configBuilder.build();
        try {
            VibeServer server = new VibeServer(config);
            server.start();

            if (legacyImportPath != null) {
                log.info("Executing legacy data import from: {}", legacyImportPath);
                LegacyDataImporter importer = new LegacyDataImporter(server.durabilityAdapter());
                LegacyDataImporter.ImportReport report = importer.importData(legacyImportPath);
                log.info("Import report: {} users, {} friendships, {} messages",
                        report.usersImported(), report.friendshipsImported(), report.messagesImported());
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered; terminating VibeServer...");
                try {
                    server.close();
                } catch (Exception e) {
                    log.error("Error during server shutdown", e);
                }
            }, "vibe-shutdown-hook"));

            log.info("VibeServer is fully operational and awaiting connections.");
        } catch (Exception e) {
            log.error("Fatal error starting VibeServer", e);
            System.exit(1);
        }
    }
}
