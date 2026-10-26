package cl.duoc.vidasalud.catalog.messaging;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ del dominio de catalogo, declarada como beans.
 *
 * Este servicio es duenio de una sola cola, q.catalogo.cupo, la que el mismo
 * consume. Los exchanges se declaran igual que en los demas servicios: la
 * operacion es idempotente, de modo que el primero que arranque los crea y el
 * resto los encuentra ya hechos. Asi ningun servicio depende del orden de
 * arranque de otro.
 */
@Configuration
public class RabbitTopologyConfig {

    private final MessagingProperties nombres;

    public RabbitTopologyConfig(MessagingProperties nombres) {
        this.nombres = nombres;
    }

    // ------------------------------------------------------------- exchanges

    @Bean
    TopicExchange exchangeAtenciones() {
        return new TopicExchange(nombres.getExchanges().getTopic(), true, false);
    }

    /**
     * El descuento de cupo llega por aqui. Es direct y no topic porque es una
     * orden con un destinatario unico: este servicio.
     */
    @Bean
    DirectExchange exchangeComandos() {
        return new DirectExchange(nombres.getExchanges().getDirect(), true, false);
    }

    @Bean
    DirectExchange exchangeDlx() {
        return new DirectExchange(nombres.getExchanges().getDlx(), true, false);
    }

    // --------------------------------------------------------- cola de cupos

    @Bean
    Queue colaCupo() {
        return QueueBuilder.durable(nombres.getQueues().getCupo())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", nombres.getExchanges().getDlx(),
                        "x-dead-letter-routing-key", nombres.getDlq().getCupo()))
                .build();
    }

    @Bean
    Queue colaCupoDlq() {
        return QueueBuilder.durable(nombres.getDlq().getCupo()).build();
    }

    @Bean
    Binding bindingCupo() {
        return BindingBuilder.bind(colaCupo())
                .to(exchangeComandos())
                .with(nombres.getRouting().getCupoTomar());
    }

    @Bean
    Binding bindingCupoDlq() {
        return BindingBuilder.bind(colaCupoDlq())
                .to(exchangeDlx())
                .with(nombres.getDlq().getCupo());
    }
}
