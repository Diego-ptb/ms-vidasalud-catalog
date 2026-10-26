package cl.duoc.vidasalud.catalog.domain;

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

import cl.duoc.vidasalud.catalog.repo.ServiceItemRepository;

/**
 * La regla del cupo tiene dos puertas de entrada (HTTP y cola), asi que se
 * prueba una sola vez, aqui, donde vive.
 */
@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    private ServiceItemRepository repository;

    private SlotService service;

    @BeforeEach
    void setUp() {
        service = new SlotService(repository);
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
        when(repository.findById(1L)).thenReturn(Optional.of(prestacionCon(10)));
        when(repository.save(any(ServiceItem.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.tomarCupo(1L).getAvailableSlots()).isEqualTo(9);
    }

    @Test
    @DisplayName("El ultimo cupo se puede tomar y deja el box en cero")
    void permiteTomarElUltimoCupo() {
        when(repository.findById(1L)).thenReturn(Optional.of(prestacionCon(1)));
        when(repository.save(any(ServiceItem.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.tomarCupo(1L).getAvailableSlots()).isZero();
    }

    @Test
    @DisplayName("Sin cupos no descuenta ni guarda")
    void rechazaCuandoNoQuedanCupos() {
        when(repository.findById(1L)).thenReturn(Optional.of(prestacionCon(0)));

        assertThatThrownBy(() -> service.tomarCupo(1L))
                .isInstanceOf(SlotService.SinCupos.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una prestacion inexistente es un error definitivo")
    void fallaSiLaPrestacionNoExiste() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tomarCupo(9L))
                .isInstanceOf(SlotService.PrestacionNoEncontrada.class)
                .hasMessageContaining("No existe la prestacion 9");
    }
}
