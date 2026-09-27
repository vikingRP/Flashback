# Validation du portage client Forge 1.20.1

## Périmètre codé

Le portage conserve les implémentations de la version 0.43.6 pour la timeline,
les pistes, les interpolations, les images clés audio, l'historique de l'éditeur,
les marqueurs, les profils d'export et les encodeurs. Les adaptations ci-dessous
remplacent les API de Minecraft 26.3 par celles de Minecraft 1.20.1.

| Fonction | Adaptation | Validation actuelle |
| --- | --- | --- |
| Interface ImGui et raccourcis | GLFW, codes ImGui normalisés, compositeur OpenGL 3 | Compilation ; interaction en jeu à vérifier |
| Chemins de caméra et marqueurs | VertexBuffer, RenderType et Font.drawInBatch | Compilation ; affichage en jeu à vérifier |
| Capture couleur, SSAA, PNG, FFmpeg | Framebuffers OpenGL, transfert asynchrone PBO avec fence | Shader et PBO vérifiés ; fichiers encodés à vérifier |
| Export transparent | Alpha conservé, alpha opaque normalisé avant la passe translucide | Shader vérifié sur vrais pixels ; scènes complexes à vérifier |
| Export profondeur | Texture R32F et linéarisation perspective | Shader analytique et transfert float PBO vérifiés ; fichier exporté à vérifier |
| Cube et équirectangulaire | Six faces conservées, projection panoramique fournie au renderer | Compilation ; orientation des six faces à vérifier |
| Audio et source audio personnalisée | OpenAL loopback, Camera.setup et gestion de buffers conservée | Compilation ; synchronisation et rendu sonore à vérifier |
| Régularité des images | Compilation synchrone des chunks, attente des profils et textures HTTP | Compilation ; apparition des chunks et skins à vérifier |
| Animations déterministes | Horloge de glint/bordure, RNG des blocs, fluides, particules et entités | Cibles vérifiées aux sources 1.20.1 ; répétabilité à vérifier |
| Progression Windows | Handle HWND obtenu via GLFWNativeWin32 | Compilation ; barre des tâches à vérifier |

Le mixin de suppression du fondu des sections a été retiré : ce fondu n'existe
pas dans le moteur 1.20.1. Les méthodes d'extraction de rendu récentes ont été
remplacées par les points de rendu immédiat correspondants.

## Preuve GPU du 27 septembre 2026

Commande reproductible :

```powershell
py -X utf8 tools/client-render-smoke/run.py --java-home "C:/path/to/jdk-17"
```

Résultat avec Java 17.0.19, NVIDIA RTX 3050 et contexte OpenGL 3.3 invisible :

```text
PASS: RGBA copy, vertical flip, alpha normalization, perspective depth, GL state restoration
```

Le test compile le ShaderManager de production, compare les pixels de sortie,
contrôle le framebuffer et le viewport restaurés, les états depth/scissor et
l'absence d'erreurs OpenGL. Il ne constitue pas une validation de l'ensemble du
mod en jeu. Le suivi global reste dans MIGRATION_1.20.1_FORGE.md.
Le même script avec `--capture` utilise les classes compilées de production
`SaveableFramebuffer` et `ImageFrame`. Résultat :

```text
PASS: PBO RGBA capture, 20 consecutive reuses, float depth, overlap rejection, pack and framebuffer restoration
```

Les 12 pixels RGBA sont comparés octet par octet sur 20 captures successives.
Le transfert de profondeur conserve la valeur flottante .375. Les bindings et
paramètres de disposition mémoire OpenGL sont restaurés, y compris lorsque la
capture est lancée avec un autre PBO déjà lié.
## Export natif et panorama du 27 septembre 2026

`--export` ajoute un test de la chaîne de production `AsyncFFmpegVideoWriter`,
avec les bibliothèques natives du JAR embarqué et des images capturées sur le GPU.
Le MP4 produit est relu par JavaCV : 24 images 64 × 48, durée d'une seconde,
vidéo H.264 (`libopenh264` disponible dans cette distribution) et audio AAC
stéréo 48 kHz avec signal non nul. Le PNG est relu et son alpha vérifié.
Les sondages automatiques des encodeurs peuvent afficher des erreurs pour les
accélérateurs absents de la machine ; le test vérifie l'encodeur logiciel réel.

