package com.clip2house.ledger.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.clip2house.ledger.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PartitionArchivalTest extends IntegrationTestBase {

    @Autowired PartitionMaintenanceJob job;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 보존_기간_경과_파티션은_CSV로_내보낸_뒤_제거된다() throws Exception {

        String partition = "analytics_events_y2024m01";
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + partition
                     + " PARTITION OF analytics_events FOR VALUES FROM ('2024-01-01') TO ('2024-02-01')");
        jdbc.update("INSERT INTO analytics_events (event_type, player_id, payload, created_at)"
                    + " VALUES ('TRADE', 1, '{\"probe\":\"archival\"}', '2024-01-15T00:00:00Z')");

        job.maintain();

        List<String> remaining = jdbc.queryForList("""
                SELECT c.relname FROM pg_inherits i
                JOIN pg_class c ON c.oid = i.inhrelid
                JOIN pg_class p ON p.oid = i.inhparent
                WHERE p.relname = 'analytics_events'
                """, String.class);
        assertThat(remaining).doesNotContain(partition);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_class WHERE relname = ?", Integer.class, partition)).isZero();

        Path csv = Path.of("build/test-archive").resolve(partition + ".csv");
        assertThat(csv).exists();
        String content = Files.readString(csv, StandardCharsets.UTF_8);
        assertThat(content).contains("event_type").contains("archival");
    }
}
