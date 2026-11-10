package com.clip2house.ledger.observability;

import com.clip2house.ledger.config.GameProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Component
public class OperatorReportJob {

    private static final Logger log = LoggerFactory.getLogger(OperatorReportJob.class);

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final GameProperties props;
    private final RestClient webhookClient = RestClient.create();

    public OperatorReportJob(JdbcClient jdbc, ObjectMapper objectMapper, GameProperties props) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Scheduled(cron = "${clip2house.report.weekly-cron}")
    @Transactional
    public void weeklyReport() {
        Instant end = Instant.now();
        Instant start = end.minus(7, ChronoUnit.DAYS);
        Map<String, Object> report = buildReport(start, end);
        jdbc.sql("INSERT INTO operator_reports (period_start, period_end, report) VALUES (:start, :end, :report::jsonb)")
                .param("start", java.sql.Timestamp.from(start))
                .param("end", java.sql.Timestamp.from(end))
                .param("report", toJson(report))
                .update();
        log.info("주간 운영 리포트: {}", report);
        deliver(report, start, end);
    }

    private void deliver(Map<String, Object> report, Instant start, Instant end) {
        String url = props.report().webhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            webhookClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(toJson(Map.of("periodStart", start.toString(), "periodEnd", end.toString(),
                            "report", report)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("운영 리포트 webhook 전달 완료: {}", url);
        } catch (Exception e) {
            log.warn("운영 리포트 webhook 전달 실패(리포트는 DB에 적재됨): {}", e.getMessage());
        }
    }

    Map<String, Object> buildReport(Instant start, Instant end) {
        Map<String, Object> report = new LinkedHashMap<>();

        List<Map<String, Object>> eventCounts = jdbc.sql("""
                        SELECT event_type, COUNT(*) AS count FROM analytics_events
                        WHERE created_at >= :start AND created_at < :end GROUP BY event_type
                        """)
                .param("start", java.sql.Timestamp.from(start))
                .param("end", java.sql.Timestamp.from(end))
                .query()
                .listOfRows();
        report.put("events", eventCounts);

        Long newItems = jdbc.sql("SELECT COUNT(*) FROM items WHERE created_at >= :start AND gen_model <> 'human'")
                .param("start", java.sql.Timestamp.from(start))
                .query(Long.class)
                .single();
        Long newListings = jdbc.sql("SELECT COUNT(*) FROM listings WHERE created_at >= :start")
                .param("start", java.sql.Timestamp.from(start))
                .query(Long.class)
                .single();
        report.put("newDictionaryEntries", newItems);
        report.put("newListings", newListings);

        Map<String, Object> abuse = jdbc.sql("""
                        SELECT COUNT(*) AS attempts, COUNT(DISTINCT player_id) AS players
                        FROM injection_audit WHERE created_at >= :start AND created_at < :end
                        """)
                .param("start", java.sql.Timestamp.from(start))
                .param("end", java.sql.Timestamp.from(end))
                .query()
                .singleRow();
        report.put("injection", abuse);

        List<Map<String, Object>> drift = jdbc.sql("""
                        SELECT i.gen_version,
                               COUNT(*) AS items,
                               ROUND(AVG(ABS(LOG(i.ref_value) - LOG(a.ref_value)))::numeric, 3) AS avg_log_distance
                        FROM items i JOIN golden_anchors a ON a.id = i.cited_anchor_id
                        WHERE i.gen_model <> 'human'
                        GROUP BY i.gen_version ORDER BY i.gen_version
                        """)
                .query()
                .listOfRows();
        report.put("valueDriftByCohort", drift);
        return report;
    }

    private String toJson(Map<String, Object> report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("리포트 직렬화 실패", e);
        }
    }
}
