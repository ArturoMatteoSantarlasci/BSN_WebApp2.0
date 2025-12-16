package it.polimi.bsnwebapp.Service;

import it.polimi.bsnwebapp.DTO.request.SensoreCreateRequest;
import it.polimi.bsnwebapp.DTO.response.SensoreResponse;
import it.polimi.bsnwebapp.Model.Entities.Sensore;
import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import it.polimi.bsnwebapp.Repository.SensoreRepository;
import it.polimi.bsnwebapp.exception.BadRequestException;
import it.polimi.bsnwebapp.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SensoreService {

    private final SensoreRepository sensoreRepository;

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
        s.setNome(request.getNome().trim());
        s.setTipo(request.getTipo().trim());
        s.setProtocollo(def);
        s.setUnitaMisura(request.getUnitaMisura());
        s.setProtocolliSupportati(supportati);

        Sensore saved = sensoreRepository.save(s);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SensoreResponse> listaSensori() {
        return sensoreRepository.findAll().stream().map(this::toResponse).toList();
    }

    private String normalizeCodice(String codice) {
        if (codice == null) throw new BadRequestException("Codice mancante");
        String c = codice.trim().toUpperCase();
        if (c.length() != 4) throw new BadRequestException("Il codice deve essere lungo 4 caratteri");
        return c;
    }

    private SensoreResponse toResponse(Sensore s) {
        return new SensoreResponse(
                s.getId(),
                s.getCodice(),
                s.getNome(),
                s.getTipo(),
                s.getProtocollo(),
                s.getProtocolliSupportati(),
                s.getUnitaMisura()
        );
    }
}
