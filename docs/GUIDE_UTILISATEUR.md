# Guide Utilisateur

**Système de Gestion Intégrée des Stocks Pharmaceutiques — CNSS**

Ce manuel décrit l'utilisation de l'application pour chaque profil : Chef de Service de Centre Médical,
Responsable de la Pharmacie Centrale, Responsable du Dépôt Central, Agent DAAS, Valideur DBI,
Magasinier et Administrateur.

---

## 1. Connexion à l'application

1. Ouvrez l'application dans votre navigateur (adresse fournie par votre administrateur, ex. `http://localhost:8080`).
2. Saisissez votre **identifiant** et votre **mot de passe**.
3. Vous arrivez sur le **tableau de bord**.

À la première connexion, changez votre mot de passe si votre administrateur vous a fourni un compte de démonstration.
Si vous avez oublié votre mot de passe ou êtes bloqué (« compte désactivé »), contactez l'administrateur.

Une **session inactive** est déconnectée automatiquement ; reconnectez-vous si le message « session expirée » apparaît.

---

## 2. Le tableau de bord

Page d'accueil après connexion, elle affiche :

- **Demandes d'approvisionnement à traiter** — demandes soumises (CM → PC, PC → DC, DC → DAAS) ;
- **Commandes en attente de validation DBI** — commandes fournisseurs de la DAAS à examiner ;
- **Commandes validées à transmettre au fournisseur** — en attente de transmission par la DAAS ;
- **Produits en alerte de stock** — quantité au niveau ou en dessous du seuil d'alerte (rouge) ;
- **Dernières commandes**, **Alertes de stock** et **Dernières actions journalisées**.

Chaque carte est un lien direct vers le module correspondant.

---

## 3. Comptes de démonstration

| Compte | Rôle | Structure |
|---|---|---|
| `admin` | Administrateur | Pharmacie Centrale |
| `resp_pharmacie` | Responsable Pharmacie Centrale | PC |
| `resp_depot` | Responsable Dépôt Central | DC |
| `chef_cm01` | Chef de Service | Centre Médical 1 |
| `agent_cm02` | Agent d'approvisionnement | Centre Médical 2 |
| `agent_daas` | Agent DAAS | DAAS |
| `dbi_valideur` | Valideur DBI | DBI |
| `magasinier` | Magasinier | DC |

---

## 4. Catalogue des produits

**Accès :** menu *Catalogue Produits* (Administrateur, Responsable Pharmacie).

- **Nouveau produit** : saisissez le **code** (ex. `MED004`), la **désignation**, l'**unité de dispensation** (Boîte, Tube…) et le **prix unitaire**, puis *Enregistrer*.
- Le code doit être **unique** ; le prix peut être nul.
- Pour modifier un produit, cliquez sur l'icône ✏️ dans la liste.

---

## 5. Structures et utilisateurs (Administrateur)

- **Structures** : gestion des entités (Centres Médicaux, Pharmacie Centrale, Dépôt Central, DBI, DAAS, Fournisseur).
  Bouton **Nouvelle structure** : saisissez un **code** unique (lettres/chiffres, ex. `CM03`), le **nom** de la
  structure et son **type**, puis *Créer la structure*. Le code est converti en majuscules ; un doublon est
  signalé par un message d'erreur.
- **Utilisateurs** : création des comptes — identifiant, nom, e-mail, mot de passe (8 caractères min.), **rôle** et **structure de rattachement**. Le rôle détermine les droits (voir §11).

---

## 6. Gestion des stocks (PC / DC)

**Accès :** menu *Gestion des Stocks* — chaque ligne présente une structure, un produit, sa quantité, son **seuil d'alerte** et son statut (**Normal** / **Stock bas** en rouge).

### 6.1 Enregistrer un mouvement
1. Bouton **Nouveau mouvement** (ou icône ↕️ sur une ligne de stock).
2. Choisissez la **structure**, le **produit** et le **type de mouvement** :
   - **Entrée** : réception de produits (approvisionnement) ;
   - **Sortie** : retrait de produits (délivrance, perte…) — refusée si le stock est insuffisant ;
   - **Ajustement d'inventaire** : saisissez la **quantité comptée physiquement** ; l'écart par rapport au stock théorique est journalisé automatiquement.
