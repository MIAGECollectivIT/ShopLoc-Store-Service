# 📋 Spécification & Règles de Gestion — Story SLE-70 (SLE-260)

> **Projet** : ShopLoc (GLOP — M2 MIAGE 2026-2027)  
> **Composant principal** : `ShopLoc-Store-Service` (avec impacts sur `ShopLoc-Sales-Service` et `ShopLoc-WebClient`)  
> **Epic** : SLE-8 — Espace Administration  
> **Story** : **SLE-70** — Révoquer un commerce partenaire sortant du programme d'animation locale  
> **Sous-tâches associées** :  
> - **SLE-260** : Conception & RG — Analyse d'impacts de la suppression/révocation et formalisation des règles de gestion (RG)  
> - **SLE-255** : Back (Store) — Endpoint de révocation de commerce avec anonymisation des coordonnées et statut REVOKED (`/v1/stores/{storeId}/revoke`)  
> - **SLE-256** : Back (Sales & Store) — Blocage systématique des commandes et transactions pour tout commerce révoqué  
> - **SLE-257** : Front — Modal de confirmation avec avertissement de révocation définitive et traçabilité de l'action admin  
> - **SLE-263** : Back & Front — Réactivation d'un commerce partenaire révoqué avec nouveau RIB conventionnel  

---

## 1. 🎯 Contexte & Objectif Métier

Dans le cadre de l'animation locale de la ville, un commerce partenaire peut quitter le programme ShopLoc (cessation d'activité, non-renouvellement de convention, réorganisation ou décision administrative de la mairie).

La story **SLE-70** formalise le processus de **clôture de partenariat** en respectant trois principes cardinaux :
1. **Rétraction publique immédiate** : Dès la révocation, l'établissement disparaît de l'annuaire public et du moteur de recherche pour les citoyens, et toute nouvelle transaction (commande en ligne, encaissement physique en magasin) est bloquée.
2. **Neutralisation & Anonymisation RGPD** : Les données bancaires (`rib`) et liens externes sont immédiatement anonymisés pour interdire tout virement futur tout en respectant le départ du commerçant.
3. **Conservation intégrale de l'historique (Soft-Delete obligatoire)** : Aucune suppression physique (`DELETE` SQL) n'est opérée. L'historique des ventes, reçus fiscaux PDF, transactions de fidélité et statistiques économiques globales de la mairie demeure parfaitement intact.

---

## 2. 🗳️ RMT — Règles-Métier Transversales

*Contraintes fonctionnelles globales s'appliquant à l'ensemble des modules (Store, Sales, Users, WebClient) et régissant les décisions système.*

| Identifiant | Intitulé | Énoncé de la Règle Métier |
| :--- | :--- | :--- |
| **RMT-001** | **Portée de la révocation et clôture de partenariat** | La révocation d'un commerce clôture immédiatement son partenariat actif dans le programme ShopLoc. Tant qu'il porte le statut `REVOKED`, le commerce est neutralisé et ne peut participer à aucune activité du réseau. Une éventuelle ré-adhésion future nécessite une décision administrative explicite de réactivation assortie de la saisie d'informations conventionnelles à jour (ex: nouveau RIB). |
| **RMT-002** | **Interdiction de suppression physique (Soft-Delete)** | Aucune suppression physique (`DELETE` SQL) ne doit être opérée sur la table des commerces afin de préserver l'intégrité référentielle des commandes passées, des ventes et des bilans économiques. |
| **RMT-003** | **Blocage systématique des transactions prospectives** | Tout commerce portant le statut `REVOKED` est formellement interdit de toute nouvelle transaction financière ou logistique (encaissements physiques en caisse, nouvelles commandes Click & Collect, réservations de stock). |
| **RMT-004** | **Dissociation entre commerce et compte utilisateur** | La révocation d'un commerce s'applique strictement à l'établissement commercial (`Shop`) et n'entraîne ni la clôture ni la rétrogradation du compte utilisateur (`User`) du commerçant, qui conserve son statut de citoyen et son accès en lecture à ses historiques. |
| **RMT-005** | **Préservation des commandes antérieures (Option C)** | Les commandes Click & Collect déjà acceptées ou payées avant l'horodatage de la révocation demeurent valides et retirables par les clients jusqu'au terme de leur cycle de vie normal. |
| **RMT-006** | **Anonymisation des coordonnées sensibles sortantes** | Dès lors qu'un commerce est révoqué, ses coordonnées bancaires (`rib`), son URL externe et sa clé d'image de devanture sont immédiatement purgées ou anonymisées, tandis que sa dénomination commerciale et son adresse physique sont conservées pour la traçabilité des factures passées. |
| **RMT-007** | **Invisibilité publique par défaut** | Tout commerce ne disposant pas du statut `ACTIVE` est automatiquement masqué et inaccessible sur l'ensemble des canaux publics de consultation et d'achat destinés aux clients (annuaire, recherche, catalogue). |
| **RMT-008** | **Préservation des avoirs et points citoyens** | Les points de fidélité et cagnottes acquis par les citoyens lors d'achats passés dans le commerce révoqué restent acquis et utilisables chez les autres commerces partenaires du réseau. |
| **RMT-009** | **Interdiction des avantages fidélité sur commerce révoqué** | Aucun bon d'achat, récompense ou conversion de points de fidélité ne peut être appliqué ou réclamé auprès d'un commerce portant le statut `REVOKED`. |

