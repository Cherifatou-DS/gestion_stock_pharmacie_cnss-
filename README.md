# Système de Gestion Intégrée des Stocks Pharmaceutiques — CNSS

Application monolithe modulaire (Spring Boot 3 + Thymeleaf/AdminLTE + PostgreSQL + Flyway) permettant la
gestion centralisée des stocks pharmaceutiques entre les Centres Médicaux (CM), la Pharmacie Centrale (PC),
le Dépôt Central (DC) et la Direction des Biens et Inventaires (DBI).

## 🚀 Démarrage rapide

```bash
# 1. Base de données PostgreSQL (une seule fois)
psql -U postgres -f scripts/db/init_postgres.sql

# 2. Lancer l'application (Flyway applique automatiquement les migrations V1 à V5)
mvn spring-boot:run

# 3. Ouvrir l'application
# http://localhost:8080
```

Connexion par défaut : `admin` / `Admin@123`

## 👥 Comptes de démonstration

Créés par la migration `V5__insert_demo_accounts.sql` — mot de passe commun : **`Demo@1234`**

| Compte | Rôle | Structure | Usage principal |
|---|---|---|---|
| `admin` | ADMIN | PC | Administration complète (utilisateurs, structures, traçabilité) |
| `resp_pharmacie` | RESPONSABLE_PHARMACIE | PC | Service des demandes CM, commandes de réappro, stocks PC |
| `resp_depot` | RESPONSABLE_DEPOT | DC | Expéditions DC, réceptions fournisseur, stocks DC |
| `chef_cm01` | CHEF_SERVICE | CM01 | Demandes de produits du Centre Médical 1 |
| `agent_cm02` | AGENT_APPROVISIONNEMENT | CM02 | Demandes de produits du Centre Médical 2 |
| `dbi_valideur` | DBI | DBI | Ajustement et validation des commandes de réappro |
| `magasinier` | MAGASINIER | DC | Consultation et mouvements de stock |

> ⚠️ Ces comptes sont destinés aux environnements de développement/démonstration uniquement.

## 📚 Documentation

| Document | Contenu |
|---|---|
| [docs/GUIDE_UTILISATEUR.md](docs/GUIDE_UTILISATEUR.md) | Manuel d'utilisation (par rôle, avec parcours pas à pas) |
| [docs/GUIDE_DEVELOPPEUR.md](docs/GUIDE_DEVELOPPEUR.md) | Architecture, conventions, base de données, tests |

## ✅ Qualité

```bash
mvn test     # Tests unitaires (27)
mvn verify   # Tests unitaires + tests d'intégration (21) — base PostgreSQL dédiée (schéma `it`)
```

## 🧰 Stack

Java 17 · Spring Boot 3.2 · Spring Security · Spring Data JPA · Flyway · PostgreSQL 15+ · Thymeleaf + Layout Dialect · AdminLTE 4 · Lombok · Maven
