-- Migration Flyway: V8__introduce_daas_and_supply_requests.sql
-- Description: Refonte v1.2 de la terminologie metier.
--   * Seule la DAAS passe des commandes fournisseurs (table orders restructuree).
--   * Tous les flux internes (CM -> PC, PC -> DC, DC -> DAAS) deviennent des
--     demandes d'approvisionnement (nouvelles tables supply_requests / supply_request_items).
--   * Le Depot Central adresse ses demandes d'approvisionnement a la DAAS.
--   * Nouvelle table financial_transactions (suivi financier DBI).
--   * Ajout de l'acteur DAAS (type d'entite + role) et du suivi des references
--     sur les mouvements de stock (reference_supply_request_id).

-- =====================================================================
-- 1. Type d'entite DAAS (elargissement du CHECK sur entities.type)
-- =====================================================================
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'entities'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%CENTRE_MEDICAL%'
    LOOP
        EXECUTE format('ALTER TABLE entities DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE entities
    ADD CONSTRAINT chk_entities_type CHECK (type IN (
        'CENTRE_MEDICAL', 'PHARMACIE_CENTRALE', 'DEPOT_CENTRAL', 'DBI', 'DAAS', 'FOURNISSEUR'
    ));

-- =====================================================================
-- 2. Role + entite de reference DAAS
-- =====================================================================
INSERT INTO roles (name, description)
VALUES ('DAAS', 'Division des Achats et des Approvisionnements Sanitaires')
ON CONFLICT (name) DO NOTHING;

INSERT INTO entities (code, name, type)
VALUES ('DAAS', 'Division des Achats et des Approvisionnements Sanitaires', 'DAAS')
ON CONFLICT (code) DO NOTHING;

-- =====================================================================
-- 3. Demandes d'approvisionnement internes (CM -> PC, PC -> DC, DC -> DAAS)
-- =====================================================================
CREATE TABLE supply_requests (
    id BIGSERIAL PRIMARY KEY,
    request_number VARCHAR(50) NOT NULL UNIQUE,
    requesting_entity_id BIGINT NOT NULL REFERENCES entities(id),  -- Entite qui demande (CM, PC ou DC)
    supplying_entity_id BIGINT NOT NULL REFERENCES entities(id),   -- Entite sollicitee (PC, DC ou DAAS)
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'BROUILLON', 'SOUMISE', 'EN_COURS_TRAITEMENT', 'EXPEDIEE', 'LIVREE', 'REJETEE'
    )),
    rejection_reason TEXT,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    processed_by_user_id BIGINT NULL REFERENCES users(id),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_supply_request_distinct_entities CHECK (requesting_entity_id <> supplying_entity_id)
);

