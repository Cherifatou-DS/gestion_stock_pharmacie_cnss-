-- Migration Flyway: V4__insert_initial_admin_user.sql
-- Description: Creation du compte administrateur initial (mot de passe chiffre avec BCrypt)
-- Mot de passe par defaut : Admin@123 (a changer a la premiere connexion)

INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, entity_id, is_active)
SELECT 'admin',
       '$2a$10$7uXv/bhGIEpYJoVg23z67OIKKAnCkGoXwDZE0MCs1bE0sLwP1CtKu',
       'Admin',
       'CNSS',
       'admin@cnss.sn',
       r.id,
       e.id,
       TRUE
FROM roles r
JOIN entities e ON e.code = 'PC'
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
