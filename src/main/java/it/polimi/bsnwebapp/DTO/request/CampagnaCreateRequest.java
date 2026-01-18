package it.polimi.bsnwebapp.DTO.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import java.util.List;

/**
 * DTO di richiesta per la creazione e l'avvio di una campagna.
 * Viene popolato dal payload JSON dei controller REST e passato ai service per la validazione.
 * Contiene i campi: nome, note, idPersona, idUtenteCreatore, idTipoCampagna, scriptFileName, frequenza,
 * connettivita, idDatabase, idSensori.
 */

@Data
public class CampagnaCreateRequest {
    private String nome;
    private String note;

    private Long idPersona;

    private Long idUtenteCreatore;

    // script da eseguire
    private Long idTipoCampagna;

    private String scriptFileName;

    private Integer frequenza;

    private Protocollo connettivita;

    private Long idDatabase;

    // sensori da usare
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<Long> idSensori;
}
