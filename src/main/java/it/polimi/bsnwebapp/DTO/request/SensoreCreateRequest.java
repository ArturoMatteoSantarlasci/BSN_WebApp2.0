package it.polimi.bsnwebapp.DTO.request;

import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SensoreCreateRequest {

    @NotBlank(message = "Il codice non può essere vuoto")
    @Size(min = 4, max = 4, message = "Il codice deve essere lungo 4 caratteri")
    private String codice;

    @NotBlank(message = "Il nome non può essere vuoto")
    private String nome;

    @NotBlank(message = "Il tipo non può essere vuoto")
    private String tipo;

    @NotNull(message = "Il protocollo di default non può essere null")
    private Protocollo protocolloDefault;

    // opzionale: se null/empty => {protocolloDefault}
    private Set<Protocollo> protocolliSupportati;

    private String unitaMisura;
}
