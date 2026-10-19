package health.goverment.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CbcService {
    public record SampleData(long categoryId, String testCode, int resultOrder,
                             Integer startPosition, Integer categoryLength, BigDecimal factor) {}
    public record Barcode(long machineId, String value) {}
    public record ResultInput(String testCode, String testValue, String testUnit) {}
    public record SubmissionRequest(String requestId, long machineId, String sampleBarcode, long branchId,
                                    String patientCode, String patientName, String patientGender,
                                    Integer patientAge, Instant measuredAt, List<ResultInput> results) {}
    public record SubmissionResponse(String requestId, String status, int acceptedCount,
                                     int rejectedCount, String eventUid, String message) {}

    private static final String UID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final JdbcTemplate db;
    private final Dhis2Publisher publisher;

    public CbcService(JdbcTemplate db, Dhis2Publisher publisher) {
        this.db = db;
        this.publisher = publisher;
    }

    public List<SampleData> getSampleData(long machineId, long sampleId) {
        if (sampleId < 0) throw new IllegalArgumentException("sampleId must be non-negative");
        return db.query("""
                SELECT c.id, c.code, COALESCE(d.result_order, c.sort_order), d.start_position,
                       d.category_length, COALESCE(d.factor, 1)
                FROM machine m
                CROSS JOIN sample_category c
                LEFT JOIN analyzer_result_definition d ON d.machine_id = m.id
                    AND d.sample_category_id = c.id AND d.active = TRUE
                WHERE m.id = ? AND m.active = TRUE AND c.active = TRUE
                ORDER BY COALESCE(d.result_order, c.sort_order), c.id
                """, (rs, row) -> new SampleData(rs.getLong(1), rs.getString(2), rs.getInt(3),
                (Integer) rs.getObject(4), (Integer) rs.getObject(5), rs.getBigDecimal(6)), machineId);
    }

    public Barcode getBarcode(long machineId, long sampleId) {
        List<Long> sampleTypes = db.query("SELECT sample_type_id FROM machine WHERE id = ? AND active = TRUE",
                (rs, row) -> rs.getLong(1), machineId);
        if (sampleTypes.size() != 1) throw new IllegalArgumentException("Active machine not found");
        return new Barcode(machineId, barcode(machineId, sampleTypes.getFirst(), sampleId));
    }

    public SubmissionResponse submit(SubmissionRequest request) {
        String requestId = required(request.requestId(), "requestId");
        if (requestId.length() > 100) throw new IllegalArgumentException("requestId is too long");
        if (request.results() == null || request.results().isEmpty()) {
            throw new IllegalArgumentException("At least one result is required");
        }
        required(request.patientCode(), "patientCode");
        required(request.patientName(), "patientName");
        required(request.patientGender(), "patientGender");
        if (request.patientAge() == null || request.patientAge() < 0) {
            throw new IllegalArgumentException("patientAge must be non-negative");
        }
        if (request.branchId() < 0) throw new IllegalArgumentException("branchId must be non-negative");
        String barcode = required(request.sampleBarcode(), "sampleBarcode");
        if (!barcode.matches("[0-9]{11}")) {
            throw new IllegalArgumentException("sampleBarcode must contain exactly 11 digits");
        }
        long sampleId = Long.parseLong(barcode.substring(4));
        if (!barcode.equals(getBarcode(request.machineId(), sampleId).value())) {
            throw new IllegalArgumentException("Barcode does not match the configured machine and sample type");
        }
        Set<String> codes = new HashSet<>();
        for (ResultInput item : request.results()) {
            String code = required(item.testCode(), "testCode");
            required(item.testValue(), "testValue");
            if (!codes.add(code)) throw new IllegalArgumentException("Duplicate testCode: " + code);
        }

        String eventUid = eventUid(requestId);
        try {
            publisher.publish(request, eventUid);
            return new SubmissionResponse(requestId, "SUCCESS", request.results().size(), 0, eventUid,
                    "Result published to DHIS2");
        } catch (Exception failure) {
            return new SubmissionResponse(requestId, "FAILED", 0, request.results().size(), eventUid,
                    "DHIS2 publication failed: " + failure.getMessage());
        }
    }

    private static String barcode(long machineId, long sampleTypeId, long sampleId) {
        if (machineId < 0 || machineId > 99 || sampleTypeId < 0 || sampleTypeId > 99
                || sampleId < 0 || sampleId > 9_999_999) {
            throw new IllegalArgumentException("IDs do not fit the 2+2+7 barcode format");
        }
        return "%02d%02d%07d".formatted(machineId, sampleTypeId, sampleId);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String eventUid(String requestId) {
        try {
            BigInteger bits = new BigInteger(1, MessageDigest.getInstance("SHA-256")
                    .digest(requestId.getBytes(StandardCharsets.UTF_8)));
            StringBuilder uid = new StringBuilder(11);
            BigInteger[] first = bits.divideAndRemainder(BigInteger.valueOf(52));
            uid.append(UID_CHARS.charAt(first[1].intValue()));
            bits = first[0];
            for (int i = 0; i < 10; i++) {
                BigInteger[] next = bits.divideAndRemainder(BigInteger.valueOf(UID_CHARS.length()));
                uid.append(UID_CHARS.charAt(next[1].intValue()));
                bits = next[0];
            }
            return uid.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
