package cm.imf.pipeline.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

import java.util.UUID;

/**
 * Superclasse mappée commune à toutes les entités JPA.
 *
 * Fournit un identifiant public ({@code uid}) généré côté serveur, distinct
 * de la clé primaire technique ({@code id}). Cet UUID est le seul identifiant
 * exposé dans les réponses API et les URL REST — l'ID interne (Long auto-incrément)
 * n'est jamais communiqué aux clients.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Column(name = "uid", nullable = false, unique = true, updatable = false,
            columnDefinition = "uuid")
    private UUID uid;

    @PrePersist
    protected void generateUid() {
        if (uid == null) {
            uid = UUID.randomUUID();
        }
    }
}
