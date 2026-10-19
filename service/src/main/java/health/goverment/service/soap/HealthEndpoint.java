package health.goverment.service.soap;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class HealthEndpoint {

    public static final String NAMESPACE_URI = "http://health.goverment/service";

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pingRequest")
    @ResponsePayload
    public Source ping(@RequestPayload Source request) {
        return new StreamSource(new StringReader("""
                <ns:pingResponse xmlns:ns=\"http://health.goverment/service\">
                    <ns:message>Service is available</ns:message>
                </ns:pingResponse>
                """));
    }
}
