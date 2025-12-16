package it.polimi.bsnwebapp.Repository;

import it.polimi.bsnwebapp.Model.Entities.Utente;
import it.polimi.bsnwebapp.Model.Enum.RuoloUtente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtenteRepository extends JpaRepository<Utente, Long> {

    Optional<Utente> findByUsername(String username);

    //evita doppioni in registrazione
    boolean existsByUsername(String username);

    Optional<Utente> findByRuolo(RuoloUtente ruolo);
}