CREATE TABLE supply_request_items (
    id BIGSERIAL PRIMARY KEY,
    supply_request_id BIGINT NOT NULL REFERENCES supply_requests(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity_requested INT NOT NULL CHECK (quantity_requested > 0),
    quantity_approved INT CHECK (quantity_approved >= 0),       -- Quantite accordee par l'entite sollicitee
    quantity_delivered INT DEFAULT 0 CHECK (quantity_delivered >= 0)
);

-- =====================================================================
-- 4. Refonte de la table orders : commandes fournisseurs de la DAAS
-- =====================================================================
ALTER TABLE orders ADD COLUMN supplier_entity_id BIGINT NULL REFERENCES entities(id);
ALTER TABLE orders ADD COLUMN receiving_entity_id BIGINT NULL REFERENCES entities(id);
ALTER TABLE orders ADD COLUMN supply_request_id BIGINT NULL REFERENCES supply_requests(id);
ALTER TABLE orders ADD COLUMN total_amount DECIMAL(14,2) DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN rejection_reason TEXT;
ALTER TABLE orders ADD COLUMN validated_by_user_id BIGINT NULL REFERENCES users(id);
ALTER TABLE orders ADD COLUMN validated_at TIMESTAMP WITHOUT TIME ZONE NULL;
ALTER TABLE orders ADD COLUMN sent_at TIMESTAMP WITHOUT TIME ZONE NULL;
ALTER TABLE orders ADD COLUMN received_by_user_id BIGINT NULL REFERENCES users(id);
ALTER TABLE orders ADD COLUMN received_at TIMESTAMP WITHOUT TIME ZONE NULL;

-- 4a. Les anciennes commandes destinees a un Fournisseur deviennent des
--     commandes fournisseurs de la DAAS (emetteur = DAAS, reception = DC).
UPDATE orders o
SET supplier_entity_id = o.target_entity_id,
    receiving_entity_id = o.source_entity_id,
    source_entity_id = (SELECT id FROM entities WHERE code = 'DAAS'),
    status = CASE o.status
                 WHEN 'EXPEDIEE' THEN 'ENVOYEE_FOURNISSEUR'
                 WHEN 'LIVREE' THEN 'RECUE'
                 WHEN 'EN_ATTENTE_SERVIE' THEN 'BROUILLON'
                 ELSE o.status
             END
WHERE o.target_entity_id IN (SELECT id FROM entities WHERE type = 'FOURNISSEUR');

-- 4b. Les anciennes commandes internes (CM -> PC, PC -> DC) migrent vers
--     supply_requests avec leurs lignes.
INSERT INTO supply_requests (
    request_number, requesting_entity_id, supplying_entity_id, status,
    created_by_user_id, created_at, updated_at
)
SELECT
    o.order_number,
    o.source_entity_id,
    o.target_entity_id,
    CASE o.status
        WHEN 'BROUILLON' THEN 'BROUILLON'
        WHEN 'EN_ATTENTE_SERVIE' THEN 'SOUMISE'
        WHEN 'EN_ATTENTE_DBI' THEN 'SOUMISE'
        WHEN 'AJUSTEE_DBI' THEN 'EN_COURS_TRAITEMENT'
        WHEN 'VALIDEE_DBI' THEN 'EN_COURS_TRAITEMENT'
        WHEN 'EXPEDIEE' THEN 'EXPEDIEE'
        WHEN 'LIVREE' THEN 'LIVREE'
        ELSE 'REJETEE'
    END,
    o.created_by_user_id,
    o.created_at,
    o.updated_at
FROM orders o
WHERE o.target_entity_id IN (SELECT id FROM entities WHERE type IN ('PHARMACIE_CENTRALE', 'DEPOT_CENTRAL'));

INSERT INTO supply_request_items (
    supply_request_id, product_id, quantity_requested, quantity_approved, quantity_delivered
)
SELECT
    sr.id,
    oi.product_id,
    oi.quantity_requested,
    oi.quantity_adjusted,
    oi.quantity_delivered
FROM supply_requests sr
JOIN orders o ON o.order_number = sr.request_number
JOIN order_items oi ON oi.order_id = o.id;

-- 4c. Purge des anciennes commandes internes (desormais representees par supply_requests).
UPDATE stock_movements
SET reference_order_id = NULL
WHERE reference_order_id IN (
    SELECT o.id FROM orders o
    JOIN entities e ON e.id = o.target_entity_id
    WHERE e.type IN ('PHARMACIE_CENTRALE', 'DEPOT_CENTRAL')
);

DELETE FROM order_items
WHERE order_id IN (
    SELECT o.id FROM orders o
    JOIN entities e ON e.id = o.target_entity_id
    WHERE e.type IN ('PHARMACIE_CENTRALE', 'DEPOT_CENTRAL')
);

DELETE FROM orders
WHERE target_entity_id IN (SELECT id FROM entities WHERE type IN ('PHARMACIE_CENTRALE', 'DEPOT_CENTRAL'));

-- 4d. Colonnes des lignes de commande : reception (DC) + valorisation.
ALTER TABLE order_items RENAME COLUMN quantity_delivered TO quantity_received;
ALTER TABLE order_items ADD COLUMN unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00;

UPDATE order_items oi
SET unit_price = (SELECT p.unit_price FROM products p WHERE p.id = oi.product_id)
WHERE oi.unit_price = 0.00;

-- Valorisation des commandes migrées : total = somme (quantite ajustee ou demandee) x prix unitaire.
UPDATE orders o
SET total_amount = (
    SELECT COALESCE(SUM(COALESCE(oi.quantity_adjusted, oi.quantity_requested) * oi.unit_price), 0)
    FROM order_items oi
    WHERE oi.order_id = o.id
)
WHERE o.supplier_entity_id IS NOT NULL;

-- 4e. Colonnes obsoletes de l'ancien modele.
ALTER TABLE orders DROP COLUMN requires_dbi_validation;
ALTER TABLE orders DROP COLUMN target_entity_id;

-- 4f. Statuts autorises pour une commande fournisseur DAAS.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'orders'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%EN_ATTENTE_SERVIE%'
    LOOP
        EXECUTE format('ALTER TABLE orders DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status CHECK (status IN (
        'BROUILLON', 'EN_ATTENTE_DBI', 'AJUSTEE_DBI', 'VALIDEE_DBI',
        'ENVOYEE_FOURNISSEUR', 'RECUE_PARTIELLEMENT', 'RECUE', 'REJETEE'
    ));

