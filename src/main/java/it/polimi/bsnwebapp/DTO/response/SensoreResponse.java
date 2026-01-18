package it.polimi.bsnwebapp.DTO.response;

import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import it.polimi.bsnwebapp.Model.Enum.TipoMisura;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO di risposta per i dettagli di un sensore registrato.
 * Viene serializzato dai controller REST per alimentare la UI o i client esterni.
 * Campi inclusi: id, codice, nome, tipo, protocolloDefault, protocolliSupportati, misureSupportate.
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SensoreResponse {
    private Long id;
    private String codice;
    private String nome;
    private String tipo;
    private Protocollo protocolloDefault;
    private Set<Protocollo> protocolliSupportati;
    private Set<TipoMisura> misureSupportate;
}
