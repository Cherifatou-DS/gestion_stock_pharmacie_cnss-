package com.pharmacy.stock.modules.supply.entity;

import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Demande d'approvisionnement interne entre entités : CM → PC, PC → DC ou
 * DC → DAAS (les seules relations autorisées, contrôlées dans
 * {@code SupplyRequestServiceImpl}).
 */
@Entity
@Table(name = "supply_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, unique = true, length = 50)
    private String requestNumber;

    /** Entité demandeuse (CM, PC ou DC). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_entity_id", nullable = false)
    private StructureEntity requestingEntity;

    /** Entité sollicitée (PC, DC ou DAAS). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplying_entity_id", nullable = false)
    private StructureEntity supplyingEntity;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    /** Utilisateur de l'entité sollicitée ayant traité la demande. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_user_id")
    private User processedByUser;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "supplyRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupplyRequestItem> requestItems = new ArrayList<>();
}
