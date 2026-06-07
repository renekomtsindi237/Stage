# 04 — Conception de l'API REST

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Principes de conception

- **REST stateless** : chaque requête est autonome, l'état de session est porté par le JWT cookie.
- **Format de réponse unifié** : toutes les réponses utilisent l'enveloppe `ApiResponse<T>` :
  ```json
  { "success": true, "data": {...}, "message": "OK", "timestamp": "..." }
  ```
- **Pagination** : les listes paginées retournent `PageResponse<T>` avec `content`, `totalElements`, `totalPages`, `page`, `size`.
- **Multi-tenant transparent** : le `imf_id` est extrait du JWT, jamais passé par le client en paramètre de requête.
- **Versionnement** : pas de versionnement URL pour ce projet académique ; les évolutions sont gérées par rétrocompatibilité.

---

## 2. Module Authentification — `/api/auth`

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| POST | `/api/auth/login` | PUBLIC | Authentification, retourne cookies JWT httpOnly |
| POST | `/api/auth/refresh` | PUBLIC (cookie refresh) | Renouvellement du token d'accès |
| POST | `/api/auth/logout` | AUTHENTIFIÉ | Invalidation des cookies |
| GET | `/api/auth/me` | AUTHENTIFIÉ | Profil de l'utilisateur courant |

**Corps de requête (login) :**
```json
{ "username": "agent01", "password": "secret" }
```
**Réponse :** cookies httpOnly `imf_access` (900s) et `imf_refresh` (7j) positionnés.

---

## 3. Module Collectes d'Épargne — `/api/collectes-epargne`

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| POST | `/api/collectes-epargne` | AGENT | Soumettre une collecte individuelle |
| POST | `/api/collectes-epargne/sync` | AGENT | Synchroniser un batch de collectes offline |
| GET | `/api/collectes-epargne` | AGENT, RESP_REC, DIRECTEUR | Lister les collectes (filtres: agence, cycle, statut, dateDebut, dateFin) |
| PATCH | `/api/collectes-epargne/{id}/valider` | RESP_REC | Valider ou rejeter une collecte |
| GET | `/api/collectes-epargne/mon-kpi-jour` | AGENT | KPI journalier de l'agent authentifié |
| GET | `/api/collectes-epargne/non-synchros` | RESP_REC | Collectes en attente de validation |

**Corps de synchronisation (POST /sync) :**
```json
{
  "collectes": [
    {
      "uuidMobile": "550e8400-e29b-41d4-a716-446655440000",
      "montant": 5000,
      "dateCollecte": "2026-05-15",
      "canal": "ESPECES",
      "clientId": 42,
      "cycleId": 7,
      "latitude": 3.8667,
      "longitude": 11.5167
    }
  ]
}
```

**Réponse de synchronisation :**
```json
{
  "totalRecu": 15,
  "acceptees": 12,
  "doublons": 2,
  "rejetees": 1,
  "listeAcceptees": [...],
  "listeDoublons": [...],
  "listeRejets": [{"uuidMobile": "...", "motif": "MONTANT_NEGATIF"}]
}
```

---

## 4. Module Créances — `/api/creances`

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| GET | `/api/creances` | RESP_REC, DIRECTEUR, ANALYSTE | Lister les créances (filtres: agenceId, classeCobac, categoriePar, classeRisque) |
| GET | `/api/creances/{id}` | RESP_REC, DIRECTEUR | Détail d'une créance avec score MCRS et SHAP |
| GET | `/api/creances/kpi` | RESP_REC, DIRECTEUR | KPI recouvrement de l'agence/IMF |
| GET | `/api/creances/client/{clientId}/score-mcrs` | RESP_REC, DIRECTEUR, ANALYSTE | Score MCRS + SHAP pour un client |
| PATCH | `/api/creances/{id}/statut` | RESP_REC | Mise à jour du statut de recouvrement |

**Réponse score MCRS :**
```json
{
  "clientId": 123,
  "scoreMcrs": 0.67,
  "scoreCrs": 0.72,
  "scoreRps": 0.58,
  "scoreCsi": 0.81,
  "classeRisque": "ELEVE",
  "probabiliteDefaut90j": 0.43,
  "actionRecommandee": "RELANCE_URGENTE",
  "topFeature": "jours_retard_actuel",
  "topShapValue": 0.18,
  "shap": [
    {"feature": "jours_retard_actuel", "shapValue": 0.18, "valeur": 45},
    {"feature": "regularite_collecte_pct", "shapValue": -0.12, "valeur": 0.68}
  ],
  "dateScore": "2026-05-18",
  "versionModele": "2.1.0"
}
```

