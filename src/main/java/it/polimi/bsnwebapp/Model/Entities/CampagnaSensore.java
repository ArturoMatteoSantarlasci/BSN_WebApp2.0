package it.polimi.bsnwebapp.Model.Entities;

import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity JPA che rappresenta l'associazione tra {@link Campagna} e {@link Sensore}.
 *
 * Nel DB corrisponde alla tabella {@code campagna_sensore}.
 * Questa NON è una semplice join table Many-to-Many perché contiene un attributo extra: {@code attivo}.
 * Per questo motivo viene modellata come entity intermedia (association entity).
 *
 * La PK è composta da ({@code id_campagna}, {@code id_sensore}) e viene rappresentata da {@link CampagnaSensoreId}.
 */


@Entity
@Table(name = "campagna_sensore")
@Getter
@Setter
@NoArgsConstructor
public class CampagnaSensore {

    @EmbeddedId
    private CampagnaSensoreId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("campagnaId")
    @JoinColumn(name = "id_campagna", nullable = false)
    private Campagna campagna;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("sensoreId")
    @JoinColumn(name = "id_sensore", nullable = false)
    private Sensore sensore;

    @Column(name = "attivo", nullable = false)
    private boolean attivo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocollo", nullable = false)
    private Protocollo protocollo = Protocollo.BLE;

    public CampagnaSensore(Campagna campagna, Sensore sensore, boolean attivo) {
        this.campagna = campagna;
        this.sensore = sensore;
        this.attivo = attivo;
        this.protocollo = (protocollo != null) ? protocollo : sensore.getProtocollo();
        this.id = new CampagnaSensoreId(campagna.getId(), sensore.getId());
    }
}

