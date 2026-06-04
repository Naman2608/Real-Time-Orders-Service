package com.realtime.orders.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Holds a single dedicated JDBC connection that issues LISTEN on the
 * 'orders_channel' channel.  When PostgreSQL fires a NOTIFY (triggered by
 * INSERT/UPDATE/DELETE on the orders table), this service receives the JSON
 * payload and broadcasts it over WebSocket to all subscribed clients.
 *
 * Why a dedicated connection?
 *   LISTEN state lives on a connection.  Using the HikariCP pool would lose
 *   the LISTEN registration whenever the connection is returned to the pool.
 */
@Service
public class PostgresListenerService {

    private static final Logger log = LoggerFactory.getLogger(PostgresListenerService.class);
    private static final String CHANNEL = "orders_channel";

    /** How long (ms) getNotifications() blocks before looping — not busy-polling */
    private static final int POLL_TIMEOUT_MS = 5_000;

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    private volatile boolean running = true;
    private Thread listenerThread;

    public PostgresListenerService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void start() {
        listenerThread = new Thread(this::listenLoop, "pg-notify-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        log.info("PostgreSQL LISTEN/NOTIFY listener started on channel '{}'", CHANNEL);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

    private void listenLoop() {
        while (running) {
            try (Connection conn = openListenerConnection()) {
                PGConnection pgConn = conn.unwrap(PGConnection.class);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LISTEN " + CHANNEL);
                }
                log.info("Listening on PostgreSQL channel '{}'", CHANNEL);

                while (running) {
                    // Blocks up to POLL_TIMEOUT_MS, then returns null — database wakes it up on NOTIFY
                    PGNotification[] notifications = pgConn.getNotifications(POLL_TIMEOUT_MS);
                    if (notifications != null) {
                        for (PGNotification n : notifications) {
                            handleNotification(n.getParameter());
                        }
                    }
                }

            } catch (Exception e) {
                if (running) {
                    log.error("Listener connection lost, reconnecting in 3s: {}", e.getMessage());
                    sleepQuietly(3_000);
                }
            }
        }
        log.info("PostgreSQL listener stopped");
    }

    private void handleNotification(String payload) {
        log.info("DB change received: {}", payload);
        messagingTemplate.convertAndSend("/topic/orders", payload);
    }

    private Connection openListenerConnection() throws Exception {
        return DriverManager.getConnection(datasourceUrl, datasourceUsername, datasourcePassword);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
