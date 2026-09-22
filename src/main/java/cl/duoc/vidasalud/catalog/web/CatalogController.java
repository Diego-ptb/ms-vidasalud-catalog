package cl.duoc.vidasalud.catalog.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
import cl.duoc.vidasalud.catalog.repo.ServiceItemRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/catalog/services")
public class CatalogController {

    private final ServiceItemRepository repository;

    public CatalogController(ServiceItemRepository repository) {
        this.repository = repository;
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
     * Descuenta un cupo del box. Lo invoca ms-vidasalud-appointments al
     * confirmar una atencion (regla del caso: el cupo baja al confirmar).
     */
    @PutMapping("/{id}/slots/take")
    @Transactional
    public ServiceItem takeSlot(@PathVariable Long id) {
        ServiceItem item = get(id);
        if (item.getAvailableSlots() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sin cupos disponibles para la prestacion " + id);
        }
        item.setAvailableSlots(item.getAvailableSlots() - 1);
        return repository.save(item);
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
