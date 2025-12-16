package it.polimi.bsnwebapp.Repository;

import it.polimi.bsnwebapp.Model.Entities.Sensore;
import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensoreRepository extends JpaRepository<Sensore, Long> {
    Optional<Sensore> findByCodice(String codice);
    boolean existsByCodice(String codice);

    // Cerca tutti i sensori con campo tipo uguale al parametro, ignorando maiuscole/minuscole
    List<Sensore> findByTipoIgnoreCase(String tipo);
    List<Sensore> findByProtocollo(Protocollo protocollo);
}
