package it.polimi.bsnwebapp.DTO.request;

import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import it.polimi.bsnwebapp.Model.Enum.TipoMisura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

/**
 * DTO di richiesta per la creazione di un sensore.
 * Viene popolato dal payload JSON dei controller REST e passato ai service per la validazione.
 * Contiene i campi: codice, nome, tipo, protocolloDefault, protocolliSupportati, misureSupportate.
 */

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

    // opzionale: se null/empty => tutte le misure disponibili
    private Set<TipoMisura> misureSupportate;
}
