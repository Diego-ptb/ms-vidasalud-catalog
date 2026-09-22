package cl.duoc.vidasalud.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.vidasalud.catalog.domain.ServiceItem;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {

    List<ServiceItem> findByBoxCode(String boxCode);

    List<ServiceItem> findByAvailableSlotsGreaterThan(Integer min);
}
