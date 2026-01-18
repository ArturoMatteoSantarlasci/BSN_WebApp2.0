package it.polimi.bsnwebapp.Service;

import it.polimi.bsnwebapp.Config.MqttProperties;
import it.polimi.bsnwebapp.DTO.request.CampagnaCreateRequest;
import it.polimi.bsnwebapp.DTO.response.CampagnaResponse;
import it.polimi.bsnwebapp.Model.Entities.*;
import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import it.polimi.bsnwebapp.Model.Enum.StatoCampagna;
import it.polimi.bsnwebapp.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service principale per il ciclo di vita delle campagne.
 * Crea campagne, valida configurazioni, associa sensori e tipi, avvia script Python e aggiorna lo stato.
 * Converte Campagna in CampagnaResponse e gestisce terminazioni e pulizia processi.
 */

@Service
public class CampagnaService {

    private static final Logger logger = LoggerFactory.getLogger(CampagnaService.class);

    //dice a Spring di cercare un oggetto già pronto (chiamato Bean) nella sua memoria e di "iniettarlo"
    // automaticamente nella tua classe, senza che tu debba fare new Classe()
    @Autowired
    private CampagnaRepository campagnaRepository;

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private UtenteRepository utenteRepository;

    @Autowired
    private TipoCampagnaRepository tipoCampagnaRepository;

    @Autowired
    private SensoreRepository sensoreRepository;

    @Autowired
    private CampagnaSensoreRepository campagnaSensoreRepository;

    @Autowired
    private CampagnaProcessManager processManager;

    @Autowired
    private DatabaseConfigRepository databaseConfigRepository;

    @Autowired
    private MqttProperties mqttProperties;

    // Percorso del folder contenente gli script Python letto da application.properties
    @Value("${app.python.scripts.folder}")
    private String scriptsFolder;

