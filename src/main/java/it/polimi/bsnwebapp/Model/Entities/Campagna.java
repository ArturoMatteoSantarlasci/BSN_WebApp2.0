package it.polimi.bsnwebapp.Model.Entities;

import it.polimi.bsnwebapp.Model.Enum.StatoCampagna;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity JPA che rappresenta una campagna di raccolta dati BSN.
 *
 * Nel database corrisponde alla tabella {@code campagna}.
 * Una campagna contiene:
 *   dati descrittivi (nome, date, note, stato)
 *   riferimenti a {@link Persona} e all'utente creatore {@link Utente}
 *   un insieme di {@link TipoCampagna} (relazione Many-to-Many tramite {@code campagna_tipo})
 *   un insieme di sensori associati tramite {@link CampagnaSensore} (tabella {@code campagna_sensore})
 */

@Entity
@Table(name = "campagna")
@Getter
@Setter
@NoArgsConstructor
public class Campagna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_campagna", updatable = false, nullable = false)
    private Long id;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "data_inizio", nullable = false)
    private LocalDateTime dataInizio;

    @Column(name = "data_fine")
    private LocalDateTime dataFine;

    @Enumerated(EnumType.STRING)
    @Column(name = "stato", nullable = false)
    private StatoCampagna stato = StatoCampagna.PIANIFICATA;

    @Lob
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    //  RELAZIONI MANY-TO-ONE (Foreign Keys)

    /**
     * Persona a cui la campagna è riferita (FK: {@code id_persona}).
     * Con LAZY, quando leggi una Campagna dal DB:
     *  •   JPA non carica subito la Persona.
     * 	•	Al posto di persona mette un “proxy” (un oggetto finto).
     * 	•	Appena fai campagna.getPersona() (in un contesto dove la sessione è aperta), allora fa una query
    */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_persona", nullable = false)
    @ToString.Exclude // Evita crash per loop infinito
    private Persona persona;

    /**
     * Utente che ha creato la campagna (FK: {@code id_utente}).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_utente_creatore", nullable = false)
    @ToString.Exclude
    private Utente utenteCreatore;

    //  RELAZIONI COMLPESSE

    // 1. Campagna <-> Tipo
    // Non serve entity intermedia perché la tabella di mezzo non ha colonne extra
    // Join table pura: campagna_tipo (solo FK) => ManyToMany
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "campagna_tipo",
            joinColumns = @JoinColumn(name = "id_campagna"),
            inverseJoinColumns = @JoinColumn(name = "id_tipo")
    )
    @ToString.Exclude
    private Set<TipoCampagna> tipi = new HashSet<>();

    // 2. Campagna <-> Sensore
    // Qui serve l'Entity intermedia "CampagnaSensore" perché c'è il campo "attivo"
    // Join table con attributo: campagna_sensore (attivo) => entity intermedia dedicata
    @OneToMany(mappedBy = "campagna", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<CampagnaSensore> campagnaSensori = new HashSet<>();

    public Campagna(String nome, LocalDateTime dataInizio, LocalDateTime dataFine,
                    Persona persona, Utente utenteCreatore, StatoCampagna stato, String note) {
        this.nome = nome;
        this.dataInizio = dataInizio;
        this.dataFine = dataFine;
        this.persona = persona;
        this.utenteCreatore = utenteCreatore;
        this.stato = (stato != null) ? stato : StatoCampagna.PIANIFICATA; //Default
        this.note = note;
    }
}
