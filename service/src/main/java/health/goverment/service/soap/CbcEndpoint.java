package health.goverment.service.soap;

import health.goverment.service.CbcService;
import health.goverment.service.CbcService.ResultInput;
import health.goverment.service.CbcService.SubmissionRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Endpoint
public class CbcEndpoint {
    public static final String NAMESPACE_URI = "http://health.goverment/service/cbc";
    private final CbcService service;

    public CbcEndpoint(CbcService service) {
        this.service = service;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getSampleDataRequest")
    @ResponsePayload
    public Source getSampleData(@RequestPayload Source payload) {
        Element response = response("getSampleDataResponse");
        try {
            Element request = element(payload);
            long machineId = number(request, "machineId");
            long sampleId = number(request, "sampleId");
            var results = service.getSampleData(machineId, sampleId);
            add(response, "status", results.isEmpty() ? "NO_DATA" : "SUCCESS");
            add(response, "message", results.isEmpty() ? "No result definitions found" : "Ordered result definitions");
            Element list = add(response, "results", null);
            for (var item : results) {
                Element result = add(list, "result", null);
                add(result, "categoryId", item.categoryId());
                add(result, "testCode", item.testCode());
                add(result, "resultOrder", item.resultOrder());
                if (item.startPosition() != null) add(result, "startPosition", item.startPosition());
                if (item.categoryLength() != null) add(result, "categoryLength", item.categoryLength());
                add(result, "factor", item.factor());
            }
        } catch (Exception error) {
            add(response, "status", "FAILED");
            add(response, "message", error.getMessage());
        }
        return new DOMSource(response.getOwnerDocument());
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "barCodeDataRequest")
    @ResponsePayload
    public Source barCodeData(@RequestPayload Source payload) {
        Element response = response("barCodeDataResponse");
        try {
            Element request = element(payload);
            long machineId = number(request, "machineId");
            long sampleId = number(request, "sampleId");
            var barcode = service.getBarcode(machineId, sampleId);
            add(response, "status", "SUCCESS");
            add(response, "message", "Barcode generated");
            Element list = add(response, "barcodes", null);
            Element item = add(list, "barcode", null);
            add(item, "machineId", barcode.machineId());
            add(item, "value", barcode.value());
        } catch (Exception error) {
            add(response, "status", "FAILED");
            add(response, "message", error.getMessage());
        }
        return new DOMSource(response.getOwnerDocument());
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "submitCBCResultsRequest")
    @ResponsePayload
    public Source submitCBCResults(@RequestPayload Source payload) {
        Element response = response("submitCBCResultsResponse");
        String requestId = "";
        try {
            Element request = element(payload);
            requestId = required(request, "requestId");
            long machineId = number(request, "machineId");
            String barcode = required(request, "sampleBarcode");
            long branchId = number(request, "branchId");
            String patientCode = required(request, "patientCode");
            String patientName = required(request, "patientName");
            String patientGender = required(request, "patientGender");
            int patientAge = Math.toIntExact(number(request, "patientAge"));
            String measuredAtText = optional(request, "measuredAt");
            Instant measuredAt = measuredAtText == null ? null : Instant.parse(measuredAtText);
            Element results = child(request, "results");
            List<ResultInput> items = new ArrayList<>();
            if (results != null) {
                for (Element result : children(results, "result")) {
                    items.add(new ResultInput(required(result, "testCode"), required(result, "testValue"),
                            optional(result, "testUnit")));
                }
            }
            var outcome = service.submit(new SubmissionRequest(requestId, machineId, barcode, branchId,
                    patientCode, patientName, patientGender, patientAge, measuredAt, items));
            add(response, "requestId", outcome.requestId());
            add(response, "status", outcome.status());
            add(response, "acceptedCount", outcome.acceptedCount());
            add(response, "rejectedCount", outcome.rejectedCount());
            if (outcome.eventUid() != null) add(response, "eventUid", outcome.eventUid());
            add(response, "message", outcome.message());
        } catch (Exception error) {
            add(response, "requestId", requestId);
            add(response, "status", "FAILED");
            add(response, "acceptedCount", 0);
            add(response, "rejectedCount", 0);
            add(response, "message", error.getMessage());
        }
        return new DOMSource(response.getOwnerDocument());
    }

    private static Element element(Source source) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DOMResult result = new DOMResult();
            factory.newTransformer().transform(source, result);
            Node node = result.getNode();
            return node instanceof Document document ? document.getDocumentElement() : (Element) node;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid SOAP payload", error);
        }
    }

    private static Element response(String name) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Document document = factory.newDocumentBuilder().newDocument();
            Element root = document.createElementNS(NAMESPACE_URI, "cbc:" + name);
            document.appendChild(root);
            return root;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static Element add(Element parent, String name, Object value) {
        Element element = parent.getOwnerDocument().createElementNS(NAMESPACE_URI, "cbc:" + name);
        if (value != null) element.setTextContent(value.toString());
        parent.appendChild(element);
        return element;
    }

    private static Element child(Element parent, String name) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && name.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && name.equals(element.getLocalName())) found.add(element);
        }
        return found;
    }

    private static String optional(Element parent, String name) {
        Element item = child(parent, name);
        return item == null || item.getTextContent().isBlank() ? null : item.getTextContent().trim();
    }

    private static String required(Element parent, String name) {
        String value = optional(parent, name);
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static long number(Element parent, String name) {
        return Long.parseLong(required(parent, name));
    }
}
