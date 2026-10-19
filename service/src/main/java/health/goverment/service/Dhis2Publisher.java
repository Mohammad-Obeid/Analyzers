package health.goverment.service;

import health.goverment.service.CbcService.ResultInput;
import health.goverment.service.CbcService.SubmissionRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class Dhis2Publisher {
    private record Target(String baseUrl, String username, String programUid, String stageUid,
                          String orgUnitUid, String programType, String barcodeDataElementUid,
                          String analyzerName) {}
    private record DataValue(String dataElement, String value) {}
    private record Mapping(String dataElementUid, String sourceUnit, BigDecimal multiplier,
                           String valueType) {}

    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final String password;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Dhis2Publisher(JdbcTemplate db, ObjectMapper mapper,
                          @Value("${dhis2.password:}") String password) {
        this.db = db;
        this.mapper = mapper;
        this.password = password;
    }

    public void publish(SubmissionRequest submission, String eventUid) throws IOException, InterruptedException {
        List<Target> targets = db.query("""
                SELECT dc.base_url, dc.username, p.uid, ps.uid, ou.uid, p.program_type,
                       ps.barcode_data_element_uid, m.name
                FROM machine m
                JOIN dhis2_org_unit_mapping oum ON oum.branch_id = ?
                JOIN dhis2_org_unit ou ON ou.uid = oum.org_unit_uid AND ou.active = TRUE
                JOIN dhis2_program p ON p.active = TRUE
                JOIN dhis2_connection dc ON dc.id = p.connection_id AND dc.active = TRUE
                JOIN dhis2_program_stage ps ON ps.program_uid = p.uid AND ps.active = TRUE
                WHERE m.id = ? AND m.active = TRUE
                """, (rs, row) -> new Target(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                submission.branchId(), submission.machineId());
        if (targets.size() != 1) {
            throw new IllegalStateException("Configure exactly one active DHIS2 program, stage and branch mapping");
        }
        Target target = targets.getFirst();
        if (!"EVENT".equals(target.programType())
                && !"WITHOUT_REGISTRATION".equals(target.programType())) {
            throw new IllegalStateException("TRACKER program requires enrollment mapping; EVENT is supported");
        }
        if (password.isBlank()) throw new IllegalStateException("DHIS2_PASSWORD is not configured");

        List<DataValue> values = new ArrayList<>();
        for (ResultInput item : submission.results()) {
            List<Mapping> mappings = db.query("""
                    SELECT rm.data_element_uid, rm.source_unit, rm.multiplier, de.value_type
                    FROM sample_category c
                    JOIN dhis2_result_mapping rm ON rm.sample_category_id = c.id
                        AND rm.stage_uid = ? AND rm.active = TRUE
                    JOIN dhis2_data_element de ON de.uid = rm.data_element_uid AND de.active = TRUE
                    JOIN dhis2_stage_data_element sde ON sde.stage_uid = rm.stage_uid
                        AND sde.data_element_uid = de.uid
                    WHERE c.code = ? AND c.active = TRUE
                    """, (rs, row) -> new Mapping(rs.getString(1), rs.getString(2),
                    rs.getBigDecimal(3), rs.getString(4)), target.stageUid(),
                    item.testCode());
            if (mappings.size() != 1) {
                throw new IllegalArgumentException("Missing or ambiguous mapping for " + item.testCode());
            }
            Mapping mapping = mappings.getFirst();
            if (mapping.sourceUnit() != null && !mapping.sourceUnit().equals(item.testUnit())) {
                throw new IllegalArgumentException("Unexpected unit for " + item.testCode());
            }
            String value = item.testValue();
            if ("NUMBER".equals(mapping.valueType()) || mapping.multiplier().compareTo(BigDecimal.ONE) != 0) {
                try {
                    value = new BigDecimal(value).multiply(mapping.multiplier())
                            .stripTrailingZeros().toPlainString();
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Numeric result required for " + item.testCode());
                }
            }
            values.add(new DataValue(mapping.dataElementUid(), value));
        }

        if (target.barcodeDataElementUid() != null) {
            Integer linked = db.queryForObject("""
                    SELECT COUNT(*) FROM dhis2_stage_data_element
                    WHERE stage_uid = ? AND data_element_uid = ?
                    """, Integer.class, target.stageUid(), target.barcodeDataElementUid());
            if (linked != 1) throw new IllegalStateException("Barcode data element is not assigned to the stage");
            values.add(new DataValue(target.barcodeDataElementUid(), submission.sampleBarcode()));
        }
        Map<String, String> contextElements = new HashMap<>();
        db.query("""
                SELECT de.code, de.uid FROM dhis2_data_element de
                JOIN dhis2_stage_data_element sde ON sde.data_element_uid = de.uid
                WHERE sde.stage_uid = ? AND de.active = TRUE AND de.code IN
                    ('CBC_ANALYZER_ID', 'CBC_ANALYZER_NAME', 'CBC_PATIENT_CODE',
                     'CBC_PATIENT_NAME', 'CBC_PATIENT_GENDER', 'CBC_PATIENT_AGE')
                """, (rs, row) -> Map.entry(rs.getString(1), rs.getString(2)),
                target.stageUid()).forEach(entry -> contextElements.put(entry.getKey(), entry.getValue()));
        if (contextElements.size() != 6) {
            throw new IllegalStateException("Configure the six analyzer and patient data elements");
        }
        values.add(new DataValue(contextElements.get("CBC_ANALYZER_ID"), Long.toString(submission.machineId())));
        values.add(new DataValue(contextElements.get("CBC_ANALYZER_NAME"), target.analyzerName()));
        values.add(new DataValue(contextElements.get("CBC_PATIENT_CODE"), submission.patientCode()));
        values.add(new DataValue(contextElements.get("CBC_PATIENT_NAME"), submission.patientName()));
        values.add(new DataValue(contextElements.get("CBC_PATIENT_GENDER"), submission.patientGender()));
        values.add(new DataValue(contextElements.get("CBC_PATIENT_AGE"), submission.patientAge().toString()));

        String body = mapper.writeValueAsString(Map.of("events", List.of(Map.of(
                "event", eventUid,
                "program", target.programUid(),
                "programStage", target.stageUid(),
                "orgUnit", target.orgUnitUid(),
                "occurredAt", (submission.measuredAt() == null ? Instant.now() : submission.measuredAt()).toString(),
                "status", "COMPLETED",
                "dataValues", values.stream().map(v -> Map.of(
                        "dataElement", v.dataElement(), "value", v.value())).toList()
        ))));
        String baseUrl = target.baseUrl().replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tracker?async=false"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (target.username() + ":" + password).getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DHIS2 returned HTTP " + response.statusCode() + ": "
                    + brief(response.body()));
        }
        JsonNode report = mapper.readTree(response.body());
        if (!"OK".equals(report.path("status").asText())
                || report.path("stats").path("ignored").asInt(0) > 0
                || report.path("stats").path("created").asInt(0)
                   + report.path("stats").path("updated").asInt(0) < 1
                || report.path("validationReport").path("errorReports").size() > 0) {
            throw new IllegalStateException("DHIS2 rejected the event: " + brief(response.body()));
        }
    }

    private static String brief(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 500));
    }
}
