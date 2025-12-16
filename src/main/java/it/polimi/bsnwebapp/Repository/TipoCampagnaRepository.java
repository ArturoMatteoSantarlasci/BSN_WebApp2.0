package it.polimi.bsnwebapp.Repository;

import it.polimi.bsnwebapp.Model.Entities.TipoCampagna;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoCampagnaRepository extends JpaRepository<TipoCampagna, Long> {
    Optional<TipoCampagna> findByCodice(String codice);
    boolean existsByCodice(String codice);
}
