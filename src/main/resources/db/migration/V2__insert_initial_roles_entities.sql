-- Migration Flyway: V2__insert_initial_roles_entities.sql
-- Description: Insertion du jeu de données de référence (Rôles, Entités de base)

-- Insertion des rôles
INSERT INTO roles (name, description) VALUES
('ADMIN', 'Administrateur système'),
('RESPONSABLE_PHARMACIE', 'Responsable de la Pharmacie Centrale'),
('RESPONSABLE_DEPOT', 'Responsable du Dépôt Central'),
('CHEF_SERVICE', 'Chef de Service / Responsable de Centre Médical'),
('AGENT_APPROVISIONNEMENT', 'Agent d''approvisionnement'),
('DBI', 'Direction des Biens et Inventaires'),
('MAGASINIER', 'Magasinier');

-- Insertion des entités de base
INSERT INTO entities (code, name, type) VALUES
('PC', 'Pharmacie Centrale', 'PHARMACIE_CENTRALE'),
('DC', 'Dépôt Central', 'DEPOT_CENTRAL'),
('DBI', 'Direction des Biens et Inventaires', 'DBI'),
('CM01', 'Centre Médical 1', 'CENTRE_MEDICAL'),
('CM02', 'Centre Médical 2', 'CENTRE_MEDICAL'),
('FRN01', 'Fournisseur Principal', 'FOURNISSEUR');
