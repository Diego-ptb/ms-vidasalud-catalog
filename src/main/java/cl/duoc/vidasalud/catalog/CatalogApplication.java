package cl.duoc.vidasalud.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import cl.duoc.vidasalud.catalog.messaging.MessagingProperties;

@SpringBootApplication
@EnableConfigurationProperties(MessagingProperties.class)
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
