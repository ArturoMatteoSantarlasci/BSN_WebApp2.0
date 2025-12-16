package it.polimi.bsnwebapp.Repository;

import it.polimi.bsnwebapp.Model.Entities.CampagnaSensore;
import it.polimi.bsnwebapp.Model.Entities.CampagnaSensoreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampagnaSensoreRepository extends JpaRepository<CampagnaSensore, CampagnaSensoreId> {
    List<CampagnaSensore> findByCampagna_Id(Long idCampagna);
    List<CampagnaSensore> findBySensore_Id(Long idSensore);

    Optional<CampagnaSensore> findByCampagna_IdAndSensore_Id(Long idCampagna, Long idSensore);
}
