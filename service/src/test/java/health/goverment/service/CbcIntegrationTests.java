package health.goverment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:cbctest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "dhis2.password=test-password",
        "cbc.api-key=test-key"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CbcIntegrationTests {
    @Autowired JdbcTemplate db;
    @Value("${local.server.port}") int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger dhisCalls = new AtomicInteger();
    private final AtomicReference<String> dhisBody = new AtomicReference<>();
    private final AtomicReference<String> dhisStatus = new AtomicReference<>("OK");
    private HttpServer fakeDhis2;

    @BeforeAll
    void startDhis2() throws IOException {
        fakeDhis2 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeDhis2.createContext("/api/tracker", exchange -> {
            dhisCalls.incrementAndGet();
            dhisBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String status = dhisStatus.get();
            byte[] response = ("{\"status\":\"" + status + "\",\"stats\":{\"created\":1,\"ignored\":0},"
                    + "\"validationReport\":{\"errorReports\":[]}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        fakeDhis2.start();
    }

    @AfterAll
    void stopDhis2() {
        fakeDhis2.stop(0);
    }

    @BeforeEach
    void seed() {
        for (String table : new String[]{"dhis2_result_mapping", "dhis2_section_data_element",
                "dhis2_program_stage_section", "dhis2_stage_data_element", "dhis2_data_element",
                "dhis2_org_unit_mapping", "dhis2_org_unit", "dhis2_program_stage", "dhis2_program",
                "dhis2_connection", "analyzer_result_definition", "sample_category", "machine"}) {
            db.update("DELETE FROM " + table);
        }
        dhisCalls.set(0);
        dhisBody.set(null);
        dhisStatus.set("OK");
        db.update("INSERT INTO machine(id,name,brand,sample_type_id) VALUES (9,'MEK CBC','MEK',38)");
        db.update("INSERT INTO sample_category(id,code,name) VALUES (101,'WBC','WBC'),(102,'HGB','HGB')");
        db.update("""
                INSERT INTO analyzer_result_definition(machine_id,sample_category_id,result_order,start_position,category_length)
                VALUES (9,101,2,20,5), (9,102,1,10,5)
                """);
        db.update("INSERT INTO dhis2_connection(id,environment,base_url,username) VALUES (1,'LOCAL',?,'integration')",
                "http://127.0.0.1:" + fakeDhis2.getAddress().getPort());
        db.update("INSERT INTO dhis2_program(uid,connection_id,name,program_type) VALUES ('Abcdef12345',1,'CBC','EVENT')");
        db.update("INSERT INTO dhis2_program_stage(uid,program_uid,name,barcode_data_element_uid) VALUES ('Stageab1234','Abcdef12345','CBC','Barcod12345')");
        db.update("INSERT INTO dhis2_org_unit(uid,name) VALUES ('Orgunit1234','Lab')");
        db.update("INSERT INTO dhis2_org_unit_mapping(branch_id,org_unit_uid) VALUES (5,'Orgunit1234')");
        db.update("""
                INSERT INTO dhis2_data_element(uid,code,name,value_type) VALUES
                ('Wbcde123456','CBC_WBC','WBC','NUMBER'),
                ('Hgbde123456','CBC_HGB','HGB','NUMBER'),
                ('Barcod12345','CBC_BARCODE','Barcode','TEXT'),
                ('AnlyId12345','CBC_ANALYZER_ID','Analyzer ID','TEXT'),
                ('AnlyNm12345','CBC_ANALYZER_NAME','Analyzer Name','TEXT'),
                ('PatCod12345','CBC_PATIENT_CODE','Patient Code','TEXT'),
                ('PatNam12345','CBC_PATIENT_NAME','Patient Name','TEXT'),
                ('PatGen12345','CBC_PATIENT_GENDER','Gender','TEXT'),
                ('PatAge12345','CBC_PATIENT_AGE','Age','NUMBER')
                """);
        for (String uid : new String[]{"Wbcde123456", "Hgbde123456", "Barcod12345",
                "AnlyId12345", "AnlyNm12345", "PatCod12345", "PatNam12345",
                "PatGen12345", "PatAge12345"}) {
            db.update("INSERT INTO dhis2_stage_data_element(stage_uid,data_element_uid) VALUES ('Stageab1234',?)", uid);
        }
        db.update("INSERT INTO dhis2_result_mapping(sample_category_id,stage_uid,data_element_uid) VALUES (101,'Stageab1234','Wbcde123456')");
        db.update("INSERT INTO dhis2_result_mapping(sample_category_id,stage_uid,data_element_uid) VALUES (102,'Stageab1234','Hgbde123456')");
    }

    @Test
    void definitionsAreOrderedWithoutStoredSamples() throws Exception {
        String response = soap("""
                <cbc:getSampleDataRequest xmlns:cbc="http://health.goverment/service/cbc">
                  <cbc:machineId>9</cbc:machineId><cbc:sampleId>1156447</cbc:sampleId>
                </cbc:getSampleDataRequest>
                """);
        assertTrue(response.contains("<cbc:status>SUCCESS</cbc:status>"), response);
        assertTrue(response.indexOf("HGB") < response.indexOf("WBC"), response);
        assertTrue(response.contains("<cbc:startPosition>10</cbc:startPosition>"), response);
        assertFalse(response.contains("testTypeId"), response);
    }

    @Test
    void barcodeUsesMachineConfigurationWithoutStoredSamples() throws Exception {
        String response = soap("""
                <cbc:barCodeDataRequest xmlns:cbc="http://health.goverment/service/cbc">
                  <cbc:machineId>9</cbc:machineId><cbc:sampleId>1156447</cbc:sampleId>
                </cbc:barCodeDataRequest>
                """);
        assertTrue(response.contains("09381156447"), response);
        assertTrue(response.contains("<cbc:status>SUCCESS</cbc:status>"), response);
    }

    @Test
    void publishesPatientAndResultsWithoutStoringThemLocally() throws Exception {
        String response = soap(resultRequest("request-1", "09381156447", "Demo Patient"));
        assertTrue(response.contains("<cbc:status>SUCCESS</cbc:status>"), response);
        assertTrue(response.contains("<cbc:acceptedCount>2</cbc:acceptedCount>"), response);
        assertEquals(1, dhisCalls.get());
        assertTrue(dhisBody.get().contains("Demo Patient"));
        assertTrue(dhisBody.get().contains("09381156447"));
        assertTrue(dhisBody.get().contains("Wbcde123456"));
        assertTrue(dhisBody.get().contains("Hgbde123456"));
        assertEquals(0, db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'PUBLIC' AND table_name IN ('SAMPLE','SAMPLE_TEST','ANALYZER_RESULT','DHIS2_SUBMISSION')
                """, Integer.class));
    }

    @Test
    void sameRequestIdProducesSameEventUidForRetry() throws Exception {
        String first = soap(resultRequest("request-1", "09381156447", "Demo Patient"));
        String again = soap(resultRequest("request-1", "09381156447", "Demo Patient"));
        String uid = first.substring(first.indexOf("<cbc:eventUid>") + 14, first.indexOf("</cbc:eventUid>"));
        assertEquals(11, uid.length());
        assertTrue(again.contains("<cbc:eventUid>" + uid + "</cbc:eventUid>"), again);
        assertEquals(2, dhisCalls.get());
    }

    @Test
    void failedDeliveryReturnsFailureAndCanBeRetried() throws Exception {
        dhisStatus.set("ERROR");
        String failed = soap(resultRequest("request-2", "09381156447", "Demo Patient"));
        assertTrue(failed.contains("<cbc:status>FAILED</cbc:status>"), failed);
        assertTrue(failed.contains("<cbc:rejectedCount>2</cbc:rejectedCount>"), failed);
        dhisStatus.set("OK");
        String retried = soap(resultRequest("request-2", "09381156447", "Demo Patient"));
        assertTrue(retried.contains("<cbc:status>SUCCESS</cbc:status>"), retried);
        assertEquals(2, dhisCalls.get());
    }

    @Test
    void rejectsWrongBarcodeAndMissingPatientBeforePublishing() throws Exception {
        String wrongBarcode = soap(resultRequest("request-3", "09391156447", "Demo Patient"));
        assertTrue(wrongBarcode.contains("<cbc:status>FAILED</cbc:status>"), wrongBarcode);
        String missingPatient = soap(resultRequest("request-4", "09381156447", ""));
        assertTrue(missingPatient.contains("<cbc:status>FAILED</cbc:status>"), missingPatient);
        assertEquals(0, dhisCalls.get());
    }

    @Test
    void wsdlExposesAllThreeOperations() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/ws/machineReaderService.wsdl"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("getSampleData"));
        assertTrue(response.body().contains("barCodeData"));
        assertTrue(response.body().contains("submitCBCResults"));
        assertTrue(response.body().contains("patientCode"));
        assertTrue(response.body().contains("branchId"));
    }

    @Test
    void soapRequestsRequireApiKey() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/ws"))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString("<invalid/>"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    private String soap(String body) throws Exception {
        String envelope = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>%s</soap:Body>
                </soap:Envelope>
                """.formatted(body);
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/ws"))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("X-CBC-Api-Key", "test-key")
                .POST(HttpRequest.BodyPublishers.ofString(envelope)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    private static String resultRequest(String requestId, String barcode, String patientName) {
        return """
                <cbc:submitCBCResultsRequest xmlns:cbc="http://health.goverment/service/cbc">
                  <cbc:requestId>%s</cbc:requestId><cbc:machineId>9</cbc:machineId>
                  <cbc:sampleBarcode>%s</cbc:sampleBarcode><cbc:branchId>5</cbc:branchId>
                  <cbc:patientCode>P-001</cbc:patientCode><cbc:patientName>%s</cbc:patientName>
                  <cbc:patientGender>Female</cbc:patientGender><cbc:patientAge>32</cbc:patientAge>
                  <cbc:results>
                    <cbc:result><cbc:testCode>WBC</cbc:testCode><cbc:testValue>9.9</cbc:testValue></cbc:result>
                    <cbc:result><cbc:testCode>HGB</cbc:testCode><cbc:testValue>11.9</cbc:testValue></cbc:result>
                  </cbc:results>
                </cbc:submitCBCResultsRequest>
                """.formatted(requestId, barcode, patientName);
    }
}
