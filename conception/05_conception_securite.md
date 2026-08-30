# 05 — Conception de la Sécurité

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Modèle de sécurité global

La sécurité du système repose sur trois piliers :
1. **Authentification** : JWT httpOnly cookies — protège contre le vol de token via XSS.
2. **Autorisation RBAC** : contrôle d'accès par rôle sur chaque endpoint et opération.
3. **Isolation multi-tenant** : filtrage systématique par `imf_id` au niveau applicatif.

---

## 2. Authentification — JWT httpOnly Cookies

### 2.1 Architecture des tokens

| Cookie | Durée de vie | Contenu | Utilisation |
|---|---|---|---|
| `imf_access` | 900 secondes (15 min) | `sub`, `imf_id`, `role`, `agence_id`, `iat`, `exp` | Authentification de chaque requête |
| `imf_refresh` | 7 jours | `sub`, `imf_id`, `jti` (UUID rotation) | Renouvellement du token d'accès |

**Attributs des cookies :**
- `HttpOnly` : inaccessible depuis JavaScript → protège contre XSS.
- `Secure` : transmis uniquement sur HTTPS en production.
- `SameSite=Strict` : bloque l'envoi cross-site → protège contre CSRF.
- `Path=/api` : limité aux requêtes API.

### 2.2 Flux d'authentification

```
Client → POST /api/auth/login (username, password)
  → AuthController → UserDetailsService → BCrypt.matches()
  → JWT généré (JwtUtil.generateTokens())
  → Cookies positionnés dans la réponse HTTP
  → Retour 200 OK (pas de token dans le corps)

Requêtes suivantes :
  → Cookie envoyé automatiquement par le navigateur
  → JwtAuthFilter extrait et valide le token
  → TenantContext.set(imf_id)
  → SecurityContextHolder.setAuthentication(...)
  → Handler appelé

Token expiré :
  → 401 Unauthorized
  → Client appelle POST /api/auth/refresh (avec cookie refresh)
  → Nouveau access token généré + rotation jti du refresh token
```

### 2.3 Application mobile

L'application Flutter utilise le stockage sécurisé (`flutter_secure_storage`) pour conserver le refresh token. Les requêtes incluent le header `Authorization: Bearer <access_token>` (pas de cookies dans l'app mobile).

### 2.4 Application bureau (Tauri)

Le client bureau charge Angular depuis `https://tauri.localhost` et appelle `https://imf.rene.it.com`. Les cookies `SameSite=Strict` ne sont pas envoyés en cross-origin. Le JWT est donc transmis comme sur le mobile : header `Authorization: Bearer`, jeton conservé dans `localStorage`.

Le CORS Spring fusionne toujours les origines Tauri (`https://tauri.localhost`, `http://tauri.localhost`, `tauri://localhost`, origines `asset.localhost`) avec la liste `CORS_ALLOWED_ORIGINS`. Un redéploiement de l’API est requis après cette évolution.

---

## 3. Autorisation — RBAC

### 3.1 Rôles et permissions

| Rôle | `@PreAuthorize` Spring | Accès |
|---|---|---|
| `SUPER_ADMIN` | `hasRole('SUPER_ADMIN')` | Toutes les opérations sur toutes les IMF |
| `DIRECTEUR` | `hasAnyRole('DIRECTEUR','SUPER_ADMIN')` | Lecture globale IMF, KPI, benchmarks |
| `RESPONSABLE_RECOUVREMENT` | `hasAnyRole('RESPONSABLE_RECOUVREMENT','DIRECTEUR','SUPER_ADMIN')` | Dossiers, créances, validation collectes |
| `ANALYSTE` | `hasAnyRole('ANALYSTE','DIRECTEUR','SUPER_ADMIN')` | Lecture KPI, SHAP, benchmarks |
| `DSI` | `hasAnyRole('DSI','SUPER_ADMIN')` | Config pipeline, logs, admin users |
| `AGENT` | `hasRole('AGENT')` | Saisie collectes, KPI personnel uniquement |

### 3.2 Règles métier d'autorisation

- Un `AGENT` ne peut lire que ses propres collectes (filtre `agent_id = currentUser.id`).
- Un `RESPONSABLE_RECOUVREMENT` ne peut gérer que les dossiers de son agence (filtre `agence_id = currentUser.agenceId`).
- Un `DIRECTEUR` peut voir toutes les agences de son IMF mais pas les autres IMF.
- Un `SUPER_ADMIN` n'est jamais filtré par `imf_id`.

---

