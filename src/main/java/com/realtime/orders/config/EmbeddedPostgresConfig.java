package com.realtime.orders.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Activated only when Spring profile 'embedded' is set (the default).
 * Starts a real PostgreSQL instance inside the JVM — no external installation needed.
 * Binaries are downloaded once (~50 MB) and cached in ~/.embedpostgresql/.
 */
@Configuration
@Profile("embedded")
public class EmbeddedPostgresConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfig.class);
    private static final int    PG_PORT = 5433;
    private static final String DB_NAME = "orders_db";

    private EmbeddedPostgres embeddedPostgres;

    @Bean
    public EmbeddedPostgres embeddedPostgres() throws Exception {
        log.info("=================================================");
        log.info("  Starting embedded PostgreSQL on port {}...", PG_PORT);
        log.info("  (first run downloads ~50 MB, then cached)");
        log.info("=================================================");

        embeddedPostgres = EmbeddedPostgres.builder()
                .setPort(PG_PORT)
                .start();

        createDatabase();

        log.info("Embedded PostgreSQL ready — jdbc:postgresql://localhost:{}/{}", PG_PORT, DB_NAME);
        return embeddedPostgres;
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(EmbeddedPostgres pg) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://localhost:" + PG_PORT + "/" + DB_NAME);
        cfg.setUsername("postgres");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        return new HikariDataSource(cfg);
    }

    @PreDestroy
    public void stopPostgres() throws Exception {
        if (embeddedPostgres != null) {
            log.info("Stopping embedded PostgreSQL...");
            embeddedPostgres.close();
        }
    }

    private void createDatabase() {
        try (Connection conn = embeddedPostgres.getPostgresDatabase().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + DB_NAME);
            log.info("Created database '{}'", DB_NAME);
        } catch (Exception e) {
            log.debug("Database '{}' already exists", DB_NAME);
        }
    }
}