    /**
     * Crea e avvia immediatamente una nuova campagna.
     * Il flusso prevede: validazione input, caricamento entita, creazione campagna,
     * associazione sensori, salvataggio, ricarica relazioni e avvio script Python.
     * La campagna viene salvata in stato IN_CORSO con dataInizio impostata all'istante di avvio.
     *
     * @param request Oggetto con i dati della campagna da creare
     * @return CampagnaResponse con tutti i dettagli della campagna avviata
     * @throws IOException Se l'avvio degli script fallisce
     */
    @Transactional
    public CampagnaResponse creaEAvviaCampagna(CampagnaCreateRequest request) throws IOException {
        logger.info("Creazione e avvio immediato campagna: {}", request.getNome());

        // Validazione dati obbligatori
        if (request.getNome() == null || request.getNome().isBlank()) {
            throw new IllegalArgumentException("Il nome della campagna è obbligatorio");
        }
        if (request.getIdPersona() == null) {
            throw new IllegalArgumentException("È obbligatorio specificare il paziente");
        }
        if (request.getIdUtenteCreatore() == null) {
            throw new IllegalArgumentException("È obbligatorio specificare l'utente creatore");
        }
        if (request.getIdTipoCampagna() == null) {
            throw new IllegalArgumentException("Selezionare un tipo di campagna");
        }
        if (request.getIdSensori() == null || request.getIdSensori().isEmpty()) {
            throw new IllegalArgumentException("Selezionare almeno un sensore");
        }
        if (request.getFrequenza() == null || request.getFrequenza() <= 0) {
            throw new IllegalArgumentException("Specificare una frequenza di campionamento valida");
        }
        if (request.getConnettivita() == null) {
            throw new IllegalArgumentException("Specificare la connettività");
        }

        // Recupero entità dal database
        Persona persona = personaRepository.findById(request.getIdPersona())
                .orElseThrow(() -> new IllegalArgumentException("Persona non trovata con ID: " + request.getIdPersona()));

        Utente utente = utenteRepository.findById(request.getIdUtenteCreatore())
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato con ID: " + request.getIdUtenteCreatore()));

        TipoCampagna tipo = tipoCampagnaRepository.findById(request.getIdTipoCampagna())
                .orElseThrow(() -> new IllegalArgumentException("Tipo campagna non trovato con ID: " + request.getIdTipoCampagna()));
        Set<TipoCampagna> tipi = new java.util.HashSet<>();
        tipi.add(tipo);

        List<Sensore> sensori = request.getIdSensori().stream()
                .map(idSensore -> sensoreRepository.findById(idSensore)
                        .orElseThrow(() -> new IllegalArgumentException("Sensore non trovato con ID: " + idSensore)))
                .collect(Collectors.toList());

        List<String> incompatibili = sensori.stream()
                .filter(sensore -> !supportsProtocol(sensore, request.getConnettivita()))
                .map(sensore -> sensore.getCodice() == null ? String.valueOf(sensore.getId()) : sensore.getCodice())
                .collect(Collectors.toList());
        if (!incompatibili.isEmpty()) {
            throw new IllegalArgumentException("Sensori non compatibili con la connettivita selezionata: "
                    + String.join(", ", incompatibili));
        }

        // Creazione entità Campagna
        Campagna campagna = new Campagna();
        campagna.setNome(request.getNome());
        campagna.setNote(request.getNote());
        campagna.setPersona(persona);
        campagna.setUtenteCreatore(utente);
        campagna.setStato(StatoCampagna.IN_CORSO);
        campagna.setTipi(tipi);
        campagna.setDataInizio(LocalDateTime.now());
        campagna.setFrequenza(request.getFrequenza());
        campagna.setConnettivita(request.getConnettivita());
        Long dbId = request.getIdDatabase();
        if (dbId != null && dbId > 0) {
            DatabaseConfig dbConfig = databaseConfigRepository.findById(dbId)
                    .orElseThrow(() -> new IllegalArgumentException("Database non trovato"));
            campagna.setDbHost(dbConfig.getHost());
            campagna.setDbName(dbConfig.getDbName());
        }

        String scriptName = request.getScriptFileName();
        if (scriptName == null || scriptName.isBlank()) {
            scriptName = tipo.getScriptFileName();
        }
        if (scriptName == null || scriptName.isBlank()) {
            throw new IllegalArgumentException("Script non valido per il tipo selezionato");
        }
        campagna.setScriptFileName(scriptName);

        // Salvataggio campagna per ottenere l'ID
        campagna = campagnaRepository.save(campagna);

        // Creazione associazioni CampagnaSensore
        for (Sensore sensore : sensori) {
            CampagnaSensore cs = new CampagnaSensore(campagna, sensore, true);
            campagnaSensoreRepository.save(cs);
        }

        // Ricarica la campagna per ottenere tutte le relazioni aggiornate
        campagna = campagnaRepository.findById(campagna.getId())
                .orElseThrow(() -> new IllegalStateException("Impossibile ricaricare la campagna appena creata"));

        avviaProcessiCampagna(campagna);

        logger.info("Campagna avviata con successo. ID: {}", campagna.getId());

        return convertToResponse(campagna);
    }

