# DashDeck

Écran d'accueil façon CarPlay pour téléphone ou tablette Android posé sur un tableau de bord.

## Fonctions
- Grille d'apps paginée, grosses icônes arrondies, transitions fluides
- Colonne latérale : heure, date, batterie, boutons Ajouter / Modifier / Réglages
- Ajouter une app : bouton « + » (liste avec recherche)
- Retirer une app : appui long sur une icône (ou bouton grille), puis le « – » rouge
- Réglages : couleur d'accentuation, 4 fonds, taille des icônes, écran toujours allumé, paysage forcé
- Peut servir d'écran d'accueil par défaut (touche Home)
- Aucune permission demandée, aucune donnée envoyée

## Obtenir l'APK

### Option A — GitHub (sans rien installer)
1. Créez un dépôt GitHub et envoyez-y le contenu de ce dossier.
2. Onglet **Actions** → **Build APK** → **Run workflow** (ou simple `push`).
3. Quand c'est vert, ouvrez l'exécution et téléchargez l'artefact **DashDeck-apk** (zip contenant `app-debug.apk`).

### Option B — Android Studio
1. **File → Open** et choisissez ce dossier, attendez la synchronisation Gradle.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.

### Option C — Ligne de commande (JDK 17 + SDK Android installés)
```
./gradlew assembleDebug
```

## Installer
Copiez `app-debug.apk` sur l'appareil et ouvrez-le (autorisez « Installer des apps inconnues » si demandé).
Pour en faire l'écran d'accueil : appuyez sur Home et choisissez DashDeck → « Toujours ».
