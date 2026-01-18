package it.polimi.bsnwebapp.Repository;

import it.polimi.bsnwebapp.Model.Entities.TipoCampagna;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA per l'entita TipoCampagna.
 * Espone operazioni CRUD e query derivate dai nomi dei metodi dichiarati.
 * Viene utilizzato dai service per accedere al database MariaDB.
 */

public interface TipoCampagnaRepository extends JpaRepository<TipoCampagna, Long> {
}