---

## 3. 🧑‍💼 EXF — Exigences Fonctionnelles

*Besoins métier formulés sous la forme : Acteur + Action vis-à-vis du système.*

| Identifiant | Intitulé | Acteur | Action vis-à-vis du système (Sujet + Verbe) |
| :--- | :--- | :--- | :--- |
| **EXF-001** | **Révocation administrative d'un commerce** | L'administrateur | L'administrateur révoque un commerce partenaire actif depuis l'espace de gestion des partenaires. |
| **EXF-002** | **Confirmation renforcée et avertissement d'impact** | L'administrateur | L'administrateur consulte l'avertissement de clôture de partenariat (retrait immédiat de l'annuaire, blocage des ventes et conservation de l'historique) et valide explicitement la confirmation avant d'exécuter la révocation. |
| **EXF-003** | **Visualisation du statut global des commerces** | L'administrateur | L'administrateur visualise l'ensemble des commerces (actifs et révoqués) avec leur statut distinctif (`ACTIVE` / `REVOKED`) et leur date de révocation. |
| **EXF-004** | **Consultation de l'annuaire public filtré** | Le client | Le client consulte l'annuaire et effectue des recherches textuelles ou par catégorie parmi les seuls commerces au statut `ACTIVE`. |
| **EXF-005** | **Information sur la fermeture d'un établissement** | Le client | Le client accédant par lien direct à la page d'un commerce révoqué visualise un message d'information lui notifiant que l'établissement n'est plus partenaire actif. |
| **EXF-006** | **Téléchargement des reçus passés d'un commerce révoqué** | Le client | Le client consulte l'historique et télécharge les preuves d'achat (reçus PDF) de ses commandes passées chez un commerçant désormais révoqué. |
| **EXF-007** | **Accès sécurisé à ses propres commerces** | Le commerçant | Le commerçant authentifié consulte la liste de ses propres établissements via une route contextuelle sécurisée (`/v1/stores/me`) sans exposer son identifiant technique dans l'URL. |
| **EXF-008** | **Consultation de l'historique de ventes d'un commerce clos** | Le commerçant | Le commerçant d'un établissement révoqué consulte son historique de ventes en mode lecture seule depuis son tableau de bord. |
| **EXF-009** | **Signalement d'inactivité sur le tableau de bord commerçant** | Le commerçant | Le commerçant d'un établissement révoqué visualise une bannière lui indiquant que son commerce est révoqué et que les fonctions de vente et de gestion de stock sont désactivées. |
| **EXF-010** | **Alerte de présence d'un produit révoqué dans le panier** | Le client | Le client tentant de valider un panier contenant un article d'un commerce révoqué reçoit une notification explicite l'invitant à retirer cet article de son panier. |
| **EXF-011** | **Réactivation administrative d'un commerce révoqué** | L'administrateur | L'administrateur réactive un commerce précédemment révoqué en saisissant des coordonnées conventionnelles à jour (nouveau RIB/IBAN valide), ce qui réintègre immédiatement l'établissement dans l'annuaire public et réactive les encaissements. |

---

## 4. 🤖 EXNF — Exigences Non Fonctionnelles

*Contraintes techniques, de sécurité, d'architecture et comportements à l'initiative du système.*

