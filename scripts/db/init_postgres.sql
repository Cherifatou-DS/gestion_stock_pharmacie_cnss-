-- Script d'initialisation PostgreSQL pour le projet de gestion de stock pharmaceutique

-- Création de l'utilisateur de développement
CREATE USER dev_user WITH PASSWORD 'dev_password';

-- Création des bases de données
CREATE DATABASE stock_pharmacie_dev OWNER dev_user;
CREATE DATABASE stock_pharmacie OWNER dev_user;

-- Attribution des privilèges
GRANT ALL PRIVILEGES ON DATABASE stock_pharmacie_dev TO dev_user;
GRANT ALL PRIVILEGES ON DATABASE stock_pharmacie TO dev_user;

-- Connexion à la base de développement
\c stock_pharmacie_dev
GRANT ALL ON SCHEMA public TO dev_user;

-- Connexion à la base de production
\c stock_pharmacie
GRANT ALL ON SCHEMA public TO dev_user;
