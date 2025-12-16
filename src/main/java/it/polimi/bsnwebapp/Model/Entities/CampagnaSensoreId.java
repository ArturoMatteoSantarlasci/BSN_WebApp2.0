package it.polimi.bsnwebapp.Model.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Chiave primaria composta per {@link CampagnaSensore}.
 *
 * Nel DB la tabella {@code campagna_sensore} ha PK composta: ({@code id_campagna}, {@code id_sensore}).
 * In JPA questa PK viene modellata con un {@code @Embeddable} usato come {@code @EmbeddedId}.
 *
 * È fondamentale avere {@code equals/hashCode} corretti sulle chiavi composte, perché Hibernate/JPA usano
 * questi metodi per gestire identità e caching delle entity.
 */

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode //per le chiavi composte
public class CampagnaSensoreId implements Serializable {

    @Column(name = "id_campagna")
    private Long campagnaId;

    @Column(name = "id_sensore")
    private Long sensoreId;

}
