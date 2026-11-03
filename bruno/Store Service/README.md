# 🚀 Collection Bruno - Store Service API (ShopLoc)

Cette collection de tests fonctionnels et d'intégration HTTP couvre l'intégralité des endpoints du microservice **Store Service** (Gestion des magasins et Catalogue Click & Collect).

---

## 📁 Structure de la Collection

```
bruno/Store Service/
├── bruno.json
├── opencollection.yml
├── environments/
│   ├── Local.bru              # Configuration pour localhost:8080
│   └── Dev.bru                # Configuration pour https://dev.shop-loc.fr
│
├── 01 - Magasins (Stores)/    # Endpoints et gestion des commerces
│   ├── 01 - Creer un magasin (MERCHANT) - 201.bru
│   ├── 02 - Creer un magasin (ADMIN) pour un commercant - 201.bru
│   ├── 03 - Lister tous les magasins (Public) - 200.bru
│   ├── 04 - Filtrer les magasins par commercant (Public) - 200.bru
│   ├── 05 - Consulter un magasin par ID (Public) - 200.bru
│   ├── 06 - Consulter un magasin inexistant - 404.bru
│   ├── 07 - Rejet creation magasin sans token - 401.bru
│   ├── 08 - Rejet creation magasin avec role CLIENT - 403.bru
│   ├── 09 - Rejet creation magasin tentative IDOR - 403.bru
│   └── 10 - Rejet creation avec IBAN invalide - 400.bru
│
├── 02 - Catalogue Produits/   # Catalogue Click & Collect (Story SLE-58)
│   ├── 01 - Creer un produit (MERCHANT - JSON) - 201.bru
│   ├── 02 - Creer un produit avec photo S3 (multipart) - 201.bru
│   ├── 03 - Creer un produit (ADMIN) - 201.bru
│   ├── 04 - Creer un produit sans stock (Defaut a 0) - 201.bru
│   ├── 05 - Lister tous les produits du magasin (Public) - 200.bru
│   ├── 06 - Consulter un produit par ID (Public) - 200.bru
│   ├── 07 - Mettre a jour un produit (MERCHANT - JSON) - 200.bru
│   ├── 08 - Supprimer un produit (MERCHANT) - 204.bru
│   └── 09 - Consulter un produit supprime - 404.bru
│
├── 03 - Gestion des Stocks/   # Décrémentation et réapprovisionnement atomique
│   ├── 01 - Ajuster le stock - Commande Click & Collect (SERVICE) - 200.bru
│   ├── 02 - Ajuster le stock - Reapprovisionnement delta (MERCHANT) - 200.bru
│   ├── 03 - Ajuster le stock - Valeur absolue newQuantity (MERCHANT) - 200.bru
│   ├── 04 - Rejet decrement au-dela du stock (Rupture) - 400.bru
│   ├── 05 - Rejet ajustement valeur absolue negative - 400.bru
│   └── 06 - Rejet ajustement sans quantite (body vide) - 400.bru
│
└── 04 - Securite et Validations/ # Tests anti-usurpation et Bean Validation
    ├── 01 - Rejet creation produit sans token - 401.bru
    ├── 02 - Rejet creation produit avec role CLIENT - 403.bru
    ├── 03 - Rejet creation produit par commercant non proprietaire - 403.bru
    ├── 04 - Rejet creation produit reference en double (Conflit) - 409.bru
    ├── 05 - Rejet creation produit avec prix negatif - 400.bru
    ├── 06 - Rejet creation produit avec corps vide (Validation) - 400.bru
    ├── 07 - Rejet modification produit inexistant - 404.bru
    ├── 08 - Rejet modification par commercant non proprietaire - 403.bru
    ├── 09 - Rejet suppression par role CLIENT - 403.bru
    ├── 10 - Rejet ajustement stock par role CLIENT - 403.bru
    ├── 11 - Rejet tentative spoofing header X-User-Role ADMIN - 403.bru
    └── 12 - Rejet creation produit magasin inexistant - 404.bru
```

---

## 🛠️ Utilisation dans Bruno

1. Ouvrez l'application **Bruno**.
2. Cliquez sur **Open Collection** et sélectionnez le dossier `bruno/Store Service`.
3. En haut à droite, sélectionnez l'environnement :
   - `Local` pour pointer vers `http://localhost:8080`.
   - `Dev` pour pointer vers `https://dev.shop-loc.fr`.
4. Renseignez vos jetons JWT de test dans les variables d'environnement (`merchantToken`, `adminToken`, `clientToken`, `serviceToken`).
5. Cliquez sur **Run** sur un dossier pour exécuter toute la suite de tests fonctionnels avec assertions automatisées (`res.status`, `res.body`).

## 💻 Exécution en ligne de commande (CLI)

```bash
# Exécution de toute la collection en local
bru run --env Local

# Exécution avec rapport de synthèse
bru run "02 - Catalogue Produits" --env Local
```
