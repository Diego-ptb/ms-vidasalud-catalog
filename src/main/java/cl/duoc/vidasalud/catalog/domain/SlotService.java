package cl.duoc.vidasalud.catalog.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.vidasalud.catalog.repo.ServiceItemRepository;

/**
 * Regla del caso: el cupo del box baja cuando la atencion se confirma.
 *
 * La logica vive aqui y no en el controlador ni en el consumidor, porque tiene
 * dos puntos de entrada: la llamada HTTP del catalogo y el mensaje que llega
 * por la cola q.catalogo.cupo. La regla es la misma venga de donde venga.
 */
@Service
public class SlotService {

    private static final Logger log = LoggerFactory.getLogger(SlotService.class);

    private final ServiceItemRepository repository;

    public SlotService(ServiceItemRepository repository) {
        this.repository = repository;
    }

    /** La prestacion referida no existe. Error definitivo, no reintentable. */
    public static class PrestacionNoEncontrada extends RuntimeException {
        public PrestacionNoEncontrada(Long id) {
            super("No existe la prestacion " + id);
        }
    }

    /** No quedan cupos. Tampoco se arregla reintentando. */
    public static class SinCupos extends RuntimeException {
        public SinCupos(Long id) {
            super("Sin cupos disponibles para la prestacion " + id);
        }
    }

    @Transactional
    public ServiceItem tomarCupo(Long serviceId) {
        ServiceItem item = repository.findById(serviceId)
                .orElseThrow(() -> new PrestacionNoEncontrada(serviceId));

        if (item.getAvailableSlots() <= 0) {
            throw new SinCupos(serviceId);
        }

        item.setAvailableSlots(item.getAvailableSlots() - 1);
        ServiceItem guardada = repository.save(item);

        log.info("Cupo descontado de la prestacion {} ({}). Quedan {}.",
                serviceId, item.getName(), guardada.getAvailableSlots());
        return guardada;
    }
}
