package it.polimi.bsnwebapp.DTO.request;

import it.polimi.bsnwebapp.Model.Enum.RuoloUtente;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * DTO per la richiesta di registrazione di un nuovo utente.
 * Contiene i dati necessari per la registrazione e include le annotazioni di validazione per garantire l'integrità dei dati.
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "Il nome utente non può essere vuoto")
    @Size(min = 3, max = 50, message = "Il nome utente deve avere tra 3 e 50 caratteri")
    private String username;

    @NotBlank(message = "La password non può essere vuota")
    @Size(min = 5, message = "La password deve avere almeno 5 caratteri")
    private String password;

    @NotNull(message = "Il ruolo non può essere vuoto")
    private RuoloUtente ruolo; // Corrisponde al nome dell'enum RuoloUtente

}
