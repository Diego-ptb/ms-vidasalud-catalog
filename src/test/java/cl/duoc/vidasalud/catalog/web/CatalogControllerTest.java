package cl.duoc.vidasalud.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.vidasalud.catalog.domain.ServiceItem;
import cl.duoc.vidasalud.catalog.repo.ServiceItemRepository;
import cl.duoc.vidasalud.catalog.web.CatalogController.ServiceRequest;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock
    private ServiceItemRepository repository;

    private CatalogController controller;

    @BeforeEach
    void setUp() {
        controller = new CatalogController(repository);
    }

    private ServiceItem prestacionCon(int cupos) {
        ServiceItem item = new ServiceItem();
        item.setId(1L);
        item.setName("Consulta dental");
        item.setBoxCode("BOX-01");
        item.setPrice(25000);
        item.setAvailableSlots(cupos);
        return item;
    }

    @Test
    @DisplayName("Tomar un cupo lo descuenta del box")
    void descuentaUnCupo() {
        // Regla del caso: el cupo del box disminuye al confirmar la atencion.
        when(repository.findById(1L)).thenReturn(Optional.of(prestacionCon(10)));
        when(repository.save(any(ServiceItem.class))).thenAnswer(i -> i.getArgument(0));

        ServiceItem resultado = controller.takeSlot(1L);

        assertThat(resultado.getAvailableSlots()).isEqualTo(9);
    }

    @Test
    @DisplayName("Sin cupos disponibles responde 409 y no guarda")
    void rechazaCuandoNoQuedanCupos() {
        when(repository.findById(1L)).thenReturn(Optional.of(prestacionCon(0)));

        assertThatThrownBy(() -> controller.takeSlot(1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una prestacion inexistente responde 404")
    void respondeNotFoundSiNoExiste() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Crear una prestacion devuelve 201 con sus datos")
    void creaLaPrestacion() {
        when(repository.save(any(ServiceItem.class))).thenAnswer(i -> i.getArgument(0));

        var respuesta = controller.create(
                new ServiceRequest("Consulta dental", "BOX-01", 25000, 10));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getName()).isEqualTo("Consulta dental");
        assertThat(respuesta.getBody().getAvailableSlots()).isEqualTo(10);
    }

    @Test
    @DisplayName("Actualizar reemplaza los datos de la prestacion")
    void actualizaLaPrestacion() {
        when(repository.findById(1L)).thenReturn(Optional.of(prestacionCon(10)));
        when(repository.save(any(ServiceItem.class))).thenAnswer(i -> i.getArgument(0));

        ServiceItem actualizada = controller.update(1L,
                new ServiceRequest("Consulta dental urgencia", "BOX-02", 30000, 5));

        assertThat(actualizada.getName()).isEqualTo("Consulta dental urgencia");
        assertThat(actualizada.getBoxCode()).isEqualTo("BOX-02");
        assertThat(actualizada.getPrice()).isEqualTo(30000);
        assertThat(actualizada.getAvailableSlots()).isEqualTo(5);
    }
}
