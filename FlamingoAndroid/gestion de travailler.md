# Gestion des Travailleurs

Page Android de gestion du personnel dans l'application Ocean Android.

## Objectif de la page

Cette page sert à suivre et administrer les travailleurs du restaurant. Elle centralise :

- la liste des travailleurs enregistrés,
- leurs catégories,
- leur présence,
- leurs salaires journaliers,
- les avances,
- les pénalités,
- les paiements,
- et la consultation détaillée de chaque fiche.

## Titre et sous-titre

L'interface affiche :

- **Titre principal**: `Gestion des Travailleurs`
- **Sous-titre**: `Système salarial et présences`

## Structure visuelle

La page est construite autour d'une carte principale avec :

- un en-tête avec le titre,
- un bouton d'ajout,
- une ligne de statistiques,
- un champ de recherche,
- une zone de chargement,
- un message vide si aucun travailleur n'est affiché,
- puis la liste des cartes travailleurs.

## Composants visibles

### 1. Bouton Ajouter

Le bouton `Ajouter` ouvre une boîte de dialogue pour créer un nouveau travailleur.

### 2. Statistiques de la page

La zone de statistiques affiche :

- le nombre de travailleurs filtrés,
- le total déjà payé.

Exemple de format affiché :

- `3 travailleurs • 120 DT déjà payé`

### 3. Recherche

Le champ de recherche permet de filtrer les travailleurs par :

- nom complet,
- catégorie.

### 4. État de chargement

Un indicateur de progression apparaît pendant le chargement des données.

### 5. État vide

Si aucun résultat n'est trouvé, la page affiche :

- `Aucun travailleur`

## Données utilisées

La page lit les données de la collection Firestore `workers`.

Chaque travailleur contient notamment :

- `id`
- `fullName`
- `category`
- `dailyWage`
- `currentPresence`
- `attendanceCount`
- `totalAdvances`
- `totalPenalties`
- `totalPaid`
- `startDate`
- `email`
- `uid`

## Carte d'un travailleur

Chaque travailleur est affiché dans une carte dédiée avec :

- un avatar avec initiales,
- le nom complet,
- la catégorie,
- l'état de présence,
- les statistiques salariales,
- les actions rapides.

### Informations affichées dans la carte

- nom complet,
- catégorie,
- statut de présence,
- salaire journalier,
- nombre de jours comptés,
- total des avances,
- total des pénalités.

## Actions disponibles sur un travailleur

### 1. Ouvrir les détails

Un appui sur la carte ouvre une boîte de dialogue de détail.

### 2. Présence

L'utilisateur peut modifier la présence d'un travailleur avec les états suivants :

- `present`
- `half`
- `absent`
- `off`

### 3. Avance

Le bouton `+ Avance` permet d'enregistrer une avance avec :

- montant,
- motif.

### 4. Pénalité

Le bouton `+ Pénalité` permet d'ajouter une pénalité avec :

- montant,
- motif.

### 5. Paiement

Le bouton `Paiement` permet d'enregistrer un paiement avec :

- montant,
- méthode de paiement.

### 6. Suppression

Le bouton `Supprimer` ouvre une confirmation avant suppression du travailleur.

## Fenêtre Nouveau Travailleur

Le bouton `Ajouter` ouvre un formulaire de création.

### Champs du formulaire

- nom complet,
- catégorie,
- date d'entrée,
- salaire journalier.

### Catégories disponibles

- Chef serveur
- Serveur
- Cuisine
- Sécurité
- Nettoyage
- Responsable

### Comportement métier

- le rôle du compte est dérivé automatiquement de la catégorie,
- aucune sélection manuelle de rôle n'est demandée dans l'interface,
- le mot de passe du compte est géré séparément par le flux d'authentification associé.

## Fenêtre de détail d'un travailleur

La boîte de dialogue de détail affiche :

- catégorie,
- salaire journalier,
- présence actuelle,
- jours comptés,
- avances,
- pénalités,
- total payé.

Elle propose aussi l'action :

- `État du jour`

## Gestion de la présence

La page propose une boîte de dialogue dédiée au choix de l'état du jour.

### États proposés

- Présent
- Demi-journée
- Absent
- Off

### Règles d'accès

- la modification de présence est réservée aux administrateurs,
- en cas d'accès non autorisé, un message d'erreur s'affiche.

## Calculs affichés

La page calcule et affiche des totaux à partir des travailleurs filtrés :

- total payé,
- avance totale,
- pénalité totale,
- jours comptés.

## Expérience utilisateur

La page vise à être rapide et simple :

- recherche instantanée,
- actions directes sur chaque carte,
- création rapide d'un travailleur,
- consultation détaillée sans quitter la page.

## Ce que cette page ne fait pas

Dans l'état actuel du code Android :

- il n'y a pas de calendrier mensuel complet affiché sur cette page,
- il n'y a pas de grille jour par jour visible comme sur une page calendrier dédiée,
- la gestion se fait surtout via liste, carte et dialogues.

## Dépendances principales

La page s'appuie sur :

- `WorkersFragment`
- `WorkersViewModel`
- `FirebaseService`
- `Worker` model
- `FragmentWorkersBinding`
- `item_worker_admin.xml`

## Résumé

`Gestion des Travailleurs` est la page centrale de suivi du personnel dans l'application Android. Elle permet de consulter, filtrer, créer, modifier et suivre les travailleurs, tout en gérant leurs présences et leurs mouvements salariaux dans une interface simple et opérationnelle.

