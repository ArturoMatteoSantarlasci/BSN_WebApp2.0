package it.polimi.bsnwebapp.Service;

import it.polimi.bsnwebapp.DTO.request.SensoreCreateRequest;
import it.polimi.bsnwebapp.DTO.response.SensoreResponse;
import it.polimi.bsnwebapp.Model.Entities.Sensore;
import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import it.polimi.bsnwebapp.Model.Enum.TipoMisura;
import it.polimi.bsnwebapp.Repository.CampagnaSensoreRepository;
import it.polimi.bsnwebapp.Repository.SensoreRepository;
import it.polimi.bsnwebapp.exception.BadRequestException;
import it.polimi.bsnwebapp.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service per la gestione dei sensori.
 * Normalizza il codice (4 caratteri), verifica unicita, imposta protocolli supportati e salva su DB.
 * Espone anche la lista sensori e la conversione in SensoreResponse.
 */

@Service
@RequiredArgsConstructor
public class SensoreService {

    private final SensoreRepository sensoreRepository;
    private final CampagnaSensoreRepository campagnaSensoreRepository;

    /**
     * Crea un nuovo sensore applicando le regole di normalizzazione.
     * Il codice viene forzato a 4 caratteri maiuscoli e impostato anche come nome.
     * Verifica duplicati e completa i protocolli supportati con quello di default.
     *
     * @param request dati del sensore da creare
     * @return sensore creato in formato DTO
     */
    @Transactional
    public SensoreResponse creaSensore(SensoreCreateRequest request) {
        String codice = normalizeCodice(request.getCodice());

        if (sensoreRepository.existsByCodice(codice)) {
            throw new ConflictException("Codice sensore già presente");
        }

        Protocollo def = request.getProtocolloDefault();
        if (def == null) throw new BadRequestException("Protocollo di default mancante");

        Set<Protocollo> supportati = (request.getProtocolliSupportati() == null)
                ? new HashSet<>()
                : new HashSet<>(request.getProtocolliSupportati());

        if (supportati.isEmpty()) supportati.add(def);
        if (!supportati.contains(def)) supportati.add(def);

        Sensore s = new Sensore();
        s.setCodice(codice);
        s.setNome(codice);
        s.setTipo(request.getTipo().trim());
        s.setProtocollo(def);
        s.setProtocolliSupportati(supportati);
        Set<TipoMisura> misureRaw = request.getMisureSupportate();
        Set<TipoMisura> misure;
        if (misureRaw == null) {
            misure = EnumSet.allOf(TipoMisura.class);
        } else if (misureRaw.isEmpty()) {
            throw new BadRequestException("Selezionare almeno una misura supportata");
        } else {
            misure = EnumSet.copyOf(misureRaw);
        }
        s.setMisureSupportate(misure);

        Sensore saved = sensoreRepository.save(s);
        return toResponse(saved);
    }

    /**
     * Restituisce l'elenco dei sensori registrati.
     *
     * @return lista di sensori in formato DTO
     */
    @Transactional(readOnly = true)
    public List<SensoreResponse> listaSensori() {
        return sensoreRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Elimina un sensore se non e' associato ad alcuna campagna.
     *
     * @param id ID del sensore
     */
    @Transactional
    public void eliminaSensore(Long id) {
        if (id == null) {
            throw new BadRequestException("ID sensore mancante");
        }
        if (campagnaSensoreRepository.existsBySensore_Id(id)) {
            throw new BadRequestException("Impossibile eliminare: sensore associato a campagne");
        }
        Sensore sensore = sensoreRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Sensore non trovato"));
        sensoreRepository.delete(sensore);
    }

    /**
     * Normalizza il codice sensore.
     * Richiede una stringa lunga 4 caratteri e la converte in maiuscolo.
     *
     * @param codice codice inserito dall'utente
     * @return codice normalizzato
     */
    private String normalizeCodice(String codice) {
        if (codice == null) throw new BadRequestException("Codice mancante");
        String c = codice.trim().toUpperCase();
        if (c.length() != 4) throw new BadRequestException("Il codice deve essere lungo 4 caratteri");
        return c;
    }

    /**
     * Converte l'entita Sensore in DTO.
     *
     * @param s entita sensore persistita
     * @return DTO con i dati principali del sensore
     */
    private SensoreResponse toResponse(Sensore s) {
        return new SensoreResponse(
                s.getId(),
                s.getCodice(),
                s.getNome(),
                s.getTipo(),
                s.getProtocollo(),
                s.getProtocolliSupportati(),
                s.getMisureSupportate()
        );
    }
}
