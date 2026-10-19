package health.goverment.service.soap;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;

@EnableWs
@Configuration
public class WebServiceConfig {

    @Bean
    ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "healthService")
    DefaultWsdl11Definition healthServiceWsdl(XsdSchema healthSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("HealthServicePort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace(HealthEndpoint.NAMESPACE_URI);
        definition.setSchema(healthSchema);
        return definition;
    }

    @Bean
    XsdSchema healthSchema() {
        return new SimpleXsdSchema(new ClassPathResource("health-service.xsd"));
    }

    @Bean(name = "machineReaderService")
    DefaultWsdl11Definition cbcServiceWsdl(XsdSchema cbcSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("MachineReaderServicePort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace(CbcEndpoint.NAMESPACE_URI);
        definition.setSchema(cbcSchema);
        return definition;
    }

    @Bean
    XsdSchema cbcSchema() {
        return new SimpleXsdSchema(new ClassPathResource("cbc-service.xsd"));
    }
}
