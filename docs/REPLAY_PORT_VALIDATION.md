# Validation réseau et replay — Forge 1.20.1

État au 27 septembre 2026, après RUN12 lancé avec les corrections de seek.

## Résultats vérifiés

- Compilation Forge et Java 17 réussie.
- **22 assertions autonomes réussies** avec `tools/replay-protocol-smoke/run.ps1` : protocole 763, temps, tickrate et freeze easing, framing des snapshots et actions, seek du lecteur, cache des chunks sensible aux heightmaps, propriété des buffers de custom payloads, nettoyage des données temporaires conservant configurations et verrous.
- **5 assertions supplémentaires réussies sous Forge** avec `gradlew runClient -PreplaySmoke` : détection des changements de registres, stabilité de snapshots identiques reconstruits, conservation de l’identité et du mode du spectateur, sérialisation du Login avec dimension et seed enregistrés.
- Client lancé, monde normal créé et chargé, enregistrement sauvegardé en ZIP pendant les validations d’intégration.
- **RUN11 : ouverture visuelle confirmée du ZIP `2026-09-27T17_43_07.zip` dans l’éditeur, avec scène 3D et timeline.** Handshake terminé à 17:56:00 ; cache de 683 chunks chargé.
- RUN11 : lecture de la timeline observée de 6 à 58 ticks, arrêt en fin de séquence, puis seek arrière de 58 à 6 ticks. Le seek a révélé une réinitialisation indésirable de la caméra et un avertissement de profil joueur absent, corrigés et compilés dans RUN12, puis validés visuellement au seek 574 → 47 sur le replay plus long.
- RUN12 : handshake confirmé à 18:06:19. Après reprise autorisée, seek 574 → 47 validé visuellement : même vue de forêt en caméra libre, acteur Dev visible et loup réapparu. Aucun avertissement PlayerInfo ni erreur réseau dans les journaux après ce seek.
- Audit des cibles INVOKE/FIELD des mixins dans les méthodes réelles de 31 classes Forge 1.20.1.

## Résolution de « Server sent an invalid packet »

RUN9 révèle la cause initiale : `ConfigSync.syncConfigs` échoue avec une `NoSuchFileException` sur `saves/replay/serverconfig/forge-server.toml`. Le nettoyage du snapshot initial avait supprimé ce fichier avant sa lecture par Forge pendant le handshake, encore en phase HANDSHAKING. La déconnexion PLAY reçue par le listener de Login provoquait ensuite un ClassCastException secondaire.

`ReplayTempCleanup.clearSavedData` préserve maintenant les sous-arbres `serverconfig`, les fichiers `session.lock` et `flashback_pid`, ainsi que l’exception SQLite déjà présente. Trois tests réels sur des fichiers temporaires vérifient la conservation exacte des configurations, la présence des verrous et la suppression des anciennes données de chunks.

RUN10 passe le handshake et expose la seconde cause : le mixin de custom payloads restaurait un écran depuis le thread Netty. La capture et la restauration de l’écran sont désormais limitées au thread client, avec appel à `setScreen` seulement lorsque l’écran a changé. Les payloads enregistrés sont dispatchés sur le thread client ; le `Context.enqueueWork` de Forge y exécute immédiatement le travail, ce qui conserve la protection contre les interfaces ouvertes par les mods. RUN11 confirme l’ouverture de l’éditeur après ces deux corrections.

L’instrumentation ciblée de `Connection.exceptionCaught` conserve le protocole, le listener et la cause complète dans les journaux si une autre erreur réseau apparaît.

## Autres adaptations importantes

- Transport interne Forge et codec vanilla 763 ; copie immuable des custom payloads avant libération du buffer Netty original.
- Horloge et gel explicites ; pas manuel indépendant du joueur caméra. Le marqueur FinishedServerTick reste traité sur le thread réseau pour éviter de bloquer l’attente d’export.
- Fin d’interpolation au seek pour les entités vivantes, leur tête, les bateaux et les minecarts ; conservation de l’ordre des passagers.
- Registres synchronisés par Login, tags et fonctionnalités par PLAY ; actualisation des caches PlayerList. Les changements de registres reconstruisent les niveaux et resynchronisent le spectateur, ses chunks, entités, pose, horloge et caméra.
- Hash de seed enregistré conservé sans double hachage lors des Login et changements de dimension.
- Dimension de chaque acteur conservée par la surcharge de PlayerList.load, empêchant le placement forcé dans l’Overworld.
- Packs serveur mémorisés dans les snapshots et réinitialisés au Login ; réactivation possible après désactivation dans les réglages.

## Audit après RUN12

Deux corrections supplémentaires attendent la prochaine compilation groupée : conservation des instances de registres lorsque leur contenu est identique (les niveaux conservent un RegistryAccess final, et doivent recevoir les mises à jour de tags) ; restauration de la latence, du nom affiché, du mode et de la visibilité tablist enregistrés après l’initialisation des profils au reconnect de registres. Cette dernière initialisation vanilla utilisait les valeurs par défaut des acteurs factices. Ces chemins particuliers ne sont pas couverts par le seek simple validé ci-dessus.

## Validation restante

Le seek est corrigé en comparant la clé de dimension et le contenu sérialisé des registres : les IntProviders de DimensionType pouvaient comparer leur identité après décodage et provoquer une recréation injustifiée du monde. La caméra conserve sa pose lors de la recréation dans une même dimension ; un acteur déjà supprimé par le reset ne sera plus téléporté et réapparié avant sa restauration.

Les **changements de dimension/registres en replay, les fonctions détaillées de l’éditeur et l’export vidéo restent à valider**. La lecture observée et les 27 assertions ne constituent pas une validation complète de ces fonctions.

La session RUN11 s’est arrêtée sur une erreur de chargement JNI pendant l’ouverture de l’export ; le lot client/build examine ce problème. Traces disponibles dans `build/migration-client.log`, `run/logs/latest.log` et `run/logs/debug.log`.