3. Cliquez *Enregistrer le mouvement* — le stock est mis à jour et le mouvement apparaît dans le **Journal des mouvements** (bouton de la liste des stocks).

### 6.2 Ajuster le seuil d'alerte
- Icône ⚙️ (sliders) sur une ligne de stock → saisissez le nouveau seuil → *Enregistrer*.
- Une alerte rouge est levée dès que la quantité devient **inférieure ou égale** au seuil.
- Le filtre **« Stocks en alerte uniquement »** et la carte rouge du tableau de bord recensent ces produits.

> Les utilisateurs de Centres Médicaux consultent uniquement le stock de leur structure.

---

## 7. Demandes d'approvisionnement et commandes fournisseurs

À partir de la v1.2, la terminologie métier distingue clairement :

- **Demande d'approvisionnement** (`supply_requests`) : toute demande de produits **entre entités
  internes** — CM → PC, PC → DC, DC → DAAS. Menu *Demandes d'approvisionnement*.
- **Commande fournisseur** (`orders`) : l'acte d'achat passé **exclusivement par la DAAS** auprès
  d'un **fournisseur** pour approvisionner le Dépôt Central, sous **validation DBI systématique**.
  Menu *Commandes Fournisseurs*.

### 7.1 Circuit des demandes d'approvisionnement (CM → PC, PC → DC, DC → DAAS)

1. **Entité demandeuse** (CM / PC / DC) : *Demandes d'approvisionnement → Nouvelle demande*,
   choisissez la structure sollicitée (les relations autorisées sont strictement CM → PC, PC → DC,
   DC → DAAS) et les lignes de produits → **Brouillon**.
2. **Soumettre** → **Soumise**.
3. **Entité sollicitée** (PC / DC / DAAS) : ouvre la demande puis peut :
   - **Rejeter** (motif obligatoire) → **Rejetée** (terminal) ;
   - **Prendre en charge** → **En cours de traitement**.
4. Pour **CM → PC** et **PC → DC** : l'entité sollicitée **Expédie** en saisissant les quantités
   accordées (peuvent être inférieures aux quantités demandées) :
   - son stock est **débité** (mouvement SORTIE tracé avec la référence de la demande) ;
   - la sortie est **valorisée** dans le suivi financier DBI → **Expédiée**.
5. L'entité demandeuse **Confirme la livraison** → son stock est **crédité** → **Livrée**.
6. Pour **DC → DAAS** : la demande ne mouvemente pas de stock. La **DAAS** la traite en émettant une
   **commande fournisseur liée** (§7.2) ; la demande passe automatiquement à **Livrée** lorsque la
   réception des marchandises au Dépôt Central couvre les quantités demandées.

### 7.2 Circuit des commandes fournisseurs (DAAS uniquement)

1. **DAAS** : *Commandes Fournisseurs → Nouvelle commande* — choisissez le **fournisseur**, une
   **demande d'approvisionnement DC → DAAS liée** (optionnel ; les lignes sont alors préremplies) et
   les lignes → **Brouillon**.
2. **Soumettre à la DBI** → **En attente de validation DBI**. Une commande ne peut jamais être
   transmise au fournisseur sans validation DBI.
3. **DBI** : ouvre la commande et peut :
   - **Réajuster les quantités** (par ligne, ≤ quantité demandée) → **Ajustée par la DBI** ;
   - **Valider** → **Validée par la DBI** : l'**engagement fournisseur** correspondant est
     automatiquement enregistré dans le suivi financier ;
   - **Rejeter** (motif obligatoire) → **Rejetée** (terminal).
4. **DAAS** : **Transmettre au fournisseur** → **Envoyée au fournisseur**.
5. **Dépôt Central** : **Réception des marchandises** en saisissant les quantités reçues :
   - réception partielle → **Reçue partiellement** (une réception complémentaire est possible) ;
   - réception complète → **Reçue** ;
   - le stock du DC est **crédité** (mouvement ENTREE tracé avec la référence de la commande) et la
     demande liée éventuelle est mise à jour (clôture à **Livrée** si elle est couverte).

### 7.3 Suivi financier (DBI)

**Accès :** menu *Suivi Financier (DBI)* — Administrateur et DBI uniquement.

