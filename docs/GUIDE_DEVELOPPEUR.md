# Guide Développeur

**Système de Gestion Intégrée des Stocks Pharmaceutiques — CNSS**

---

## 1. Stack & prérequis

| Composant | Version |
|---|---|
| Java (JDK) | 17+ |
| Spring Boot | 3.2.5 (parent Maven) |
| PostgreSQL | 15+ |
| Maven | 3.8+ |
| Node/npm | non requis (assets AdminLTE embarqués dans `src/main/resources/static`) |

Modules Spring utilisés : Web, Thymeleaf (+ Layout Dialect, extras Spring Security 6), Data JPA/Hibernate,
Security, Validation, Flyway, Lombok. Base H2 disponible pour exploration mais le profil de référence est PostgreSQL.

---

## 2. Mise en place de l'environnement

### 2.1 Base de données

```bash
psql -U postgres -f scripts/db/init_postgres.sql
# crée l'utilisateur dev_user/dev_password et les bases stock_pharmacie_dev / stock_pharmacie
```

Variables d'environnement (valeurs par défaut dans `application-dev.properties`) :
`DB_DEV_USERNAME` (défaut `dev_user`), `DB_DEV_PASSWORD` (défaut `dev_password`).

### 2.2 Lancer l'application

```bash
mvn spring-boot:run            # profil "dev" par défaut (port 8080)
# ou via VS Code : F5 sur StockApplication (launch.json fourni)
```

Flyway applique automatiquement les migrations `V1`…`V5` au démarrage et **valide les checksums**
(`flyway.validate()`) : toute divergence bloque le démarrage.

### 2.3 Profils

| Profil | Fichier | Usage |
|---|---|---|
| `dev` (défaut) | `application-dev.properties` | PostgreSQL local `stock_pharmacie_dev`, logs verbeux, devtools actif |
| `prod` | `application-prod.properties` | PostgreSQL `stock_pharmacie` |
| `test` | `src/test/resources/application-test.properties` | **schéma `it`** de `stock_pharmacie_dev` pour les tests d'intégration |

---

## 3. Architecture

Monolithe **modulaire MVC**, découpage métier strict :

```
src/main/java/com/pharmacy/stock/
├── config/                    # SecurityConfig, FlywayConfig, WebMvcConfig
├── common/                    # BaseDto, BusinessException, DateUtils
└── modules/
    ├── user/                  # Authentification, comptes, rôles (controller/dto/entity/repository/service/security)
    ├── entity/                # Structures (CM, PC, DC, DBI, Fournisseur)
    ├── product/               # Catalogue produits
    ├── stock/                 # Stocks, mouvements, seuils, alertes
    ├── order/                 # Circuit des commandes + validation DBI
    └── audit/                 # Journal d'action_logs
```

Chaque module suit la même structure : `controller/` (MVC Thymeleaf, retours de vues),
`service/` (interface + Impl, logique métier transactionnelle), `repository/` (Spring Data, requêtes JPQL
avec `join fetch` anti N+1), `dto/` (`*Dto` lecture, `*FormDto` formulaires validés), `entity/`.

### Templates Thymeleaf

```
templates/
├── layouts/main-layout.html       # structure globale (AdminLTE)
├── fragments/                     # header, sidebar, footer
│   └── components/                # alerts, badges, buttons, cards, modal, tables
└── pages/<module>/                # vues métier
```

La charte graphique (bleu / rouge / blanc / noir) est centralisée dans `static/css/charte-graphique.css`.

---

## 4. Conventions (à respecter)

| Périmètre | Règle | Exemple |
|---|---|---|
| Packages | minuscules plats | `com.pharmacy.stock.modules.order` |
| Classes | PascalCase ; interface + Impl | `OrderService`, `OrderServiceImpl` |
| Méthodes/variables | camelCase | `calculateStockDifference()` |
| Constantes/Enum | UPPER_SNAKE_CASE | `EN_ATTENTE_DBI`, `MAX_THRESHOLD_ALERT` |
| Tables/colonnes SQL | snake_case, tables au pluriel | `stock_movements.created_by_user_id` |
| Endpoints | kebab-case, pluriel | `/stocks/movements`, `/orders/pending-dbi` |
| Flyway | `V<ENTIER>__description_snake_case.sql` | `V5__insert_demo_accounts.sql` |

