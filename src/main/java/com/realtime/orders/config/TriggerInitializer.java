package com.realtime.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Runs after the application context is fully started.
 * Creates the PostgreSQL trigger function and trigger programmatically.
 *
 * We do this in Java rather than schema.sql because Spring Boot's SQL script runner
 * splits on semicolons, which breaks dollar-quoted PL/pgSQL function bodies ($$...$$).
 */
@Component
public class TriggerInitializer {

    private static final Logger log = LoggerFactory.getLogger(TriggerInitializer.class);

    private final DataSource dataSource;

    public TriggerInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void installTrigger() {
        log.info("Installing orders change trigger...");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(CREATE_FUNCTION_SQL);
            stmt.execute("DROP TRIGGER IF EXISTS orders_change_trigger ON orders");
            stmt.execute(CREATE_TRIGGER_SQL);

            log.info("Trigger installed successfully on 'orders' table");
        } catch (Exception e) {
            log.error("Failed to install trigger: {}", e.getMessage(), e);
            throw new RuntimeException("Could not install orders trigger", e);
        }
    }

    // Using $func$...$func$ alternate quoting to avoid any nesting conflicts
    private static final String CREATE_FUNCTION_SQL = """
            CREATE OR REPLACE FUNCTION notify_orders_change()
            RETURNS TRIGGER AS $func$
            DECLARE
                payload JSON;
            BEGIN
                IF TG_OP = 'DELETE' THEN
                    payload := json_build_object(
                        'operation', TG_OP,
                        'data',      row_to_json(OLD)
                    );
                ELSE
                    payload := json_build_object(
                        'operation', TG_OP,
                        'data',      row_to_json(NEW)
                    );
                END IF;
                PERFORM pg_notify('orders_channel', payload::text);
                RETURN NEW;
            END;
            $func$ LANGUAGE plpgsql
            """;

    private static final String CREATE_TRIGGER_SQL = """
            CREATE TRIGGER orders_change_trigger
                AFTER INSERT OR UPDATE OR DELETE ON orders
                FOR EACH ROW EXECUTE FUNCTION notify_orders_change()
            """;
}
