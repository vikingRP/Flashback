# Migration Flashback vers Minecraft 1.20.1 Forge

## Objectif et état actuel

Porter **toutes les fonctionnalités de Flashback 0.43.6 dès le premier portage**
vers Minecraft 1.20.1, Forge 47.4.10 et Java 17. Ce choix a été confirmé par
l'utilisateur. Aucun retour global à une ancienne release : les anciennes
implémentations servent uniquement de référence technique.

Base initiale : `bf046b7` (Flashback 0.43.6, Minecraft 26.3, Fabric, Java 25).
Branche de travail : `migration/forge-1.20.1`.
Dernière mise à jour : 27 septembre 2026.

**Compilation, construction du JAR, démarrage du client et enregistrement réel
réussis.** Les **27 contrôles du protocole et des registres passent** : 22 tests autonomes
et 5 tests dans le runtime Forge transformé. L'erreur de relecture
« invalid packet » est **résolue**. Le run 11 ouvre le ZIP enregistré et affiche
la scène 3D, l'éditeur et la timeline, confirmés visuellement à 17 h 56. L'export intégré en jeu reste à
valider ; les tests autonomes GPU, panorama, vidéo et audio passent.

La migration reste ouverte jusqu'à validation de la relecture, de l'éditeur,
de l'export et de l'ensemble des fonctions de la 0.43.6. Une compilation réussie
ne mesure pas la parité fonctionnelle.

## Tableau de suivi

| Lot | Responsable | État vérifié | Prochaine validation |
| --- | --- | --- | --- |
| Audit de l'historique et des API replay | Agent replay_port | Terminé ; références Forge 1.20.1 identifiées | Utiliser ces références pour les défauts rencontrés en jeu |
| Forge, Java 17, dépendances, métadonnées et JAR | Agent forge_build2 | PASS compilation et build ; JAR réobfusqué produit avec bibliothèques natives | Refaire le build final après les corrections d'intégration |
| Initialisation, événements, commandes, configuration et mixins | Agent principal et forge_build2 | PASS démarrage du client et entrée dans un monde | Vérifier les chemins de code déclenchés par le replay et l'export |
| Protocole, codecs et snapshots | Agent replay_port | PASS : 22 tests autonomes + 5 tests de registres sous Forge, soit 27 | Poursuivre les vérifications en lecture active et lors des changements temporels |
| Enregistrement et sauvegarde | Agent replay_port et principal | PASS : session réelle de 574 ticks sauvegardée en ZIP | Tester aussi la récupération d'un enregistrement interrompu |
| Relecture et navigation temporelle | Agent replay_port et principal | PASS ouverture du ZIP et affichage 3D au run 11 ; « invalid packet » résolu | Lecture active, recherche temporelle, pause et changement de vitesse |
| Rendu, interface et éditeur | Agent client_port et principal | PASS affichage de l'éditeur et de la timeline dans le replay au run 11 ; backend OpenGL/GLFW | Interactions avec timeline, keyframes, historique et réglages visuels |
| Capture GPU, panorama et encodage | Agent client_port | PASS tests autonomes sur les classes de production | Export intégré depuis un replay, son OpenAL, orientation des caméras et file d'exports |
| Documentation et validation finale | Agent principal, avec les agents spécialisés | Instructions Forge dans README ; preuves ci-dessous | Compléter la couverture fonctionnelle et livrer le JAR final validé |

## Preuves de validation

### Compilation et distribution

- `compileJava` passe avec traitement des annotations Mixin activé.
- `build` passe ; le JAR principal est `build/libs/flashback-0.43.6.jar`.
- Le JAR principal inclut les dépendances nécessaires et les bibliothèques
  natives d'export. Les variantes `-slim`, `-jij` et `-sources` ne sont pas
  des fichiers d'installation autonomes.
- Journal : `build/migration-compile.log`. Les journaux et artefacts sous
  `build/` sont générés et non versionnés.

### Démarrage et enregistrement en jeu

- Le client Forge atteint le menu, charge un monde et permet l'enregistrement.
- Un enregistrement réel de **574 ticks** a été sauvegardé dans
  `run/flashback/replays/2026-09-27T17_35_22.zip`.
- Les défauts de transformation rencontrés au démarrage ont été corrigés,
  notamment le mixin de création des particules incompatible avec les
  injections de constructeur de Mixin 0.8.5.
- Le run suivant ne présente plus les erreurs GLFW « Invalid scancode -1 ».
- Journaux d'intégration : `run/logs/latest.log`, `build/migration-client.log`
  et les versions numérotées conservées dans `build/`.

