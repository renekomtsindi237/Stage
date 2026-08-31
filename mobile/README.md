# MicroRecouv — Application mobile (Flutter)

Application terrain de l’agent : **mode offline complet** (SQLite), saisie des collectes, synchronisation batch, bascule **serveur local ↔ en ligne**.

## Mode offline (ENF-D03)

Après **une** connexion réussie (OTP), l’app télécharge le portefeuille clients, le dashboard et les alertes dans SQLite.

Sans réseau pendant 72 h, l’agent peut :

1. consulter les **clients** en cache (recherche nom / téléphone) ;
2. **saisir une collecte** (UUID v4, GPS optionnel) ;
3. voir le **dashboard** et les **alertes** en cache ;
4. garder collectes **et positions GPS** localement jusqu’au retour réseau → `POST /api/v1/sync/collectes` + `PUT /api/v1/agents/me/position`.

SUCCESS / DOUBLON : retirés de l’outbox et archivés dans le journal local.  
CONFLIT / ERREUR : restent en attente avec le message serveur.

L’OTP et le premier téléchargement du portefeuille exigent le réseau.

## Session 24 h

Après un OTP réussi, la session agent est valable **24 heures** à partir de l’horodatage de connexion (fuseau `Africa/Douala`). Le refresh JWT ne prolonge pas cette fenêtre. À l’échéance, l’app redemande un OTP. Les collectes encore locales restent dans SQLite.

## Transition local ↔ en ligne

| Profil | URL |
|---|---|
| En ligne (prod) | `https://imf.rene.it.com` |
| Staging | `http://84.247.128.40:9090` |
| Serveur local | `http://127.0.0.1:8080` (émulateur Android : `10.0.2.2:8080`) |

Choix sur l’écran de connexion et dans **Profil**. Les collectes encore locales **ne sont pas perdues** : après reconnexion sur le nouveau serveur, elles sont poussées. Le cache clients / dashboard est retéléchargé.

Surcharge compile-time :

```bash
flutter run --dart-define=API_BASE_URL=https://imf.rene.it.com
flutter run --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

Défaut : production (`https://imf.rene.it.com`).

## APK smartphone (production)

```bash
cd mobile
flutter pub get
python android/app/src/main/res/generate_icons.py
flutter build apk --release --dart-define=API_BASE_URL=https://imf.rene.it.com
```

Fichier livré : `mobile/build/app/outputs/flutter-apk/app-release.apk`  
Copie pratique : `mobile/dist/MicroRecouv-1.0.4.apk` (`make mobile-apk` à la racine).

Installation sur le téléphone :

1. Ouvrir le fichier `MicroRecouv-1.0.2.apk` (identifiant `cm.rene.microrecouv`, distinct d’un éventuel reste Xiaomi `cm.imf.microrecouv` invisible).
2. Android → Paramètres → Sécurité → **autoriser l’installation depuis cette source**.
3. Installer.
4. Se connecter **une fois** avec un compte AGENT (OTP, réseau obligatoire).
5. Ensuite : collectes hors ligne jusqu’à 24 h de session / 72 h de cache.

L’APK pointe vers `https://imf.rene.it.com`. Signature sideload du projet (`android/app/microrecouv-sideload.jks`), pas Play Store. Les prochaines versions construites avec cette clé s’installent par-dessus.

## Lancer (émulateur / câble)

```bash
cd mobile
flutter pub get
flutter test
flutter run --dart-define=API_BASE_URL=https://imf.rene.it.com
```

## Tests

```bash
flutter test
```

Inclut une **simulation HTTP réelle** (`test/simulation_terrain_test.dart`) : prefetch → saisie offline → sync local → bascule prod → transfert de l’outbox → idempotence DOUBLON.