**Règles Flyway** : versions entières séquentielles, jamais de sous-version (V1.1 interdit) ;
un fichier exécuté est **immuable** — toute évolution = nouveau fichier `V(N+1)__….sql`.

---

## 5. Modèle de données & migration

Tables : `roles`, `entities`, `users`, `products`, `stocks` (unicité `entity_id+product_id`),
`orders` (+ `order_items`), `stock_movements` (journal entrées/sorties/ajustements), `action_logs` (audit).

Migrations actuelles :

| Version | Contenu |
|---|---|
| V1 | Schéma initial (9 tables + index) |
| V2 | Rôles (7) + entités de référence (PC, DC, DBI, CM01, CM02, FRN01) |
| V3 | Alignement des colonnes id/FK en `BIGINT` |
| V4 | Compte `admin` initial (`Admin@123`) |
| V5 | **Comptes de démonstration** (mot de passe `Demo@1234`), produits et stocks de démo |

> V5 est destiné aux environnements de développement/démonstration : ne pas l'exécuter en production
> (retirer le fichier du classpath prod ou insérer un jeu de données dédié).

---

## 6. Sécurité

- `SecurityConfig` : formulaire de login (`/login`), sessions (1 max, fixation prévenue, expiration → `/login?expired`),
  deny par URL + rôles, `accessDeniedPage("/403")`.
- `CustomUserDetails` : expose l'id utilisateur, le **rôle** (autorité `ROLE_<role>`) et la **structure** ; les
  données de structure sont copiées pour éviter les `LazyInitializationException` hors session.
- Les actions du circuit des commandes sont **aussi contrôlées dans `OrderServiceImpl`** (rôle + statut),
  de sorte que l'appel direct des endpoints est protégé même hors UI.
- Journalisation d'audit : `AuditService.record(...)` avec propagation `REQUIRES_NEW` (persiste même si la
  transaction métier est annulée).

### Matrice d'accès par URL

| Pattern | Rôles |
|---|---|
| `/users/**`, `/entities/**` | ADMIN |
| `/products/**` | ADMIN, RESPONSABLE_PHARMACIE |
| `/stocks/**` | ADMIN, RESPONSABLE_PHARMACIE, RESPONSABLE_DEPOT, MAGASINIER, CHEF_SERVICE, AGENT_APPROVISIONNEMENT |
| `/orders/**` | ADMIN, RESPONSABLE_PHARMACIE, RESPONSABLE_DEPOT, MAGASINIER, CHEF_SERVICE, AGENT_APPROVISIONNEMENT, DBI |
| `/audit/**` | ADMIN, DBI |
| reste | authentifié |

Des restrictions complémentaires s'appliquent **dans les services** (ex. un CHEF_SERVICE ne voit que les
commandes/stocks de sa structure ; les scoping checks sont dans `isRestrictedToOwnEntity` /
`isRestrictedToOwnOrders`).

---

## 7. Règles métier clés

### Stock (`StockServiceImpl`)
- Création automatique de la ligne de stock au premier mouvement (seuil par défaut 10).
- `ENTREE` : stock += q ; `SORTIE` : refusée si q > stock ; `AJUSTEMENT_INVENTAIRE` : la quantité saisie est
  le **comptage physique**, le mouvement journalise l'écart (peut être négatif).
- Alerte `lowStock` : `quantity <= minThreshold`.

### Commandes (`OrderServiceImpl`)
- Numérotation `CMD-<année>-NNNN` (séquence par année, parse du dernier numéro).
- Cibles autorisées par rôle : CM → PC ; PC → DC ; DC → Fournisseur ; ADMIN → toutes.
- `requires_dbi_validation = true` pour toute cible ≠ PC.
- Machine à états :