    /**
     * Converte un'entità Campagna in CampagnaResponse DTO.
     */
    /**
     * Converte una entita Campagna in DTO CampagnaResponse.
     * Mappa i campi base, i dati della persona, dell'utente creatore, i tipi campagna e i sensori associati.
     *
     * @param campagna entita persistita da convertire
     * @return DTO pronto per la serializzazione
     */
    public CampagnaResponse convertToResponse(Campagna campagna) {
        CampagnaResponse response = new CampagnaResponse();
        response.setId(campagna.getId());
        response.setNome(campagna.getNome());
        response.setNote(campagna.getNote());
        response.setStato(campagna.getStato());
        response.setDataInizio(campagna.getDataInizio());
        response.setDataFine(campagna.getDataFine());
        response.setDurataTotale(formatDuration(campagna.getDataInizio(), campagna.getDataFine()));
        response.setFrequenza(campagna.getFrequenza());
        response.setDurata(campagna.getDurata());
        response.setConnettivita(campagna.getConnettivita());
        response.setScriptFileName(campagna.getScriptFileName());
        response.setDbHost(campagna.getDbHost());
        response.setDbName(campagna.getDbName());

        // Informazioni persona
        response.setIdPersona(campagna.getPersona().getId());
        response.setNomePersona(campagna.getPersona().getNome());
        response.setCognomePersona(campagna.getPersona().getCognome());
        response.setNotePersona(campagna.getPersona().getNote());

        // Informazioni utente creatore
        response.setIdUtenteCreatore(campagna.getUtenteCreatore().getId());
        response.setUsernameCreatore(campagna.getUtenteCreatore().getUsername());

        // Tipi campagna
        List<CampagnaResponse.TipoCampagnaInfo> tipiInfo = campagna.getTipi().stream()
                .map(tipo -> new CampagnaResponse.TipoCampagnaInfo(
                        tipo.getId(),
                        tipo.getCodice(),
                        tipo.getDescrizione(),
                        null, // nomeApplicazione - da implementare se necessario
                        tipo.getScriptFileName() // eseguibile
                ))
                .collect(Collectors.toList());
        response.setTipiCampagna(tipiInfo);

        // Sensori
        List<CampagnaResponse.SensoreInfo> sensoriInfo = campagna.getCampagnaSensori().stream()
                .map(cs -> new CampagnaResponse.SensoreInfo(
                        cs.getSensore().getId(),
                        cs.getSensore().getNome(),
                        cs.getSensore().getTipo(),
                        cs.getSensore().getCodice(),
                        cs.getProtocollo(), // protocollo dalla relazione CampagnaSensore
                        cs.getSensore().getMisureSupportate(),
                        cs.isAttivo()
                ))
                .collect(Collectors.toList());
        response.setSensori(sensoriInfo);

        return response;
    }

