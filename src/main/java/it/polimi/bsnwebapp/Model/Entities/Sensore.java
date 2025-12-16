package it.polimi.bsnwebapp.Model.Entities;

import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Entity JPA che rappresenta un sensore fisico (dispositivo) utilizzato nelle campagne BSN.
 *
 * Nel database corrisponde alla tabella {@code sensore}.
 * Un sensore è identificato da:
 *   un id numerico (PK auto-increment)</li>
 *   un {@link #codice} univoco di 4 caratteri derivato dal MAC address (es. "XEXA", "5B35")
 *
 * Il campo {@link #tipo} è volutamente una stringa (es. "XIAO", "NANO33BLE") per permettere di aggiungere
 * nuovi modelli senza dover modificare il codice (evitando un enum rigido).
 *
 * L'associazione con le campagne è gestita tramite {@link CampagnaSensore} (tabella {@code campagna_sensore}),
 * perché la join table contiene un attributo extra ({@code attivo}).
 */

@Entity
@Table(name = "sensore")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class Sensore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sensore", updatable = false, nullable = false)
    private Long id;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocollo", nullable = false)
    private Protocollo protocollo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "sensore_protocollo",
            joinColumns = @JoinColumn(name = "id_sensore")
    )
    @Enumerated(EnumType.STRING)
    @EqualsAndHashCode.Exclude
    @Column(name = "protocollo", nullable = false)
    private Set<Protocollo> protocolliSupportati = new HashSet<>();


    @Column(name = "unita_misura", length = 20)
    private String unitaMisura;

    @Column(name = "codice", nullable = false, unique = true, length = 4, columnDefinition = "CHAR(4)")
    private String codice;

    // relazione con campagna_sensore (join con attributo "attivo")
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "sensore", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CampagnaSensore> campagnaSensori = new HashSet<>();

    public Sensore(String nome, String tipo, Protocollo protocollo, String unitaMisura) {
        this.nome = nome;
        this.tipo = tipo;
        this.protocollo = protocollo;
        this.unitaMisura = unitaMisura;
    }
}
