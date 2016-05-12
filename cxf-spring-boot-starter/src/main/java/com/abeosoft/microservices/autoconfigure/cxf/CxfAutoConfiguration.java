package com.abeosoft.microservices.autoconfigure.cxf;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.transport.servlet.CXFServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

// @EnableConfigurationProperties(CxfProperties.class)
@Configuration
@ConditionalOnClass({ CXFServlet.class })
@ComponentScan({ "com.abeosoft.microservices.consul.api" })
@ImportResource({ "classpath:META-INF/cxf/cxf.xml" })
public class CxfAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CxfAutoConfiguration.class);

    @Autowired
    private ApplicationContext ctx;

    @Value("${spring.application.name:news}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.cxf.inbound.logging.enabled:false}")
    private boolean requestLoggingEnabled;

    @Bean
    public ServletRegistrationBean dispatcherCXFServlet() {
	ServletRegistrationBean servlet = new ServletRegistrationBean(new CXFServlet(), "/" + applicationName + "/*");
	// servlet.addInitParameter("swagger.api.basepath", "http://localhost:"
	// + serverPort + "/" + applicationName);
	servlet.setLoadOnStartup(1);
	return servlet;
    }

    @Bean
    public Server jaxRsServer() {
	// Find all beans annotated with @Path
	List<Object> serviceBeans = new ArrayList<>(ctx.getBeansWithAnnotation(Path.class).values());
	logger.debug("Registering service beans: " + serviceBeans);

	// Find all beans annotated with @Providers
	List<Object> providers = new ArrayList<Object>(ctx.getBeansWithAnnotation(Provider.class).values());
	logger.debug("Registering providers: " + providers);

	// providers.add(this.swaggerWriter());
	// serviceBeans.add(this.swaggerResource());

	JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
	factory.setBus(ctx.getBean(SpringBus.class));
	factory.setAddress("/");
	factory.setServiceBeans(serviceBeans);
	factory.setProviders(providers);

	Server server = factory.create();

	if (requestLoggingEnabled) {
	    server.getEndpoint().getInInterceptors().add(new LoggingInInterceptor());
	}

	return server;
    }

    @Bean
    @ConditionalOnMissingBean
    public JacksonJsonProvider jsonProvider(ObjectMapper objectMapper) {
	JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
	provider.setMapper(objectMapper);
	return provider;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
	ObjectMapper mapper = new ObjectMapper();
	mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	mapper.setSerializationInclusion(Include.NON_NULL);
	mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	mapper.registerModule(new JavaTimeModule());

	mapper.setAnnotationIntrospector(AnnotationIntrospector.pair(new JacksonAnnotationIntrospector(),
		new JaxbAnnotationIntrospector(mapper.getTypeFactory())));
	return mapper;
    }
}