```
BROUILLON --submit--> EN_ATTENTE_SERVIE --deliver(PC)--> LIVREE          (processus A)
                      BROUILLON --submit--> EN_ATTENTE_DBI --adjust(DBI)--> AJUSTEE_DBI
                                           EN_ATTENTE_DBI/AJUSTEE_DBI --validate(DBI)--> VALIDEE_DBI
                                           EN_ATTENTE_DBI/AJUSTEE_DBI --reject(DBI)--> REJETEE
                      VALIDEE_DBI --ship(DC)--> EXPEDIEE --receive(émetteur)--> LIVREE
                      VALIDEE_DBI --receive-supplier(DC, cible FRN)--> LIVREE
```

- Débits/crédits de stock via `StockService.recordMovement` (une seule transaction = atomicité ;
  vérification de disponibilité **avant** tout mouvement).
- `quantity_delivered` = `quantity_adjusted` si présente, sinon `quantity_requested`.

---

## 8. Tests

```bash
mvn test       # tests unitaires (Mockito pur, rapides)
mvn verify     # + tests d'intégration (contexte Spring + MockMvc + PostgreSQL)
```

| Familiarité | Emplacement | Technique |
|---|---|---|
| Unitaires | `src/test/java/com/pharmacy/stock/modules/*/service/*Test` | Mockito (`@Mock`/`@InjectMocks`), lenient strictness, captors d'arguments |
| Intégration | `src/test/java/com/pharmacy/stock/it/*IT` | `@SpringBootTest` + `AutoConfigureMockMvc`, connexion réelle via `formLogin()`, assertions en base via `JdbcTemplate` |

- Les tests d'intégration s'exécutent dans le **schéma PostgreSQL `it`** (`currentSchema=it,public`,
  `spring.flyway.schemas=it`) : données de dev jamais touchées ; Flyway y applique V1–V5 au premier `mvn verify`.
- `AbstractIntegrationTest` purge les données métier et **restaure le socle de stocks de démonstration** avant
  chaque test.
- Failsafe est configuré dans le `pom.xml` pour exécuter `*IT` lors du goal `verify`.
- Nouveaux tests d'intégration : étendre `AbstractIntegrationTest` ; comptes disponibles (V5) :
  `resp_pharmacie`, `resp_depot`, `chef_cm01`, `agent_cm02`, `dbi_valideur`, `magasinier` — mot de passe `Demo@1234`.

---

## 9. Ajouter une évolution

**Nouvelle migration** : créer `src/main/resources/db/migration/V6__ma_description.sql`
(snake_case, version entière suivante) puis redémarrer — jamais modifier un fichier déjà appliqué.

**Nouveau module métier** : dupliquer la structure d'un module existant (controller/dto/entity/repository/service),
ajouter la règle d'accès dans `SecurityConfig`, l'entrée dans `fragments/sidebar.html`, les vues dans
`templates/pages/<module>/`, et journaliser les actions via `AuditService`.

**Nouveau statut de commande** : mettre à jour l'enum `OrderStatus`, la contrainte `CHECK` (nouvelle migration),
le fragment `order-status-badge` et les contrôles de transitions dans `OrderServiceImpl`.

---

## 10. Dépannage

| Symptôme | Cause probable / solution |
|---|---|
| `Flyway validate` bloque le démarrage | Checksum divergent → ne jamais éditer une migration appliquée ; `mvn clean` si le classpath est pollué |
| `Connection refused` au démarrage | PostgreSQL arrêté, ou URL/identifiants (`DB_DEV_*`) incorrects |
| Erreurs Lombok dans l'IDE | Annotation processing non activé / import du projet non terminé (VS Code : *Java: Clean Java Language Server Workspace*) |
| Port 8080 occupé après rechargement devtools | Tuer le JVM résiduel (`pkill -f com.pharmacy.stock`) puis relancer |
| Tests d'intégration échouent sur le schéma | Vérifier que `dev_user` possède les droits sur `stock_pharmacie_dev` et que le schéma `it` peut être créé ; réinitialiser avec `DROP SCHEMA it CASCADE` si besoin |