```text
PASS: production async GPU -> RGBA/YUV -> H.264/AAC MP4, 24 decoded frames, stereo 48 kHz sound, transparent PNG, missing encoder cleanup
PASS: production cube cross and equirectangular reconstruction, six cardinal directions, RGBA alpha, float depth and audio retention
```

La reconstruction CPU est extraite sans modification de l'algorithme dans
`PanoramaProjector`, utilisé directement par `ExportJob`. Le test contrôle les
six directions, la couverture de tous les pixels, les zones vides du cube,
la conservation exacte des pixels RGBA/de profondeur et du buffer audio.
Il reste à valider l'orientation réelle des caméras et la scène exportée en jeu.
Les artefacts synthétiques sont dans `build/client-render-smoke/exports/`.

## Chargement des bibliothèques natives : défaut constaté et correction

Le run 11 a échoué le 27 septembre à 17 h 59 lors de l'ouverture de
File > Export Video : JavaCPP tentait de réécrire `jniavutil.dll` déjà chargée
dans son cache partagé Windows. Le chargement récursif `avutil` puis
`AVChannelLayout` rencontre ce défaut avant l'affichage des réglages d'export.

Le code de JavaCPP vérifie et extrait la ressource avant de retourner le chemin
d'une bibliothèque déjà chargée. Les URL `union:` de Forge passent par son
traitement générique des métadonnées ; un écart peut donc provoquer une nouvelle
extraction d'une DLL déjà verrouillée. Voir
[Loader.java, JavaCPP 1.5.14](https://github.com/bytedeco/javacpp/blob/1.5.14/src/main/java/org/bytedeco/javacpp/Loader.java).

`NativeLibraryBootstrap` extrait désormais les bibliothèques FFmpeg et JavaCPP
dans un dossier identifié par le SHA-256 de leur contenu, puis configure leur
chargement prioritaire depuis des fichiers ordinaires. Les dossiers publiés
sont immuables : ni une réinitialisation, ni une autre instance du jeu ne
réécrit une DLL déjà chargée. Le cache JavaCPP existant et les autres processus
Java restent intacts. Le choix utilisateur d'utiliser FFmpeg système est
conservé.

Validation autonome après correction, dans
`build/client-render-smoke/native-validation.log` :

```text
PASS: production async GPU -> RGBA/YUV -> H.264/AAC MP4, 24 decoded frames, stereo 48 kHz sound, transparent PNG, missing encoder cleanup
PASS: immutable native bundle and repeated JavaCPP loads without DLL rewriting
```

Le test contrôle le chemin de la DLL chargée et l'absence de modification du
fichier après cinq rechargements. Le run 12 atteint le menu à 18 h 06 après
extraction des ressources via les URL `union:` de Forge. L'ouverture réelle
du dialogue d'export a ensuite été confirmée, avec analyse des encodeurs et
sans nouveau crash de chargement natif. Le correctif est donc aussi validé
sous Forge ; la production du fichier exporté depuis le replay reste à vérifier.

## Callbacks des dialogues natifs : correction et test ciblé

`AsyncFileDialogs.show` complétait son `CompletableFuture` depuis le worker du
dialogue sous Windows. Les callbacks `thenApply` et `thenAccept` de
`StartExportWindow` lisaient et copiaient alors l'état de l'éditeur, puis
publiaient `EXPORT_JOB`, depuis ce worker.

Après reprise autorisée, le résultat est publié via `Minecraft.execute`, sur
le thread client, y compris pour l'annulation. Le dialogue natif lui-même reste
sur son worker Windows. Son indicateur d'activité reste posé jusqu'à réception
du résultat par le client, puis est retiré avant exécution des callbacks.

Test ciblé passé sous Java 17, sans ouvrir Minecraft ni dialogue natif :

```powershell
py -X utf8 tools/client-dialog-smoke/run.py --java-home "C:/path/to/jdk-17"
```

Le test compile la classe de production et vérifie succès et annulation,
le thread des callbacks, la valeur du résultat et le cycle de l'indicateur
du dialogue. L'intégration avec le vrai sélecteur de fichier reste à vérifier
pendant l'export en jeu.
