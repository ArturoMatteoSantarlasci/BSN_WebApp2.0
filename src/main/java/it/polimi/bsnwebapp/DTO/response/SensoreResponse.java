package it.polimi.bsnwebapp.DTO.response;

import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

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
    private String unitaMisura;
}