- **Engagements fournisseurs** : totaux et détail des commandes validées (montant = quantités
  ajustées × prix unitaire du catalogue) ;
- **Sorties de stock valorisées** : totaux et détail des expéditions de demandes
  d'approvisionnement ;
- Filtre par type de transaction.

### 7.4 Suivi
- La liste des demandes permet de filtrer par **structure demandeuse** et **statut** ;
- La liste des commandes permet de filtrer par **fournisseur** et **statut** ;
- Les Centres Médicaux ne voient que leurs propres demandes ;
- Les quantités affichées pour une commande : **demandée**, **ajustée (DBI)**, **reçue (DC)**,
  prix unitaire et total par ligne.

---

## 8. Signification des statuts

### Demandes d'approvisionnement

| Statut | Couleur | Signification |
|---|---|---|
| Brouillon | Gris | Demande créée, non soumise |
| Soumise | Bleu | En attente de traitement par la structure sollicitée |
| En cours de traitement | Bleu clair | Prise en charge par la structure sollicitée |
| Expédiée | Orange | Stock débité chez la structure sollicitée, en attente de confirmation |
| Livrée | Vert | Confirmée par l'entité demandeuse (ou réception DC pour DC → DAAS) |
| Rejetée | Rouge | Refusée (terminal) |

### Commandes fournisseurs

| Statut | Couleur | Signification |
|---|---|---|
| Brouillon | Gris | Commande créée par la DAAS, non soumise |
| En attente de validation DBI | Rouge | À examiner par la DBI |
| Ajustée par la DBI | Orange | Quantités réajustées, en attente de validation |
| Validée par la DBI | Vert | Validée (engagement fournisseur enregistré), à transmettre |
| Envoyée au fournisseur | Bleu clair | Transmise, en attente de réception au DC |
| Reçue partiellement | Orange | Réception partielle au Dépôt Central |
| Reçue | Vert | Réception complète au Dépôt Central |
| Rejetée | Rouge | Refusée par la DBI (terminal) |

---

## 9. Traçabilité (journal d'audit)

**Accès :** menu *Traçabilité* (Administrateur, DBI).

Chaque action significative est journalisée : création de produits/commandes, mouvements de stock, validations DBI,
livraisons, expéditions, connexions/déconnexions… avec l'utilisateur, l'horodatage, l'adresse IP et le détail de l'action.

Filtres disponibles : **utilisateur**, **type d'action**, **entité affectée** (table impactée).

---

## 10. Questions fréquentes

**« Stock insuffisant » à l'enregistrement d'une sortie ?**
La quantité demandée dépasse le stock disponible. Vérifiez l'état du stock et le seuil, ou enregistrez d'abord une entrée.

**Ma commande est bloquée en Brouillon ?**
Ouvrez-la et cliquez *Soumettre la commande*. Seul son auteur peut la soumettre.

**Je ne vois pas de bouton d'action sur une commande ?**
Les actions dépendent de votre rôle et du statut de la commande (voir §8). Si l'action vous incombe, contactez l'administrateur.

**Un produit n'apparaît pas dans les listes ?**
Il doit d'abord exister dans le catalogue (§4).

---

## 11. Rappel des droits par module

| Module | ADMIN | Resp. Pharmacie | Resp. Dépôt / Magasinier | Chef Service / Agent CM | DAAS | DBI |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Catalogue produits | ✏️ | ✏️ | lecture | — | — | — |
| Utilisateurs / Structures | ✏️ | — | — | — | — | — |
| Stocks / mouvements / seuils | ✏️ | ✏️ | ✏️ | lecture (son CM) | lecture | — |
| Demandes d'appro. (création) | toutes | → DC | → DAAS | → PC | — | lecture |
| Demandes (prise en charge / rejet / expédition) | ✏️ | ✏️ | ✏️ | — | ✏️ (rejet / liaison commande) | — |
| Demandes (confirmation de livraison) | ✏️ | — | — | ✏️ | — | — |
| Commandes fournisseurs (création / envoi) | ✏️ | — | réception | — | ✏️ | lecture |
| Commandes (ajustement / validation / rejet DBI) | ✏️ | — | — | — | — | ✏️ |
| Suivi financier | lecture | — | — | — | — | ✏️ |
| Traçabilité | ✏️ | — | — | — | — | ✏️ |
