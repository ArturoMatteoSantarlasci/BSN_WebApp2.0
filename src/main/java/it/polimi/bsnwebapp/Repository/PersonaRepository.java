package it.polimi.bsnwebapp.Repository;

import it.polimi.bsnwebapp.Model.Entities.Persona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonaRepository extends JpaRepository<Persona, Long> {
    List<Persona> findByCognome(String cognome);
    List<Persona> findByNomeAndCognome(String nome, String cognome);
}
