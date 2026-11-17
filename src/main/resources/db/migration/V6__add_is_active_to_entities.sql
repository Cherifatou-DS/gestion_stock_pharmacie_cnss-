-- Migration Flyway: V6__add_is_active_to_entities.sql
-- Description : ajout de la désactivation logique des structures de référence,
--               nécessaire aux fonctions de modification et de suppression (désactivation).

ALTER TABLE entities
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
