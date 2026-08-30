# Client bureau MicroRecouv (Tauri)

**Auteur :** KOMTSINDI Réné Alban  
**Année :** 2025–2026

Le client bureau encapsule le frontend Angular dans une fenêtre native Windows. Il s’installe comme une application bureautique (menu Démarrer, raccourci Bureau, désinstallation depuis Paramètres). L’API reste hébergée : `https://imf.rene.it.com`.

L’affichage est le même que le site [https://imf.rene.it.com](https://imf.rene.it.com). L’icône (installeur, `.exe`, barre de titre, raccourci Bureau) est le logo `MicroRecouv.png`.

---

## 1. Rôle dans l’architecture

```text
Installeur NSIS  →  MicroRecouv.exe (WebView)
                         │
                    Angular (bundlé)
                         │  HTTPS + JWT Bearer
                         ▼
              https://imf.rene.it.com  →  Spring Boot
```

Le backend, PostgreSQL, Airflow et le scoring ne sont pas embarqués. Le poste d’agence n’est qu’un client.

| Canal | Public visé | Offline |
|---|---|---|
| Web Angular | Consultation depuis un navigateur | Non |
| Bureau Tauri | Directeur, recouvrement, DSI, analyste sur PC d’agence | UI locale, API distante |
| Mobile Flutter | Agent terrain | Oui (SQLite + sync) |

---

## 2. Installation (utilisateur)

Fichier : `desktop/dist/MicroRecouv_1.0.0_x64-setup.exe` (~6 Mo).

1. Fermer MicroRecouv s’il est déjà ouvert.
2. Désinstaller l’ancienne version : Paramètres Windows → Applications → MicroRecouv.
3. Double-cliquer le Setup.
4. Choisir le français ou l’anglais.
5. Suivre l’assistant (installation pour l’utilisateur courant, sans droits administrateur).
6. Cocher le raccourci Bureau à la dernière étape.

Après installation :

- menu Démarrer → dossier **MicroRecouv** ;
- désinstallation : Paramètres Windows → Applications ;
- WebView2 est installé automatiquement s’il manque.

L’application se connecte à `https://imf.rene.it.com`. Le backend doit autoriser les origines Tauri (`https://tauri.localhost`, `http://tauri.localhost`). C’est prévu dans `app.cors.allowed-origins` : redéployer l’API après mise à jour.

---

## 3. Identité visuelle (logo)

Source : `MicroRecouv.png` à la racine du dépôt (même visuel que `frontend/src/assets/logo.png`).

| Surface | Fichier utilisé |
|---|---|
| Installeur NSIS | `desktop/src-tauri/icons/icon.ico` |
| `microrecouv.exe` installé | même `icon.ico` embarqué dans le binaire |
| Raccourci Bureau / menu Démarrer | icône de l’`.exe` |
| Barre de titre de la fenêtre | icône de l’`.exe` |

### Régénérer les icônes

```bash
cd desktop
npm run icons
```

Cela :

1. recadre `MicroRecouv.png` en carré (`src-tauri/icons/app-icon.png`) ;
2. lance `tauri icon` pour produire un `.ico` Windows compatible NSIS et l’éditeur de ressources (le format Pillow seul est souvent ignoré : l’installeur et l’`.exe` retombent alors sur le « M » violet Tauri).

Puis reconstruire l’installeur (`npm run build`). **Désinstaller** l’ancienne version avant de réinstaller : Windows met en cache l’icône du raccourci.

Si l’Explorateur affiche encore l’ancien pictogramme après réinstallation :

```powershell
ie4uinit.exe -show
Stop-Process -Name explorer -Force
```

---

## 4. Développement et build

Prérequis : Node.js, Rust (`rustup`), Visual Studio Build Tools (charge C++), Python 3 + Pillow (`pip install pillow`) pour régénérer les icônes.

```bash
cd desktop
npm ci
npm run icons    # uniquement si MicroRecouv.png a changé
npm run dev      # ng serve (config desktop) + fenêtre Tauri
npm run build    # Angular desktop + installeur NSIS
```

Sous Windows, initialiser le compilateur C++ avant le build :

```bat
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
cd desktop
npm run build
```

Équivalents Makefile : `make desktop-dev`, `make desktop-build`.

L’installeur généré par Tauri se trouve sous `src-tauri/target/release/bundle/nsis/` (ou le répertoire Cargo du poste). Le copier vers `desktop/dist/MicroRecouv_1.0.0_x64-setup.exe` pour la livraison.

| Fichier | Rôle |
|---|---|
| `MicroRecouv.png` | Logo source (installeur + application) |
| `frontend/src/environments/environment.desktop.ts` | `apiUrl = https://imf.rene.it.com`, `useHash = true` |
| `frontend/angular.json` (config `desktop`) | `baseHref: "./"`, CSS non différé |
| `desktop/src-tauri/tauri.conf.json` | Fenêtre, CSP, icônes, installeur NSIS |
| `desktop/src-tauri/icons/` | `icon.ico` et PNG dérivés |
| `desktop/src-tauri/src/` | Point d’entrée Rust |

### Affichage dans la WebView

La configuration Angular `desktop` produit un build avec navigation hash (`/#/directeur/dashboard`) pour éviter les 404, `baseHref` relatif (`./`) pour les assets, et le CSS critique n’est pas différé (`inlineCritical: false`) : WebView2 n’applique pas le motif `media="print"` + `onload` utilisé par Critters.

Tauri injecte un `nonce` dans `style-src` au build. Avec un nonce, le navigateur ignore `'unsafe-inline'` : les balises `<style>` générées par Angular sont bloquées. `dangerousDisableAssetCspModification` désactive cette injection pour `style-src` uniquement.

---

## 5. Authentification et CORS

Le web utilise surtout les cookies JWT `httpOnly` (`SameSite=Strict`). Le client bureau envoie le JWT via `Authorization: Bearer` (stocké dans `localStorage`), comme le mobile : les cookies ne traversent pas l’origine `https://tauri.localhost` → `https://imf.rene.it.com`.

Le backend fusionne toujours les origines Tauri avec `CORS_ALLOWED_ORIGINS`. SSE et exports (PDF, CSV) passent par `environment.apiUrl`.

---

## 6. Distribution

L’installeur NSIS (`targets: ["nsis"]`) :

- éditeur : Openxtech ;
- langues : français / anglais ;
- icône : logo `MicroRecouv.png` (`installerIcon` / `uninstallerIcon`) ;
- raccourci menu Démarrer dans le dossier MicroRecouv ;
- bootstrapper WebView2 embarqué.

Les artefacts générés (`desktop/dist/`, `desktop/src-tauri/target/`) ne sont pas versionnés.
