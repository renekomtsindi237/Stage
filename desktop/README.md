# MicroRecouv — Client bureau

Application Windows (Tauri 2) qui encapsule le frontend Angular. L’API utilisée est `https://imf.rene.it.com`. L’icône de l’installeur et de l’application est le logo `MicroRecouv.png`.

Documentation complète : [docs/desktop.md](../docs/desktop.md).

```bash
npm ci
npm run icons    # régénère icon.ico depuis ../MicroRecouv.png
npm run dev      # développement
npm run build    # installeur : dist/MicroRecouv_1.0.0_x64-setup.exe
```

Sous Windows, lancer `vcvars64.bat` (Build Tools C++) avant `npm run build`. Livrer le Setup depuis `desktop/dist/`.
