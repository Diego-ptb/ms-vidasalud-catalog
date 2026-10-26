package cl.duoc.vidasalud.catalog.messaging.consumer.catalogo;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.vidasalud.catalog.domain.SlotService;
import cl.duoc.vidasalud.catalog.messaging.ErrorRecuperable;
import cl.duoc.vidasalud.catalog.messaging.events.TomarCupoCommand;

/**
 * Consumidor del dominio de catalogo: escucha q.catalogo.cupo y descuenta el
 * cupo del box cuando una atencion se confirma.
 *
 * Esta es la parte que antes era una llamada HTTP directa entre microservicios
 * y ahora va por cola. La diferencia practica: si el catalogo esta caido, la
 * confirmacion de la atencion no falla; el mensaje espera en la cola y se
 * procesa cuando el servicio vuelve.
 *
 * ACK manual, con las mismas tres salidas que el resto de los consumidores:
 *
 *   descontado             -> basicAck
 *   prestacion invalida    -> basicNack sin requeue (a la DLQ)
 *   sin cupos              -> basicNack sin requeue (a la DLQ)
 *   fallo transitorio      -> basicNack con requeue la primera vez, DLQ la segunda
 *
 * "Sin cupos" va a la DLQ y no a reintento a proposito: el cupo no va a
 * aparecer por insistir, y alguien tiene que revisar esa atencion a mano.
 */
@Component
public class CupoListener {

    private static final Logger log = LoggerFactory.getLogger(CupoListener.class);

    private final SlotService cupos;

    public CupoListener(SlotService cupos) {
        this.cupos = cupos;
    }

    @RabbitListener(queues = "${vidasalud.messaging.queues.cupo}")
    public void recibir(TomarCupoCommand comando,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean reentregado)
            throws IOException {

        boolean segundoIntento = Boolean.TRUE.equals(reentregado);

        try {
            cupos.tomarCupo(comando.serviceId());
            log.info("Mensaje procesado: cupo descontado para la atencion {}", comando.atencionId());
            channel.basicAck(deliveryTag, false);

        } catch (SlotService.PrestacionNoEncontrada | SlotService.SinCupos e) {
            log.error("Mensaje a la DLQ. atencionId={} serviceId={} motivo={}",
                    comando.atencionId(), comando.serviceId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);

        } catch (ErrorRecuperable e) {
            if (segundoIntento) {
                log.error("Fallo transitorio repetido; el cupo de la atencion {} va a la DLQ.",
                        comando.atencionId(), e);
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.warn("Fallo transitorio; se devuelve a la cola el cupo de la atencion {}.",
                        comando.atencionId(), e);
                channel.basicNack(deliveryTag, false, true);
            }

        } catch (Exception e) {
            if (segundoIntento) {
                log.error("Error inesperado repetido; atencion {} a la DLQ.",
                        comando.atencionId(), e);
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.error("Error inesperado descontando el cupo de la atencion {}; un reintento.",
                        comando.atencionId(), e);
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }

    /**
     * Consumidor de la DLQ de cupos. Registra el descuadre para que alguien lo
     * revise: una atencion confirmada sin cupo descontado deja el box mal
     * contado, y eso no se puede perder en silencio.
     */
    @RabbitListener(queues = "${vidasalud.messaging.dlq.cupo}")
    public void recibirDeLaDlq(TomarCupoCommand comando,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.error("DLQ catalogo: el cupo de la prestacion {} no se desconto (atencion {}). "
                + "Revisar el box a mano.", comando.serviceId(), comando.atencionId());
        channel.basicAck(deliveryTag, false);
    }
}
