# Guide Utilisateur

**Système de Gestion Intégrée des Stocks Pharmaceutiques — CNSS**

Ce manuel décrit l'utilisation de l'application pour chaque profil : Chef de Service de Centre Médical,
Responsable de la Pharmacie Centrale, Responsable du Dépôt Central, Valideur DBI, Magasinier et Administrateur.

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

- **Commandes en attente de traitement** — demandes à servir (PC) ou à valider (DBI) ;
- **Commandes validées à expédier** — en attente d'expédition par le Dépôt Central ;
- **Produits en alerte de stock** — quantité au niveau ou en dessous du seuil d'alerte (rouge) ;
- **Produits au catalogue** — nombre total de références ;
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

- **Structures** : gestion des entités (Centres Médicaux, Pharmacie Centrale, Dépôt Central, DBI, Fournisseur).
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

## 7. Circuit des commandes

Deux processus existent selon le circuit :

### 7.1 Processus A — Demande directe CM → PC (stock disponible)
1. **Centre Médical** : *Commandes → Nouvelle commande*, destinataire **Pharmacie Centrale**, ajoutez les lignes (produit + quantité) → la commande est créée en **Brouillon**.
2. Cliquez **Soumettre la commande** → statut **En attente de service (PC)**.
3. **Pharmacie Centrale** : ouvrez la commande puis **Valider et délivrer** :
   - le stock de la PC est **débité automatiquement** ;
   - la commande passe en **Livrée**.
   - Si le stock est insuffisant, la PC peut **Rejeter** la demande (le CM devra refaire une demande après réapprovisionnement), ou lancer une commande de réappro vers le DC (processus B).

### 7.2 Processus B — Réapprovisionnement avec validation DBI
1. **Pharmacie Centrale** : *Nouvelle commande*, destinataire **Dépôt Central** → *Soumettre* → **En attente de validation DBI**.
2. **DBI** : ouvre la commande et peut :
   - **Réajuster les quantités** (par ligne, ≤ quantité demandée) → **Ajustée par la DBI** ;
   - **Valider** → **Validée par la DBI** ;
   - **Rejeter** → **Rejetée** (terminal).
3. **Dépôt Central** : **Expédier** → le stock DC est débité → **Expédiée**.
4. **Pharmacie Centrale** (structure émettrice) : **Réceptionner** → le stock PC est crédité → **Livrée**.

### 7.3 Commande Fournisseur
1. **Dépôt Central** : *Nouvelle commande*, destinataire **Fournisseur** → *Soumettre* → circuit DBI comme ci-dessus.
2. Après validation DBI, le Dépôt Central **Réceptionne la livraison fournisseur** → le stock DC est crédité → **Livrée**.

### 7.4 Suivi
- La liste des commandes permet de filtrer par **structure émettrice** et **statut** ;
- Les Centres Médicaux ne voient que leurs propres demandes ;
- Les quantités affichées : **demandée**, **ajustée (DBI)**, **livrée**.

---

## 8. Signification des statuts

| Statut | Couleur | Signification |
|---|---|---|
| Brouillon | Gris | Commande créée, non soumise (modifiable par son auteur) |
| En attente de service (PC) | Bleu | Demande directe en attente de traitement par la PC |
| En attente de validation DBI | Rouge | Commande de réappro en attente de la DBI |
| Ajustée par la DBI | Orange | Quantités réajustées, en attente de validation |
| Validée par la DBI | Vert | Validée, en attente d'expédition |
| Expédiée | Bleu clair | Expédiée par le DC, en attente de réception |
| Livrée | Vert | Traite et terminée |
| Rejetée | Rouge | Refusée (terminal) |

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

| Module | ADMIN | Resp. Pharmacie | Resp. Dépôt | Chef Service / Agent | DBI | Magasinier |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Catalogue produits | ✏️ | ✏️ | lecture | — | — | — |
| Utilisateurs / Structures | ✏️ | — | — | — | — | — |
| Stocks / mouvements / seuils | ✏️ | ✏️ | ✏️ | lecture (son CM) | — | ✏️ |
| Commandes (création) | toutes cibles | → DC | → Fournisseur | → PC | lecture | lecture |
| Commandes (livraison directe) | ✏️ | ✏️ | — | — | — | — |
| Commandes (ajust./validation DBI) | ✏️ | — | — | — | ✏️ | — |
| Commandes (expédition / réception fournisseur) | ✏️ | — | ✏️ | — | — | — |
| Traçabilité | ✏️ | — | — | — | ✏️ | — |
