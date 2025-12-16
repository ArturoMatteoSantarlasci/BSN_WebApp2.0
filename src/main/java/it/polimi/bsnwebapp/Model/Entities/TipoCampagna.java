package it.polimi.bsnwebapp.Model.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity JPA che rappresenta un "tipo" di campagna (tabella dizionario).
 *
 * Nel database corrisponde alla tabella {@code tipo_campagna} (es. RESPIRO, BATTITO_CARDIACO, ...).
 * La relazione con {@link Campagna} è gestita tramite la join table {@code campagna_tipo}.
 */

@Entity
@Table(name = "tipo_campagna")
@Getter
@Setter
@NoArgsConstructor
public class TipoCampagna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo", updatable = false, nullable = false)
    private Long id;

    @Column(name = "codice", nullable = false, unique = true, length = 30)
    private String codice;

    @Column(name = "descrizione", nullable = false, length = 100)
    private String descrizione;

    public TipoCampagna(String codice, String descrizione) {
        this.codice = codice;
        this.descrizione = descrizione;
    }
}