### Protocole et relecture

- **27 contrôles PASS** : 22 tests autonomes du protocole et 5 tests des
  registres dans le runtime Forge transformé. Ils couvrent notamment la
  sérialisation des paquets 1.20.1, la propriété des buffers, le tickrate,
  les snapshots, les actions et les changements de registres.
- Outil reproductible : `tools/replay-protocol-smoke/`.
- L'erreur « invalid packet » est résolue après deux corrections : préservation
  de `serverconfig` pendant le nettoyage des données du replay, puis exécution
  de `setScreen` sur le thread de rendu.
- **Run 11, 17 h 56** : ouverture de
  `run/flashback/replays/2026-09-27T17_43_07.zip` et affichage de la scène 3D,
  de l'éditeur et de la timeline confirmés visuellement.
- La lecture active, la navigation temporelle et l'export intégré restent à
  valider. L'affichage de l'éditeur ne valide pas encore toutes ses interactions.

### GPU, panorama, vidéo et audio hors client

Commande reproductible après résolution des dépendances et compilation :

```powershell
py -X utf8 tools/client-render-smoke/run.py --java-home "C:/path/to/jdk-17" --export
```

Résultats obtenus avec Java 17.0.19 et une NVIDIA RTX 3050 :

- PASS : copie RGBA, retournement vertical, alpha, profondeur perspective et
  restauration des états OpenGL.
- PASS : capture PBO, comparaison des pixels sur 20 réutilisations,
  profondeur flottante et restauration des paramètres de transfert.
- PASS : reconstruction cubemap et équirectangulaire, six directions,
  couverture des pixels, alpha, profondeur et conservation du buffer audio.
- PASS : chaîne de production `AsyncFFmpegVideoWriter`, capture GPU,
  conversion RGBA vers YUV420P, encodage MP4 H.264/AAC puis décodage JavaCV.
  Le fichier relu contient 24 images 64 × 48, une seconde de vidéo et un
  signal audio stéréo à 48 kHz. L'encodeur logiciel embarqué utilisé est
  `libopenh264`.
- PASS : PNG transparent relu avec contrôle de l'alpha ; erreur explicite et
  nettoyage correct lorsqu'un profil référence un encodeur absent.

Preuves : `build/client-render-smoke/export-validation.log` et fichiers dans
`build/client-render-smoke/exports/`. Détails du portage client dans
`docs/CLIENT_PORT_VALIDATION.md`.

L'export intégré depuis un replay, la capture sonore OpenAL, la synchronisation,
l'orientation réelle des caméras et les scènes complexes restent à vérifier.

## Historique technique utile

- 27 septembre 2026 : dépôt initialement propre ; trois agents spécialisés
  travaillent en parallèle sur replay, client et construction Forge, avec
  intégration et suivi par l'agent principal. Les travaux ont été conservés
  et repris après les interruptions.
- La base utilise Minecraft 26.3 : le travail comprend le rétroportage des API
  Minecraft, le remplacement de Fabric par Forge et l'adaptation de Java 25
  à Java 17.
- Lattice 2.x repose sur des API récentes. La base 1.2.18 et son support 1.20.1
  ont été intégrés avec les annotations de configuration et la licence MIT.
- Le canal Forge et les codecs PLAY remplacent les payloads Fabric.
  La phase CONFIGURATION n'existe pas en 1.20.1 ; les snapshots ont été adaptés
  au protocole de cette version.
- RenderPearl et SDL ont été remplacés par OpenGL et GLFW. Les framebuffers,
  transferts PBO, rendu ImGui, chemins de caméra, marqueurs et exports ont été
  adaptés. Les implémentations 0.43.6 de timeline, keyframes, interpolations,
  historique et profils d'export sont conservées.
- Les effets de première personne, mains du joueur filmé, caméra, HUD Forge,
  écrans et réglages visuels ont été adaptés aux points de rendu de 1.20.1.
- Le mixin supprimant le fondu des sections a été retiré parce que ce fondu
  n'existe pas dans le moteur 1.20.1.
- Les compilations de diagnostic ont successivement révélé 1 608, 1 264,
  513 puis 390 erreurs Java avant les corrections des contrôles Mixin et le
  build réussi. Ces nombres correspondent à différentes phases et ne
  représentent pas un pourcentage de fonctionnalités validées.