---

## 5. Module KPI — `/api/kpi`

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| GET | `/api/kpi/dashboard-directeur` | DIRECTEUR, ANALYSTE | Dashboard global IMF (collectes + recouvrement + MCRS) |
| GET | `/api/kpi/dashboard-recouvrement` | RESP_REC, DIRECTEUR | Dashboard recouvrement agence |
| GET | `/api/kpi/dashboard-agent` | AGENT | KPI hebdomadaire de l'agent |
| GET | `/api/kpi/par-stats` | DIRECTEUR, ANALYSTE, RESP_REC | Séries temporelles PAR par agence (dateDebut, dateFin) |
| GET | `/api/kpi/collecte-stats` | DIRECTEUR, ANALYSTE | Séries temporelles collectes (dateDebut, dateFin) |
| GET | `/api/kpi/tendances-prix` | DIRECTEUR, ANALYSTE | Tendances prix produits génériques (codeProduit?, zoneId?, jours=90) |
| GET | `/api/kpi/benchmarks` | DIRECTEUR, ANALYSTE | Classement inter-agences (z-scores) |

**Réponse dashboard directeur :**
```json
{
  "montantCollecteJour": 12500000,
  "collecteJour": 87,
  "variationCollecteSemaine": 4.3,
  "tauxRealisationObjectifPct": 78.5,
  "nbClientsRisqueCritique": 12,
  "nbClientsRisqueEleve": 34,
  "nbAlertesMlActives": 5,
  "encoursPar30": 45000000,
  "tauxPar30Pct": 6.9,
  "encoursPar90": 22000000,
  "tauxPar90Pct": 3.4,
  "tauxRecouvrementPct": 82.1,
  "totalProvisions": 18000000,
  "rangAgence": 2,
  "nbAgencesComparees": 8
}
```

---

## 6. Module SSE — `/api/sse`

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| GET | `/api/sse/events` | AUTHENTIFIÉ | Flux SSE d'événements temps réel pour l'IMF |

**Types d'événements publiés :**
- `kpi_collecte_updated` : après `dag_collecte_epargne`.
- `recouvrement_updated` : après `dag_recouvrement`.
- `scoring_updated` : après `dag_ml_scoring`.
- `alerte_critique` : alerte immédiate (PAR dépassé, client CRITIQUE).

---

## 7. DTOs principaux

### CollecteEpargneRequest (record Java)
```java
record CollecteEpargneRequest(
    @NotBlank String uuidMobile,
    @NotNull @Positive BigDecimal montant,
    @NotNull LocalDate dateCollecte,
    @NotNull String canal,
    @NotNull Long clientId,
    Long cycleId,
    Double latitude,
    Double longitude
) {}
```

### CreanceResponse (record Java)
Inclut un record imbriqué `ScoreMcrs` avec CRS, RPS, CSI, MCRS, `probabiliteDefaut90j`, `actionRecommandee`, `topFeature`, `topShapValue`.

### KpiRecouvrementResponse (record Java)
Inclut : `encoursPar30`, `tauxPar30Pct`, `encoursPar60`, `tauxPar60Pct`, `encoursPar90`, `tauxPar90Pct`, `tauxRecouvrementPct`, `totalProvisions`, `rangAgence`, `nbAgencesComparees`.

---

## 8. Gestion des erreurs

| Code HTTP | Signification | Exemple |
|---|---|---|
| 400 | Validation échouée | Montant négatif, UUID malformé |
| 401 | Non authentifié | Cookie JWT absent ou expiré |
| 403 | Non autorisé | Rôle insuffisant |
| 404 | Ressource introuvable | Créance inconnue |
| 409 | Conflit | UUID déjà enregistré (doublon) |
| 422 | Entité non traitable | Données CBS non conformes |
| 500 | Erreur serveur | Erreur pipeline inattendue |

Toutes les erreurs retournent l'enveloppe :
```json
{ "success": false, "data": null, "message": "Description de l'erreur", "timestamp": "..." }
```
