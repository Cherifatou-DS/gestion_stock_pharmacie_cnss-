-- Migration Flyway: V7__secure_known_passwords.sql
-- Description: Renforcement de la securite des comptes a mot de passe connu.
--   1. Colonne must_change_password : changement de mot de passe force a la
--      premiere connexion (rendu obligatoire par ForcePasswordChangeFilter).
--   2. Neutralisation des comptes de demonstration (V5) en production.
--
-- Placeholders Flyway :
--   admin_must_change_password : true par defaut (dev/prod), false en tests d'integration
--   demo_accounts_active       : true en dev/tests, false en production
--                                (voir application*.properties)

ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Le mot de passe du compte administrateur initial (V4 : Admin@123) est connu :
-- son changement est rendu obligatoire des la premiere connexion.
UPDATE users SET must_change_password = TRUE
WHERE username = 'admin'
  AND ${admin_must_change_password};

-- Comptes de demonstration (V5, mot de passe commun Demo@1234 publiable) :
-- desactives automatiquement en production.
UPDATE users SET is_active = FALSE, must_change_password = TRUE
WHERE username IN ('resp_pharmacie', 'resp_depot', 'chef_cm01',
                   'agent_cm02', 'dbi_valideur', 'magasinier')
  AND NOT ${demo_accounts_active};
