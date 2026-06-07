# Référence API — MicroRecouv V0

**Auteur :** KOMTSINDI Réné Alban  
**Version :** V0 — Avril 2026  
**Base URL :** `http://localhost:8080` (dev) / `https://api.imf.cm` (prod)  
**Format :** JSON (UTF-8)  
**Authentification :** Bearer JWT (`Authorization: Bearer <token>`)

---

## Conventions

- Tous les endpoints (sauf `/api/auth/**` et `/internal/**`) nécessitent un token JWT valide.
- Les réponses de liste sont paginées via `?page=0&size=20`.
- Les dates sont au format ISO-8601 : `YYYY-MM-DD` ou `YYYY-MM-DDTHH:mm:ss`.
- Les montants sont en **FCFA** (entiers ou décimaux).
- Les codes d'erreur suivent le standard RFC 7807 (Problem Details).

### Rôles disponibles

| Rôle | Description |
|---|---|
| `AGENT` | Agent de terrain — accès collectes et prêts de sa zone |
| `RESPONSABLE_RECOUVREMENT` | Gestion alertes, validation collectes |
| `ANALYSTE` | Lecture reporting et KPI |
| `DIRECTEUR` | Vue globale lecture seule |
| `DSI` | Accès complet + administration utilisateurs |

---

## Authentification

### POST /api/auth/login

Authentifie un utilisateur et retourne un couple access/refresh token.

**Corps de la requête :**
```json
{
  "username": "admin",
  "password": "Admin2026!"
}
```

