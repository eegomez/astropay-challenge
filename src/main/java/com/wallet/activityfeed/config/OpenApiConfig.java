package com.wallet.activityfeed.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI activityFeedOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");

        Contact contact = new Contact();
        contact.setName("Wallet Engineering Team");
        contact.setEmail("engineering@wallet.com");

        Info info = new Info()
                .title("Activity Feed Microservice API")
                .version("1.0.0")
                .description("Unified Activity Feed API for consolidating financial transactions from multiple sources")
                .contact(contact);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
