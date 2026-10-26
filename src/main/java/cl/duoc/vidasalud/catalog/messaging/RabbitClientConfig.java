package cl.duoc.vidasalud.catalog.messaging;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serializacion de los mensajes. Este servicio solo consume, asi que no
 * necesita RabbitTemplate; si necesita el mismo converter que el productor,
 * porque los mensajes viajan en JSON.
 */
@Configuration
public class RabbitClientConfig {

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