| Identifiant | Intitulé | Catégorie | Énoncé de l'Exigence Non Fonctionnelle |
| :--- | :--- | :--- | :--- |
| **EXNF-001** | **Contrôle d'accès strict au point de révocation** | **Sécurité & RBAC** | Le système restreint l'accès à l'endpoint de révocation (`POST /v1/stores/{storeId}/revoke`) aux seuls porteurs d'un jeton JWT valide doté du rôle `ADMIN`. |
| **EXNF-002** | **Anonymisation des données bancaires et externes** | **Conformité RGPD** | Le système remplace immédiatement le champ bancaire `rib` par la mention technique `REVOKED`, positionne le champ `url` à `null` et désassocie l'image de devanture lors de l'enregistrement de la révocation. |
| **EXNF-003** | **Horodatage et auditabilité de la révocation** | **Traçabilité** | Le système enregistre l'horodatage UTC précis de la révocation dans le champ `revoked_at` ainsi que l'identifiant de l'administrateur ayant ordonné la révocation dans `revoked_by`. |
| **EXNF-004** | **Contrôle de transition d'état** | **Contrôle d'intégrité** | Le système rejette avec un code d'erreur HTTP `409 Conflict` toute tentative de révocation portant sur un commerce qui n'est pas au statut `ACTIVE`. |
| **EXNF-005** | **Verrouillage du catalogue d'un commerce révoqué** | **Contrôle d'intégrité** | Le système `Store-Service` rejette avec un code d'erreur HTTP `422 Unprocessable Entity` toute tentative de création, modification ou suppression de produit sur un commerce au statut `REVOKED`. |
| **EXNF-006** | **Blocage des commandes et encaissements révoqués** | **Contrôle d'intégrité** | Le système `Sales-Service` rejette avec un code d'erreur HTTP `422 Unprocessable Entity` toute création de commande Click & Collect ou tout encaissement physique ciblant un commerce au statut `REVOKED`. |
| **EXNF-007** | **Résolution contextuelle de l'identité commerçante** | **Sécurité Anti-IDOR** | Le système `Store-Service` fournit un endpoint contextuel sécurisé `GET /v1/stores/me` dérivant l'identité du commerçant exclusivement depuis les claims du jeton JWT. |
| **EXNF-008** | **Filtrage automatique de l'annuaire et du moteur de recherche** | **Filtrage de données** | Le système `Store-Service` filtre systématiquement `WHERE status = 'ACTIVE'` dans l'ensemble des requêtes de liste, de recherche dynamique (`name`) et de filtrage par catégorie (`category`). |
| **EXNF-009** | **Support universel d'identification UUID et technique** | **Interopérabilité** | L'endpoint de révocation accepte indifféremment l'identifiant public UUID (`uid`) ou l'identifiant numérique technique (`id`). |
| **EXNF-010** | **Désactivation de la ressource image sur commerce clos** | **Gestion des médias** | En cas d'accès direct à l'image binaire d'un commerce révoqué (`GET /v1/stores/{id}/image`), le système retourne une réponse HTTP `404 Not Found` sans solliciter le stockage objet S3. |
| **EXNF-011** | **Optimisation des requêtes de consultation publique** | **Performance & Indexation**| Le système maintient un index de base de données composite `idx_shops_status_category` sur la table `shops` afin de garantir des temps de réponse inférieurs à 100ms sur le filtrage de l'annuaire public. |
| **EXNF-012** | **Validation algorithmique et persistance du nouveau RIB lors de la réactivation** | **Contrôle d'intégrité & Conformité** | Le système exige et valide algorithmiquement (clé RIB / checksum modulo 97 IBAN) le nouveau RIB fourni lors de la réactivation (`POST /v1/stores/{storeId}/reactivate`). Le statut est rétabli à `ACTIVE` et les champs d'audit de révocation (`revoked_at`, `revoked_by`) sont réinitialisés à `null`. |
| **EXNF-013** | **Contrôle d'accès et de transition d'état à la réactivation** | **Sécurité & Intégrité** | Le système restreint l'endpoint de réactivation aux administrateurs (rôle `ADMIN`) et rejette avec un code d'erreur HTTP `409 Conflict` toute tentative de réactivation ciblant un commerce qui ne porte pas le statut `REVOKED`. |

