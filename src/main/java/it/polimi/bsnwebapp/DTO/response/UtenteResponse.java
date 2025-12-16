package it.polimi.bsnwebapp.DTO.response;

import it.polimi.bsnwebapp.Model.Enum.RuoloUtente;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UtenteResponse {

    private Long id;
    private String username;
    private RuoloUtente ruolo;
}
