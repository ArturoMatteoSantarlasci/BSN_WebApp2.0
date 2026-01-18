package it.polimi.bsnwebapp.Repository;

import it.polimi.bsnwebapp.Model.Entities.CampagnaSensore;
import it.polimi.bsnwebapp.Model.Entities.CampagnaSensoreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository Spring Data JPA per l'entita CampagnaSensore.
 * Espone operazioni CRUD e query derivate dai nomi dei metodi dichiarati.
 * Viene utilizzato dai service per accedere al database MariaDB.
 */

public interface CampagnaSensoreRepository extends JpaRepository<CampagnaSensore, CampagnaSensoreId> {
    List<CampagnaSensore> findByCampagna_Id(Long idCampagna);
    boolean existsBySensore_Id(Long idSensore);
}