- Le présent fichier a été réécrit en UTF-8 après plusieurs conversions
  d'encodage incorrectes. Une copie binaire du texte précédent est conservée
  dans `.migration/MIGRATION_1.20.1_FORGE.before-utf8-*.md`.

## Méthode de suivi

Chaque lot distingue le code adapté de son résultat vérifié. Les validations
en jeu restent séparées des tests autonomes. Le tableau et les critères
ci-dessous sont mis à jour au fil des preuves et des défauts rencontrés.

`py tools/migration_status.py` analyse le journal de compilation existant et
produit `build/migration-status.json` avec le résultat du build et la répartition
des erreurs. Ce rapport ne déduit pas la validation en jeu ; ses champs ne
remplacent pas les preuves fonctionnelles de ce document.

## Critères de clôture

- [x] `compileJava` avec traitement des annotations Mixin activé.
- [x] `build` et JAR réobfusqué Forge avec dépendances et bibliothèques natives.
- [x] Démarrage du client et entrée dans un monde sans erreur de transformation Mixin.
- [x] Enregistrement et sauvegarde d'une session 1.20.1 réelle.
- [x] 22 contrôles autonomes du protocole + 5 contrôles de registres sous Forge : 27 PASS.
- [x] Tests autonomes GPU, PBO, panorama, MP4/audio et PNG transparent.
- [x] Erreur « invalid packet » résolue : `serverconfig` préservé et `setScreen` sur le thread de rendu.
- [x] Ouverture du ZIP et affichage 3D, éditeur et timeline au run 11.
- [x] Lecture active du court replay de validation : progression du tick 6 au tick 58 et arrêt en fin de session observés au run 11.

### Tests complémentaires en cours

- [x] Retour du tick 574 au tick 47 : la caméra libre conserve sa position, le joueur et les entités restent visibles, sans erreur réseau (correction de la comparaison des types de dimension).
- [x] Fenêtre d'export vidéo : s'ouvre sans crash et détecte les encodeurs (correction du chargement natif JavaCPP via les URL Forge `union:`).
- [x] **Export intégré MP4 depuis un replay (run 12, 18 h 23)** : `2026-09-27T18_22_56.mp4`, H.264 `libopenh264` 320 × 180 à 24 images/s (689 images, 28,7 s) et AAC stéréo 48 kHz synchronisé (écart 22 ms, environ 425 trames non silencieuses sur 1 347). Aucune erreur dans le journal. Contrôle visuel (orientation, contenu de l'image) confirmé par l'utilisateur : à compléter.
- [x] JAR de distribution : les deux bibliothèques imbriquées sont présentes ; `py -X utf8 tools/verify_forge_jar.py` → 2 845 contrôles PASS sur le build de 18 h 15, sans entrée source plus récente.
- Correctif (à valider en jeu, run 13) : sélection d'entité au clic droit, déplacement et rotation orbitale de la caméra à la souris. Le quaternion de vue est aligné sur la convention caméra récente (−Z) attendue par `ReplayUI` : rotation 1.20.1 suivie d'un demi-tour autour de Y (`MixinLevelRenderer`).
- Correctif (à valider en jeu, run 13) : « Override Time » sans effet, car le ciel lit `LevelAccessor#dayTime` et contourne `Level#getDayTime`. L'heure forcée est désormais appliquée dans `DimensionType#timeOfDay/moonPhase`, sur le thread de rendu uniquement (`visuals.MixinDimensionType`).
- Rapports de crash dans `run/crash-reports` : 17 h 29, injection Mixin dans le constructeur de `Particle`, corrigée par `@WrapOperation` ; 17 h 59, DLL JavaCPP verrouillée, corrigée par `NativeLibraryBootstrap` (répertoires adressés par contenu, jamais réécrits), 3 exports sans erreur au run 12 ; 18 h 33, `NoClassDefFoundError EnhancedFlight`, dû à une recompilation pendant que le client tournait et non à un défaut du code (classe présente dans le build et le JAR).
- [ ] Lancement isolé du JAR de production dans une instance Forge 1.20.1 hors environnement de développement.
- [ ] Récupération d'un enregistrement interrompu.
- [ ] Avance/retour temporel, pauses, vitesses, gel et précision des positions.
- [ ] Éditeur, tous types de keyframes, annulation/rétablissement et réglages visuels.
- [ ] Export intégré vidéo/audio, images, profondeur, projections et file d'exports.
- [ ] Compatibilités optionnelles Forge et fonctionnement sans ces mods.
- [ ] Build final et validation de l'ensemble des fonctionnalités de la 0.43.6.
