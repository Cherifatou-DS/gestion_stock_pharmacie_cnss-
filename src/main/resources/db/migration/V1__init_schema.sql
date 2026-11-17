-- Migration Flyway: V1__init_schema.sql
-- Description: Initialisation du schéma de la base de données PostgreSQL du système pharmaceutique

-- 1. Table des Rôles
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);

-- 2. Table des Entités (Centres Médicaux, Pharmacie Centrale, Dépôt Central, DBI, Fournisseur)
CREATE TABLE entities (
    id SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('CENTRE_MEDICAL', 'PHARMACIE_CENTRALE', 'DEPOT_CENTRAL', 'DBI', 'FOURNISSEUR')),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Table des Utilisateurs
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    role_id INT NOT NULL REFERENCES roles(id),
    entity_id INT NOT NULL REFERENCES entities(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Table des Produits Pharmaceutiques
CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    designation VARCHAR(150) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(10,2) DEFAULT 0.00,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Table des Stocks par Entité
CREATE TABLE stocks (
    id SERIAL PRIMARY KEY,
    entity_id INT NOT NULL REFERENCES entities(id),
    product_id INT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    min_threshold INT DEFAULT 10,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_entity_product UNIQUE(entity_id, product_id)
);

-- 6. Table des Commandes & Circuit de Validation
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    source_entity_id INT NOT NULL REFERENCES entities(id),
    target_entity_id INT NOT NULL REFERENCES entities(id),
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'BROUILLON', 'EN_ATTENTE_SERVIE', 'EN_ATTENTE_DBI', 
        'AJUSTEE_DBI', 'VALIDEE_DBI', 'EXPEDIEE', 'LIVREE', 'REJETEE'
    )),
    requires_dbi_validation BOOLEAN DEFAULT FALSE,
    created_by_user_id INT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Détails des Lignes de Commande
CREATE TABLE order_items (
    id SERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    quantity_requested INT NOT NULL CHECK (quantity_requested > 0),
    quantity_adjusted INT CHECK (quantity_adjusted >= 0),
    quantity_delivered INT DEFAULT 0 CHECK (quantity_delivered >= 0)
);

-- 8. Journal des Mouvements de Stock (Inventaire / Traçabilité Entrées-Sorties)
CREATE TABLE stock_movements (
    id SERIAL PRIMARY KEY,
    entity_id INT NOT NULL REFERENCES entities(id),
    product_id INT NOT NULL REFERENCES products(id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('ENTREE', 'SORTIE', 'AJUSTEMENT_INVENTAIRE')),
    quantity INT NOT NULL,
    stock_before INT NOT NULL,
    stock_after INT NOT NULL,
    reference_order_id INT NULL REFERENCES orders(id),
    created_by_user_id INT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 9. Journal d'Audit & Traçabilité des Actions Utilisateurs
CREATE TABLE action_logs (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    entity_affected VARCHAR(50) NOT NULL,
    record_id INT NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index pour optimiser les recherches fréquentes
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_source ON orders(source_entity_id);
CREATE INDEX idx_stock_movements_entity_product ON stock_movements(entity_id, product_id);
CREATE INDEX idx_action_logs_user ON action_logs(user_id);
