-- Migration Flyway: V5__insert_demo_accounts.sql
-- Description: Comptes de demonstration pour chaque role + produits et stocks de demonstration
-- Mot de passe commun pour tous les comptes de demonstration : Demo@1234
-- (hash BCrypt ; a desactiver en production, cf. docs/GUIDE_DEVELOPPEUR.md)

-- 1. Comptes de demonstration (un par role)
INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'resp_pharmacie', '$2a$10$QkV3TiL7SjF4RPZSRQVxi.Yq/Myzn/k2Vecq14OyS153LjH9GC4Va',
       'Ndiaye', 'Awa', 'awa.ndiaye@pc.cnss.sn', r.id, e.id, TRUE
FROM roles r, entities e
WHERE r.name = 'RESPONSABLE_PHARMACIE' AND e.code = 'PC'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'resp_pharmacie');

INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'resp_depot', '$2a$10$QkV3TiL7SjF4RPZSRQVxi.Yq/Myzn/k2Vecq14OyS153LjH9GC4Va',
       'Faye', 'Moussa', 'moussa.faye@dc.cnss.sn', r.id, e.id, TRUE
FROM roles r, entities e
WHERE r.name = 'RESPONSABLE_DEPOT' AND e.code = 'DC'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'resp_depot');

INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'chef_cm01', '$2a$10$QkV3TiL7SjF4RPZSRQVxi.Yq/Myzn/k2Vecq14OyS153LjH9GC4Va',
       'Diallo', 'Fatou', 'fatou.diallo@cm01.cnss.sn', r.id, e.id, TRUE
FROM roles r, entities e
WHERE r.name = 'CHEF_SERVICE' AND e.code = 'CM01'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'chef_cm01');

INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'agent_cm02', '$2a$10$QkV3TiL7SjF4RPZSRQVxi.Yq/Myzn/k2Vecq14OyS153LjH9GC4Va',
       'Sarr', 'Ibrahima', 'ibrahima.sarr@cm02.cnss.sn', r.id, e.id, TRUE
FROM roles r, entities e
WHERE r.name = 'AGENT_APPROVISIONNEMENT' AND e.code = 'CM02'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'agent_cm02');

INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'dbi_valideur', '$2a$10$QkV3TiL7SjF4RPZSRQVxi.Yq/Myzn/k2Vecq14OyS153LjH9GC4Va',
       'Gueye', 'Aminata', 'aminata.gueye@dbi.cnss.sn', r.id, e.id, TRUE
FROM roles r, entities e
WHERE r.name = 'DBI' AND e.code = 'DBI'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'dbi_valideur');

INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'magasinier', '$2a$10$QkV3TiL7SjF4RPZSRQVxi.Yq/Myzn/k2Vecq14OyS153LjH9GC4Va',
       'Ba', 'Cheikh', 'cheikh.ba@dc.cnss.sn', r.id, e.id, TRUE
FROM roles r, entities e
WHERE r.name = 'MAGASINIER' AND e.code = 'DC'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'magasinier');

-- 2. Produits de demonstration (reprise des references du catalogue)
INSERT INTO products (code, designation, unit, unit_price)
SELECT 'MED001', 'Paracetamol 500mg', 'Boite', 500.00
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = 'MED001');

INSERT INTO products (code, designation, unit, unit_price)
SELECT 'MED002', 'Cotrimazole creme', 'Tube', 1500.00
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = 'MED002');

INSERT INTO products (code, designation, unit, unit_price)
SELECT 'MED003', 'Seringue 5ml', 'Unite', 100.00
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = 'MED003');

-- 3. Stocks de demonstration (PC et DC ; aucun effet si deja presents)
INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
SELECT e.id, p.id, 250, 50, CURRENT_TIMESTAMP
FROM entities e, products p
WHERE e.code = 'PC' AND p.code = 'MED001'
  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id);

INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
SELECT e.id, p.id, 80, 20, CURRENT_TIMESTAMP
FROM entities e, products p
WHERE e.code = 'PC' AND p.code = 'MED002'
  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id);

INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
SELECT e.id, p.id, 500, 50, CURRENT_TIMESTAMP
FROM entities e, products p
WHERE e.code = 'DC' AND p.code = 'MED001'
  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id);

INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
SELECT e.id, p.id, 200, 20, CURRENT_TIMESTAMP
FROM entities e, products p
WHERE e.code = 'DC' AND p.code = 'MED002'
  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id);