    /**
     * Calcola la durata tra due timestamp in formato HH:mm:ss.
     * Ritorna null se uno dei due valori e' assente o se la durata risulta negativa.
     */
    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds < 0) {
            return null;
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Recupera i dettagli di una campagna specifica.
     * Esegue una query per id e converte l'entita in DTO.
     *
     * @param id ID della campagna
     * @return CampagnaResponse con tutti i dettagli
     */
    public CampagnaResponse getCampagnaById(Long id) {
        logger.info("Recupero dettagli campagna ID: {}", id);
        Campagna campagna = campagnaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campagna non trovata con ID: " + id));
        return convertToResponse(campagna);
    }

    /**
     * Recupera tutte le campagne associate a un paziente.
     * Verifica l'esistenza della persona prima di eseguire la query.
     *
     * @param idPersona ID del paziente
     * @return Lista di CampagnaResponse
     */
    public List<CampagnaResponse> getCampagneByPersona(Long idPersona) {
        logger.info("Recupero campagne per paziente ID: {}", idPersona);

        // Verifica che la persona esista
        if (!personaRepository.existsById(idPersona)) {
            throw new IllegalArgumentException("Persona non trovata con ID: " + idPersona);
        }

        List<Campagna> campagne = campagnaRepository.findByPersona_Id(idPersona);
        return campagne.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Recupera tutte le campagne create da un utente medico.
     * Verifica l'esistenza dell'utente prima di eseguire la query.
     *
     * @param idUtente ID dell'utente medico
     * @return Lista di CampagnaResponse
     */
    public List<CampagnaResponse> getCampagneByUtente(Long idUtente) {
        logger.info("Recupero campagne create dall'utente ID: {}", idUtente);

        // Verifica che l'utente esista
        if (!utenteRepository.existsById(idUtente)) {
            throw new IllegalArgumentException("Utente non trovato con ID: " + idUtente);
        }

        List<Campagna> campagne = campagnaRepository.findByUtenteCreatore_Id(idUtente);
        return campagne.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }


    /**
     * Avvia gli script Python associati a una campagna.
     * Costruisce il comando con gli argomenti richiesti dallo script e imposta le variabili d'ambiente.
     * Registra il processo in CampagnaProcessManager per il monitoraggio successivo.
     *
     * @param campagna entita campagna gia salvata con id valorizzato
     * @throws IOException se lo script non esiste o il processo non puo essere avviato
     */
    private void avviaProcessiCampagna(Campagna campagna) throws IOException {
        if (campagna == null || campagna.getId() == null) {
            throw new IllegalArgumentException("Campagna non valida");
        }
        Long id = campagna.getId();

        if (processManager.isCampagnaAttiva(id)) {
            throw new IllegalStateException("Impossibile avviare: Processi già attivi rilevati in memoria.");
        }

        // Recupero il Tipo di Campagna (uno solo per campagna)
        Set<TipoCampagna> tipi = campagna.getTipi();
        if (tipi == null || tipi.isEmpty()) {
            throw new IllegalStateException("La campagna non ha nessun Tipo associato (nessuno script da lanciare).");
        }
        if (tipi.size() != 1) {
            throw new IllegalStateException("La campagna deve avere un solo tipo/script associato.");
        }

        TipoCampagna tipo = tipi.iterator().next();
        String scriptName = campagna.getScriptFileName();
        if (scriptName == null || scriptName.isBlank()) {
            scriptName = tipo.getScriptFileName();
        }
        if (scriptName == null || scriptName.isBlank()) {
            throw new IllegalStateException("Nome script mancante per il tipo selezionato.");
        }

        // Costruzione percorso assoluto: cartella + nome_file
        File scriptFile = new File(scriptsFolder, scriptName);
        if (!scriptFile.exists()) {
            throw new IOException("Script non trovato: " + scriptFile.getAbsolutePath());
        }

        // Determina il comando Python in base al sistema operativo
        String pythonCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";

        try {
            logger.info("Avvio script: {} per Tipo: {}", scriptName, tipo.getDescrizione());

            // Prepara la lista dei sensori in formato CSV per lo script Python
            String sensoriCsv = campagnaSensoreRepository.findByCampagna_Id(id).stream()
                    .filter(CampagnaSensore::isAttivo)
                    .map(cs -> cs.getSensore().getCodice())
                    .filter(codice -> codice != null && !codice.isBlank())
                    .collect(Collectors.joining(","));
            if (sensoriCsv.isBlank()) {
                throw new IllegalStateException("Lista sensori vuota: selezionare almeno un sensore valido");
            }

            // Nome campagna come cname
            String cname = String.valueOf(id);

            // nth_start sempre 0 per nuove campagne
            String nthStart = "0";

            // Preparazione comando con argomenti posizionali
            // sys.argv[1] = lista IMU, sys.argv[2] = cname, sys.argv[3] = nth_start
            ProcessBuilder builder = new ProcessBuilder(
                    pythonCmd,
                    scriptFile.getAbsolutePath(),
                    sensoriCsv,  // argv[1]
                    cname,       // argv[2]
                    nthStart     // argv[3]
            );

            // Imposta variabili d'ambiente aggiuntive per informazioni supplementari
            var env = builder.environment();
            env.put("BSN_CAMPAIGN_ID", String.valueOf(id));
            env.put("BSN_TYPE_CODE", tipo.getCodice());
            env.put("BSN_SENSORS", sensoriCsv);
            String mqttBroker = mqttProperties.getBroker();
            String mqttHost = extractMqttHost(mqttBroker);
            if (mqttHost != null) {
                env.put("BSN_MQTT_BROKER", mqttHost);
            }
            String mqttPort = resolveMqttPort(mqttBroker);
            if (mqttPort != null) {
                env.put("BSN_MQTT_PORT", mqttPort);
            }
            String mqttTopic = mqttProperties.getTopic();
            if (mqttTopic != null && !mqttTopic.isBlank()) {
                env.put("BSN_MQTT_TOPIC", mqttTopic);
            }
            if (campagna.getFrequenza() != null) {
                env.put("BSN_SAMPLING_HZ", String.valueOf(campagna.getFrequenza()));
            }
            if (campagna.getConnettivita() != null) {
                env.put("BSN_CONNECTIVITY", campagna.getConnettivita().name());
            }
            if (campagna.getDbHost() != null) {
                env.put("BSN_DB_HOST", campagna.getDbHost());
            }
            if (campagna.getDbName() != null) {
                env.put("BSN_DB_NAME", campagna.getDbName());
            }

            // Redirige output nella console di IntelliJ per debug
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            // avvio effettivo
            Process processo = builder.start();
            processManager.registraProcesso(id, processo);

        } catch (Exception e) {
            logger.error("Errore durante l'avvio script. Terminazione processo...", e);
            terminaProcessiFisici(id);
            throw e;
        }
    }

    /**
     * Termina forzatamente una campagna in corso.
     * Uccide il processo Python associato e aggiorna il DB.
     *
     * @param id ID della campagna
     * @return L'entità Campagna aggiornata
     */
    @Transactional
    public Campagna terminaCampagna(Long id) {
        logger.info("Richiesta terminazione campagna ID: {}", id);

        Campagna campagna = campagnaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campagna non trovata"));

        // 1. Uccidi i processi OS
        terminaProcessiFisici(id);

        // 2. Aggiorna DB
        campagna.setStato(StatoCampagna.TERMINATA);
        campagna.setDataFine(LocalDateTime.now());

        return campagnaRepository.save(campagna);
    }

    private String extractMqttHost(String broker) {
        if (broker == null) {
            return null;
        }
        String value = broker.trim();
        if (value.isEmpty()) {
            return null;
        }
        int schemeIdx = value.indexOf("://");
        if (schemeIdx >= 0) {
            value = value.substring(schemeIdx + 3);
        }
        int slashIdx = value.indexOf('/');
        if (slashIdx >= 0) {
            value = value.substring(0, slashIdx);
        }
        int colonIdx = value.lastIndexOf(':');
        if (colonIdx > 0) {
            value = value.substring(0, colonIdx);
        }
        return value.isBlank() ? null : value;
    }

    private String resolveMqttPort(String broker) {
        String port = extractMqttPort(broker);
        return port == null ? "1883" : port;
    }

    private String extractMqttPort(String broker) {
        if (broker == null) {
            return null;
        }
        String value = broker.trim();
        if (value.isEmpty()) {
            return null;
        }
        int schemeIdx = value.indexOf("://");
        if (schemeIdx >= 0) {
            value = value.substring(schemeIdx + 3);
        }
        int slashIdx = value.indexOf('/');
        if (slashIdx >= 0) {
            value = value.substring(0, slashIdx);
        }
        int colonIdx = value.lastIndexOf(':');
        if (colonIdx < 0 || colonIdx == value.length() - 1) {
            return null;
        }
        String port = value.substring(colonIdx + 1).trim();
        return port.isEmpty() ? null : port;
    }

    /**
     * Elimina una campagna terminata appartenente all'utente creatore.
     * Blocca l'operazione se la campagna non e' in stato TERMINATA o se l'utente non e' proprietario.
     *
     * @param id ID della campagna
     * @param idUtente ID dell'utente autenticato
     */
    @Transactional
    public void eliminaCampagnaTerminata(Long id, Long idUtente) {
        logger.info("Richiesta eliminazione campagna ID: {} da utente {}", id, idUtente);

        Campagna campagna = campagnaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campagna non trovata"));

        if (campagna.getUtenteCreatore() == null || campagna.getUtenteCreatore().getId() == null
                || !campagna.getUtenteCreatore().getId().equals(idUtente)) {
            throw new IllegalArgumentException("Non autorizzato a eliminare questa campagna");
        }

        if (campagna.getStato() != StatoCampagna.TERMINATA) {
            throw new IllegalStateException("Puoi eliminare solo campagne terminate");
        }

        terminaProcessiFisici(id);
        campagnaRepository.delete(campagna);
    }

    /**
     * Termina i processi OS associati alla campagna e pulisce la memoria.
     * Non aggiorna lo stato DB: la persistenza viene gestita dal chiamante.
     */
    private void terminaProcessiFisici(Long id) {
        List<Process> processi = processManager.getProcessi(id);
        int uccisi = 0;

        for (Process p : processi) {
            if (p.isAlive()) {
                p.destroy(); // Invia SIGTERM
                uccisi++;
            }
        }

        processManager.pulisciMemoriaCampagna(id);
        logger.info("Terminati {} processi per campagna {}", uccisi, id);
    }

    private boolean supportsProtocol(Sensore sensore, Protocollo protocollo) {
        if (sensore == null || protocollo == null) {
            return false;
        }
        Set<Protocollo> supportati = sensore.getProtocolliSupportati();
        if (supportati != null && !supportati.isEmpty()) {
            return supportati.contains(protocollo);
        }
        return sensore.getProtocollo() != null && sensore.getProtocollo() == protocollo;
    }
}
