# Système de Gestion Intégrée des Stocks Pharmaceutiques — CNSS

Application monolithe modulaire (Spring Boot 3 + Thymeleaf/AdminLTE + PostgreSQL + Flyway) permettant la
gestion centralisée des stocks pharmaceutiques entre les Centres Médicaux (CM), la Pharmacie Centrale (PC),
le Dépôt Central (DC), la Division des Achats et des Approvisionnements Sanitaires (DAAS) et la
Direction des Biens et Inventaires (DBI).

> **Modèle métier v1.2** : les flux internes (CM → PC, PC → DC, DC → DAAS) sont des **demandes
> d'approvisionnement** ; **seule la DAAS passe des commandes fournisseurs** (validation DBI
> systématique, réception par le Dépôt Central). La DBI assure le suivi financier
> (engagements fournisseurs et sorties de stock valorisées).

## 🚀 Démarrage rapide

```bash
# 1. Base de données PostgreSQL (une seule fois)
psql -U postgres -f scripts/db/init_postgres.sql

# 2. Lancer l'application (Flyway applique automatiquement les migrations V1 à V8)
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
| `resp_pharmacie` | RESPONSABLE_PHARMACIE | PC | Traitement des demandes CM, demandes PC → DC, stocks PC |
| `resp_depot` | RESPONSABLE_DEPOT | DC | Demandes DC → DAAS, réceptions des commandes fournisseurs, stocks DC |
| `chef_cm01` | CHEF_SERVICE | CM01 | Demandes d'approvisionnement du Centre Médical 1 |
| `agent_cm02` | AGENT_APPROVISIONNEMENT | CM02 | Demandes d'approvisionnement du Centre Médical 2 |
| `agent_daas` | DAAS | DAAS | Commandes fournisseurs (seul acteur habilité) |
| `dbi_valideur` | DBI | DBI | Validation des commandes DAAS, suivi financier des sorties |
| `magasinier` | MAGASINIER | DC | Consultation et mouvements de stock |

> ⚠️ Ces comptes sont destinés aux environnements de développement/démonstration uniquement.

## 📚 Documentation

| Document | Contenu |
|---|---|
| [docs/GUIDE_UTILISATEUR.md](docs/GUIDE_UTILISATEUR.md) | Manuel d'utilisation (par rôle, avec parcours pas à pas) |
| [docs/GUIDE_DEVELOPPEUR.md](docs/GUIDE_DEVELOPPEUR.md) | Architecture, conventions, base de données, tests |

## ✅ Qualité

```bash
mvn test     # Tests unitaires (23)
mvn verify   # Tests unitaires + tests d'intégration (34) — base PostgreSQL dédiée (schéma `it`)
```

## 🧰 Stack

Java 17 · Spring Boot 3.2 · Spring Security · Spring Data JPA · Flyway · PostgreSQL 15+ · Thymeleaf + Layout Dialect · AdminLTE 4 · Lombok · Maven
