package cl.duoc.vidasalud.catalog.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.vidasalud.catalog.domain.ServiceItem;
import cl.duoc.vidasalud.catalog.domain.SlotService;
import cl.duoc.vidasalud.catalog.repo.ServiceItemRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/catalog/services")
public class CatalogController {

    private final ServiceItemRepository repository;
    private final SlotService cupos;

    public CatalogController(ServiceItemRepository repository, SlotService cupos) {
        this.repository = repository;
        this.cupos = cupos;
    }

    public record ServiceRequest(
            @NotBlank String name,
            @NotBlank String boxCode,
            @NotNull @Min(0) Integer price,
            @NotNull @Min(0) Integer availableSlots) {
    }

    @GetMapping
    public List<ServiceItem> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ServiceItem get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No existe la prestacion " + id));
    }

    @PostMapping
    public ResponseEntity<ServiceItem> create(@Valid @RequestBody ServiceRequest request) {
        ServiceItem item = new ServiceItem();
        apply(item, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(item));
    }

    @PutMapping("/{id}")
    public ServiceItem update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        ServiceItem item = get(id);
        apply(item, request);
        return repository.save(item);
    }

    /**
     * Descuenta un cupo del box por via HTTP. El camino habitual es asincrono
     * (la cola q.catalogo.cupo), pero el endpoint se mantiene para operar a
     * mano y para poder probar la regla sin broker.
     *
     * La regla misma vive en SlotService: aqui solo se traduce a codigos HTTP.
     */
    @PutMapping("/{id}/slots/take")
    public ServiceItem takeSlot(@PathVariable Long id) {
        try {
            return cupos.tomarCupo(id);
        } catch (SlotService.PrestacionNoEncontrada e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SlotService.SinCupos e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.delete(get(id));
        return ResponseEntity.noContent().build();
    }

    private void apply(ServiceItem item, ServiceRequest request) {
        item.setName(request.name());
        item.setBoxCode(request.boxCode());
        item.setPrice(request.price());
        item.setAvailableSlots(request.availableSlots());
    }
}