## 4. Isolation Multi-tenant

### 4.1 Mécanisme

```java
// JwtAuthFilter.java
String imfId = jwtUtil.extractImfId(token);
TenantContext.setCurrentTenant(imfId);

// TenantContext.java
public class TenantContext {
    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();
    public static void setCurrentTenant(Long imfId) { CONTEXT.set(imfId); }
    public static Long getCurrentTenant() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
```

### 4.2 Application dans les requêtes JPA

Toutes les méthodes de repository Spring Data utilisent le `imf_id` du `TenantContext` :

```java
// Exemple : CollecteEpargneRepository
List<CollecteEpargne> findByImfIdAndStatut(Long imfId, String statut);

// Appelé systématiquement depuis le service :
collecteRepo.findByImfIdAndStatut(TenantContext.getCurrentTenant(), statut);
```

### 4.3 Garanties

- Aucun endpoint ne permet de passer `imf_id` comme paramètre de requête.
- Le `SUPER_ADMIN` passe le `imf_id` cible dans un header spécial `X-IMF-ID` (vérifié par un filtre dédié).
- Le `TenantContext` est nettoyé après chaque requête (pattern `finally { TenantContext.clear(); }`).

---

## 5. Sécurité du Pipeline

### 5.1 Accès base de données depuis Airflow
- Le pipeline Airflow utilise un **utilisateur PostgreSQL dédié** (`imf_pipeline`) avec des droits limités aux schémas `raw.*`, `staging.*`, `intermediate.*`, `dw.*`, `ml.*`.
- Aucun accès direct au schéma `app.*` depuis le pipeline (sauf via les procédures stockées ou l'API REST).
- Les credentials sont stockés dans les **Airflow Connections** chiffrées (Fernet key).

### 5.2 Validation des données d'ingestion
- Les fichiers CBS déposés sont validés en format et signature avant traitement.
- Les anomalies > 5% de lignes invalides bloquent l'ingestion et génèrent une alerte.
- Les UUID des collectes mobiles sont validés (format UUID v4) avant insertion.

### 5.3 Secrets
- Toutes les clés (JWT secret, Fernet key Airflow, FCM key, API keys externes) sont gérées via variables d'environnement et non stockées dans le code source.
- Un fichier `.env.example` documente les variables requises sans leurs valeurs.

---

## 6. Protection des données personnelles

### 6.1 Données sensibles identifiées
- Noms et coordonnées des clients (PII — Personally Identifiable Information).
- Coordonnées GPS des collectes (localisation habituelle du client).
- Montants d'épargne et revenus estimés.
- Scores MCRS et probabilités de défaut.

### 6.2 Mesures de protection
- Les champs PII ne sont jamais inclus dans les logs du pipeline.
- Les benchmarks inter-agences sont **agrégés et anonymisés** (pas de données client individuelles exposées entre IMF).
- L'accès aux scores MCRS est limité aux rôles autorisés (RESP_REC, DIRECTEUR, ANALYSTE).
- Les exports de données (rapports COBAC) ne contiennent pas d'identifiants personnels en dehors du contexte autorisé.

---

## 7. Traçabilité et audit

### 7.1 Journal applicatif
Chaque action sur une collecte ou une créance enregistre dans la table d'audit :
- `user_id`, `imf_id`, `action` (VALIDER, REJETER, MODIFIER_STATUT, etc.).
- `entity_type`, `entity_id`.
- `timestamp`, `ip_address`, `motif`.

### 7.2 Journal pipeline
`raw.journal_ingestions` trace chaque exécution de DAG avec statut, lignes traitées, erreurs.

### 7.3 Métriques de sécurité
- Tentatives de connexion échouées loggées avec IP et timestamp.
- Alertes automatiques si > 5 échecs consécutifs pour un même compte (protection brute-force).

---

## 8. Déploiement sécurisé

| Composant | Mesure de sécurité |
|---|---|
| Nginx reverse proxy | TLS 1.3, HSTS, rate limiting |
| Spring Boot | CORS limité aux origines connues (web + Tauri), CSRF désactivé (JWT stateless) |
| Client bureau Tauri | JWT Bearer, CSP restreignant `connect-src` à l’API `imf.rene.it.com` |
| PostgreSQL | Accès réseau interne Docker uniquement, pas de port 5432 exposé |
| Airflow | Interface Web protégée par auth Airflow, accès réseau interne |
| Redis | Pas de persistance, accès réseau interne uniquement |
| Flutter | Certificate pinning optionnel pour environnement de production |