**Réponse 200 :**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "role": "DSI",
  "username": "admin",
  "expiresInSeconds": 900
}
```

**Erreurs :** `401 Unauthorized` si identifiants incorrects.

---

### POST /api/auth/refresh

Renouvelle le token d'accès à partir du refresh token.

**Corps de la requête :**
```json
{ "refreshToken": "eyJhbGci..." }
```

**Réponse 200 :** même structure que `/login`.

**Erreurs :** `401` si refresh token expiré ou invalide.

---

### POST /api/auth/logout

Invalide la session courante (côté serveur).

**Réponse 204 No Content.**

---

## Utilisateurs

### GET /api/users/me

Retourne le profil de l'utilisateur connecté.

**Réponse 200 :**
```json
{
  "id": 1,
  "username": "admin",
  "nom": "Administrateur Système",
  "role": "DSI",
  "agenceId": null,
  "actif": true,
  "createdAt": "2026-01-01T00:00:00"
}
```

---

### POST /api/users/me/fcm-token

Enregistre le token FCM pour les notifications push.

**Corps :** `{ "fcmToken": "fcm_token_string" }`  
**Réponse 204.**

---

### GET /api/admin/users

Liste tous les utilisateurs. **Rôle requis : DSI**

**Paramètres :** `?page=0&size=20&role=AGENT`

**Réponse 200 :**
```json
{
  "content": [
    { "id": 1, "username": "admin", "role": "DSI", "actif": true }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

---

### POST /api/admin/users

Crée un nouvel utilisateur. **Rôle requis : DSI**

**Corps :**
```json
{
  "username": "agent01",
  "password": "TempPass2026!",
  "nom": "Jean Kamga",
  "role": "AGENT",
  "agenceId": "AG-YDE-01"
}
```

**Réponse 201 Created** avec l'objet utilisateur créé.

---

### PATCH /api/admin/users/{id}

Modifie un utilisateur (actif/inactif, rôle). **Rôle requis : DSI**

**Corps :** `{ "actif": false }` ou `{ "role": "ANALYSTE" }`  
**Réponse 200** avec l'objet modifié.

---

## Prêts

### GET /api/prets

Liste les prêts avec filtrage et pagination.

**Paramètres :** `?page=0&size=20&statut=ACTIF&agenceId=AG-YDE-01&par=PAR30`

**Réponse 200 :**
```json
{
  "content": [
    {
      "idPret": "PRE-2024-001",
      "reference": "REF-001",
      "nomClient": "Marie Ngo",
      "montantInitial": 500000,
      "montantRestant": 200000,
      "joursRetard": 35,
      "statutPar": "PAR30",
      "tauxRecouvrement": 60.00,
      "statut": "ACTIF"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "page": 0,
  "size": 20
}
```

---

### GET /api/prets/{idPret}

Détail complet d'un prêt.

**Réponse 200 :**
```json
{
  "idPret": "PRE-2024-001",
  "reference": "REF-001",
  "idClient": 42,
  "nomClient": "Marie Ngo",
  "montantInitial": 500000,
  "montantRestant": 200000,
  "tauxInteret": 18.5,
  "dateDebut": "2024-01-15",
  "dateFin": "2025-01-15",
  "nombreEcheances": 12,
  "echeancesPaye": 7,
  "joursRetard": 35,
  "statutPar": "PAR30",
  "tauxRecouvrement": 60.00,
  "statut": "ACTIF",
  "alerteRequise": true
}
```

---

## Échéances

### GET /api/echeances/pret/{idPret}

Liste toutes les échéances d'un prêt, triées par numéro.

**Réponse 200 :**
```json
[
  {
    "id": 1,
    "numero": 1,
    "dateEcheance": "2024-02-15",
    "montantDu": 45000,
    "montantPaye": 45000,
    "datePaiement": "2024-02-14",
    "statut": "PAYEE"
  },
  {
    "id": 8,
    "numero": 8,
    "dateEcheance": "2024-09-15",
    "montantDu": 45000,
    "montantPaye": 0,
    "statut": "EN_RETARD"
  }
]
```

---

### GET /api/echeances/{id}

Détail d'une échéance.

**Réponse 200 :** objet `EcheanceResponse` (voir ci-dessus).  
**Erreurs :** `404` si l'échéance n'existe pas.

---

### PUT /api/echeances/{id}

Met à jour le statut ou le montant payé d'une échéance.  
**Rôles requis : AGENT, RESPONSABLE_RECOUVREMENT, DSI**

**Corps :**
```json
{
  "statut": "PAYEE",
  "montantPaye": 45000,
  "observation": "Paiement reçu via MTN Mobile Money"
}
```

**Réponse 200** avec l'échéance mise à jour.

---

### GET /api/echeances/en-retard

Liste les échéances en retard (PAR), paginées.  
**Rôles requis : RESPONSABLE_RECOUVREMENT, DSI**

**Paramètres :** `?page=0&size=20`  
**Réponse 200 :** page de `EcheanceResponse`.

---

## Alertes

### GET /api/alertes

Liste les alertes avec filtres.

**Paramètres :** `?page=0&size=20&statut=ACTIVE&agenceId=AG-YDE-01`

**Réponse 200 :**
```json
{
  "content": [
    {
      "id": 101,
      "idPret": "PRE-2024-001",
      "nomClient": "Marie Ngo",
      "joursRetard": 35,
      "montantEnRetard": 200000,
      "statutAlerte": "ACTIVE",
      "statutPar": "PAR30",
      "dateGeneration": "2026-04-01T07:00:00",
      "dateCloture": null
    }
  ],
  "totalElements": 23,
  "totalPages": 2,
  "page": 0,
  "size": 20
}
```

---

### GET /api/alertes/{id}

Détail d'une alerte.

**Réponse 200 :** objet `AlerteResponse` complet.

---

### PUT /api/alertes/{id}

Mise à jour du statut d'une alerte (traitement, escalade, clôture).  
**Rôles requis : RESPONSABLE_RECOUVREMENT, DSI**

**Corps :**
```json
{
  "statut": "TRAITEE",
  "commentaire": "Client contacté, engagement de paiement reçu"
}
```

Valeurs possibles pour `statut` : `ACTIVE`, `TRAITEE`, `ESCALADEE`, `CLOTUREE`

**Réponse 200** avec l'alerte mise à jour.

---

## Clients

### GET /api/clients

Liste les clients avec recherche.

**Paramètres :** `?page=0&size=20&search=Kamga&agenceId=AG-YDE-01`

**Réponse 200 :** page de `ClientResponse`.

---

### GET /api/clients/{id}

Profil complet d'un client.

**Réponse 200 :**
```json
{
  "id": 42,
  "nom": "Marie Ngo",
  "prenom": "Claire",
  "telephone": "+237 699 123 456",
  "email": "marie.ngo@example.cm",
  "adresse": "Bastos, Yaoundé",
  "numeroCni": "123456789",
  "dateNaissance": "1985-06-15",
  "agenceId": "AG-YDE-01",
  "actif": true,
  "nbPrets": 2,
  "totalEmprunte": 1000000
}
```

---

### GET /api/clients/search

Recherche rapide par nom, téléphone ou CNI.

**Paramètres :** `?q=Marie&page=0&size=10`

**Réponse 200 :** liste de résultats simplifiés.

---

## KPI & Reporting

### GET /api/kpi/summary

Résumé des KPI pour le tableau de bord.  
**Rôles requis : DIRECTEUR, ANALYSTE, RESPONSABLE_RECOUVREMENT, DSI**

**Paramètres :** `?dateDebut=2026-01-01&dateFin=2026-03-31&agenceId=AG-YDE-01` (optionnel)

**Réponse 200 :**
```json
{
  "totalCollectes": 12500000,
  "nbCollectes": 145,
  "encoursPar30": 3200000,
  "encoursPar90": 850000,
  "nbAlertesActives": 23,
  "tauxRecouvrementGlobal": 78.5,
  "dateDebut": "2026-01-01",
  "dateFin": "2026-03-31"
}
```

---

### GET /api/reporting/collectes/csv

Export CSV des collectes sur une période.  
**Rôles requis : ANALYSTE, DIRECTEUR, DSI**

**Paramètres :** `?dateDebut=2026-01-01&dateFin=2026-03-31`  
**Réponse :** fichier CSV (`Content-Type: text/csv`).

---

### GET /api/reporting/prets/csv

Export CSV des prêts avec indicateurs PAR.

**Réponse :** fichier CSV.

---

### GET /api/reporting/alertes/pdf

Export PDF du rapport des alertes.  
**Réponse :** fichier PDF (`Content-Type: application/pdf`).

---

## SSE (Server-Sent Events)

### GET /api/sse/stream

Flux d'événements en temps réel (alertes, mises à jour de statut).

**Authentification :** `?token=<access_token>` (le header Authorization ne fonctionne pas avec EventSource)

**Format des événements :**
```
event: nouvelle-alerte
data: {"id": 102, "idPret": "PRE-001", "joursRetard": 31, "statutPar": "PAR30"}

event: alerte-cloturee
data: {"id": 101, "dateCloture": "2026-04-04T10:30:00"}
```

Le client doit reconnecter automatiquement si la connexion est interrompue.

---

## API Interne (Pipeline → Backend)

Ces endpoints sont réservés au pipeline Python et protégés par une clé API (`X-API-Key`).

### POST /internal/alertes

Crée ou met à jour les alertes générées par le pipeline ETL.

**En-tête :** `X-API-Key: <SPRING_API_KEY>`

**Corps :**
```json
[
  {
    "idPret": "PRE-2024-001",
    "joursRetard": 35,
    "montantEnRetard": 200000,
    "statutPar": "PAR30"
  }
]
```

**Réponse 200 :** `{ "created": 5, "updated": 12, "skipped": 2 }`

---

## Codes d'erreur

| Code HTTP | Signification |
|---|---|
| `400 Bad Request` | Corps de requête invalide ou paramètres manquants |
| `401 Unauthorized` | Token JWT absent, expiré ou invalide |
| `403 Forbidden` | Rôle insuffisant pour l'action demandée |
| `404 Not Found` | Ressource introuvable |
| `409 Conflict` | Conflit de données (ex. username déjà pris) |
| `422 Unprocessable Entity` | Données valides mais rejetées par la logique métier |
| `500 Internal Server Error` | Erreur serveur inattendue |

**Format d'erreur standard (RFC 7807) :**
```json
{
  "type": "https://imf.cm/errors/not-found",
  "title": "Ressource introuvable",
  "status": 404,
  "detail": "Prêt PRE-9999 introuvable",
  "instance": "/api/prets/PRE-9999"
}
```

---

*Documentation API générée à partir du code source — MicroRecouv V0 — Openxtech 2026*
