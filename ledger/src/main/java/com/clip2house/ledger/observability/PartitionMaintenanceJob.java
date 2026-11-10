package com.clip2house.ledger.observability;

import com.clip2house.ledger.config.GameProperties;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PartitionMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceJob.class);
    private static final List<String> PARTITIONED_TABLES = List.of("injection_audit", "analytics_events");

    private final JdbcTemplate jdbc;
    private final GameProperties props;

    public PartitionMaintenanceJob(JdbcTemplate jdbc, GameProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        maintain();
    }

    @Scheduled(fixedDelayString = "${clip2house.retention.partition-maintenance-interval}")
    public void maintain() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        for (String table : PARTITIONED_TABLES) {
            createPartition(table, current);
            createPartition(table, current.plusMonths(1));
            dropExpiredPartitions(table, current);
        }
    }

    private void createPartition(String table, YearMonth month) {
        String name = partitionName(table, month);
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        jdbc.execute("CREATE TABLE IF NOT EXISTS %s PARTITION OF %s FOR VALUES FROM ('%s') TO ('%s')"
                .formatted(name, table, from, to));
    }

    private void dropExpiredPartitions(String table, YearMonth current) {
        int retentionMonths = table.equals("injection_audit")
                ? props.retention().auditMonths()
                : props.retention().analyticsMonths();
        YearMonth oldestKept = current.minusMonths(retentionMonths);

        List<String> partitions = jdbc.queryForList("""
                SELECT c.relname FROM pg_inherits i
                JOIN pg_class c ON c.oid = i.inhrelid
                JOIN pg_class p ON p.oid = i.inhparent
                WHERE p.relname = ?
                """, String.class, table);
        for (String partition : partitions) {
            YearMonth month = parseMonth(table, partition);
            if (month != null && month.isBefore(oldestKept)) {

                archivePartition(partition);
                jdbc.execute("ALTER TABLE %s DETACH PARTITION %s".formatted(table, partition));
                jdbc.execute("DROP TABLE %s".formatted(partition));
                log.info("보존 기간 경과 파티션 제거: {}", partition);
            }
        }
    }

    private void archivePartition(String partition) {
        String dir = props.retention().archiveDir();
        if (dir == null || dir.isBlank()) {
            log.info("archive-dir 미설정: {} 은 내보내기 없이 제거됩니다", partition);
            return;
        }
        try {
            Path target = Path.of(dir).resolve(partition + ".csv");
            Files.createDirectories(target.getParent());
            boolean[] headerWritten = {false};
            try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                jdbc.query("SELECT * FROM " + partition, rs -> {
                    try {
                        ResultSetMetaData meta = rs.getMetaData();
                        int columns = meta.getColumnCount();
                        if (!headerWritten[0]) {
                            writer.write(header(meta, columns));
                            writer.newLine();
                            headerWritten[0] = true;
                        }
                        StringBuilder line = new StringBuilder();
                        for (int i = 1; i <= columns; i++) {
                            if (i > 1) {
                                line.append(',');
                            }
                            line.append(csvCell(rs.getString(i)));
                        }
                        writer.write(line.toString());
                        writer.newLine();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            log.info("파티션 Archival 완료: {} -> {}", partition, target);
        } catch (IOException | UncheckedIOException e) {

            throw new IllegalStateException("파티션 Archival 실패: " + partition, e);
        }
    }

    private static String header(ResultSetMetaData meta, int columns) throws java.sql.SQLException {
        StringBuilder line = new StringBuilder();
        for (int i = 1; i <= columns; i++) {
            if (i > 1) {
                line.append(',');
            }
            line.append(meta.getColumnName(i));
        }
        return line.toString();
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String partitionName(String table, YearMonth month) {
        return "%s_y%dm%02d".formatted(table, month.getYear(), month.getMonthValue());
    }

    private static YearMonth parseMonth(String table, String partition) {
        String suffix = partition.substring(table.length());
        if (!suffix.matches("_y\\d{4}m\\d{2}")) {
            return null;
        }
        return YearMonth.of(Integer.parseInt(suffix.substring(2, 6)), Integer.parseInt(suffix.substring(7, 9)));
    }
}