-- =====================================================================
-- 5. Mouvements de stock : reference aux demandes d'approvisionnement
-- =====================================================================
ALTER TABLE stock_movements ADD COLUMN reference_supply_request_id BIGINT NULL REFERENCES supply_requests(id);

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_single_reference CHECK (
        reference_supply_request_id IS NULL OR reference_order_id IS NULL
    );

-- =====================================================================
-- 6. Suivi financier DBI (engagements fournisseurs & sorties valorisees)
-- =====================================================================
CREATE TABLE financial_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_type VARCHAR(40) NOT NULL CHECK (transaction_type IN (
        'ENGAGEMENT_FOURNISSEUR', 'SORTIE_STOCK_VALORISEE'
    )),
    amount DECIMAL(14,2) NOT NULL CHECK (amount >= 0),
    reference_supply_request_id BIGINT NULL REFERENCES supply_requests(id),
    reference_order_id BIGINT NULL REFERENCES orders(id),
    recorded_by_user_id BIGINT NOT NULL REFERENCES users(id),   -- Utilisateur DBI
    comment TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_financial_reference CHECK (
        (reference_supply_request_id IS NOT NULL AND reference_order_id IS NULL)
        OR (reference_supply_request_id IS NULL AND reference_order_id IS NOT NULL)
    )
);

-- =====================================================================
-- 7. Index de performance
-- =====================================================================
CREATE INDEX idx_supply_requests_status ON supply_requests(status);
CREATE INDEX idx_supply_requests_requesting ON supply_requests(requesting_entity_id);
CREATE INDEX idx_supply_requests_supplying ON supply_requests(supplying_entity_id);
CREATE INDEX idx_supply_request_items_request ON supply_request_items(supply_request_id);
CREATE INDEX idx_orders_supplier ON orders(supplier_entity_id);
CREATE INDEX idx_orders_receiving ON orders(receiving_entity_id);
CREATE INDEX idx_orders_supply_request ON orders(supply_request_id);
CREATE INDEX idx_stock_movements_supply_request ON stock_movements(reference_supply_request_id);
CREATE INDEX idx_financial_transactions_type ON financial_transactions(transaction_type);
CREATE INDEX idx_financial_transactions_order ON financial_transactions(reference_order_id);
CREATE INDEX idx_financial_transactions_supply_request ON financial_transactions(reference_supply_request_id);

-- =====================================================================
-- 8. Compte de demonstration DAAS (desactivable, cf. docs/GUIDE_DEVELOPPEUR.md)
--    Mot de passe : Demo@1234 (identique aux autres comptes de demonstration)
-- =====================================================================
INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'agent_daas', '$2a$10$QkV3TiL7SjF4RPZSRQVxi.Yq/Myzn/k2Vecq14OyS153LjH9GC4Va',
       'Ndiaye', 'Sokhna', 'sokhna.ndiaye@daas.cnss.sn', r.id, e.id, TRUE
FROM roles r, entities e
WHERE r.name = 'DAAS' AND e.code = 'DAAS'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'agent_daas');
