# PrestigeHoe – Panorama détaillé du code

Ce document décrit l'architecture complète du plugin PrestigeHoe, en passant en revue les responsabilités de chaque composant et les points de vigilance associés.

## Coeur du plugin
- **PrestigeHoePlugin** orchestre l'initialisation : copie des ressources YAML, choix du backend de stockage, création des services (formules, economy hook, boosts, skins, menus) puis enregistrement des listeners et commandes. Le `reloadAllConfigs` relance les managers dépendants et rafraîchit l'affichage des hoes connectées. Les logs de dépendances et l'auto-save asynchrone sont également pilotés ici.【F:src/main/java/fr/batistou15/prestigehoe/PrestigeHoePlugin.java†L72-L515】

## Données joueurs & stockage
- **HoeData** stocke niveau, XP, prestige, skin et niveaux d’enchants avec garde-fous (valeurs minimales, identifiants en minuscule).【F:src/main/java/fr/batistou15/prestigehoe/data/HoeData.java†L6-L114】
- **PlayerProfile** encapsule l’économie (essence, prestige tokens), statistiques, perks, skins, flags d’affichage et la `HoeData` principale. Les méthodes `addEssence`, `incrementCropBroken` ou `addMoneyEarned` tiennent les totaux à jour pour les recaps.【F:src/main/java/fr/batistou15/prestigehoe/data/PlayerProfile.java†L6-L194】
- **PlayerDataManager** gère un cache thread-safe, charge les profils à la volée, applique les valeurs par défaut de config (recap) et sauvegarde lors du quit ou via `saveAll`, en s’appuyant sur `DataStorage`.【F:src/main/java/fr/batistou15/prestigehoe/data/PlayerDataManager.java†L10-L105】
- **DataStorage** est implémenté par **JsonDataStorage** (fichiers `data/players/<uuid>.json` via Gson, chargement global pour leaderboard), **SqliteDataStorage** et **MysqlDataStorage** (choisis dans `setupStorage`). Les sauvegardes JSON loggent chaque appel et corrigent l’UUID manquant si besoin.【F:src/main/java/fr/batistou15/prestigehoe/data/JsonDataStorage.java†L13-L129】【F:src/main/java/fr/batistou15/prestigehoe/PrestigeHoePlugin.java†L398-L515】

## Services de farm
- **FarmService** fédère les services métier : progression, affichage, formules de récompense (prestige inclus), upgrades d’enchant, prestige shop et exécution du farm. Il délègue le `BlockBreakEvent` à `FarmExecutionService`, expose les helpers d’XP, les upgrades/disenchant et les actions de prestige.【F:src/main/java/fr/batistou15/prestigehoe/farm/FarmService.java†L19-L181】
- **HoeProgressionService**, **HoeDisplayService**, **RewardFormulaService**, **EnchantUpgradeService**, **PrestigeService** et **FarmExecutionService** sont instanciés dans le constructeur et se basent sur les managers injectés (config, economy, enchants, crops).【F:src/main/java/fr/batistou15/prestigehoe/farm/FarmService.java†L31-L74】

## Enchants
- **EnchantManager** instancie et enregistre tous les enchants « core » (fortune, autosell, boosters, pouches, fury, proc, etc.) ainsi que les enchants à venir (explosive, line breaker, speed, jobs). Il recharge chaque enchant via `reloadAll` et crée dynamiquement les enchants personnalisés `custom_enchantX` depuis `enchants.yml`. Les getters exposent chaque enchant spécifique.【F:src/main/java/fr/batistou15/prestigehoe/enchant/EnchantManager.java†L14-L200】
- Chaque enchant dédié (`FortuneEnchant`, `AutosellEnchant`, `EssenceBoosterEnchant`, etc.) hérite de `HoeEnchant` pour définir son coût, ses effets et ses hooks de proc (voir `src/main/java/fr/batistou15/prestigehoe/enchant/`).
- **EnchantProcHelper** et **EnchantDebugService** centralisent respectivement les déclenchements communs et la visibilité debug.

## Menus & interface
- **MenuManager** charge les fichiers `menus/*.yml`, assemble les templates d’items (enchants, prestige shop, skins, crops, leaderboards) et construit les services spécialisés (`MenuPlaceholderService`, `MenuItemFactory`, `EnchantMenuService`, `PrestigeShopMenuService`, `SkinMenuService`, `CropsMenuService`, `LeaderboardMenuService`). Il garde les sélections par joueur pour les enchants et leaderboards.【F:src/main/java/fr/batistou15/prestigehoe/menu/MenuManager.java†L18-L200】
- **MenuListener** et **SkinMenuListener** gèrent les clics et navigations, tandis que **MenuAction**, **MenuConfig**, **MenuItemConfig**, **MenuIconConfig** et les holders définissent la structure des inventaires.
- **NotificationService** pilote les notifications (chat/action bar/title) avec des types énumérés via **NotificationType**.

## Ressources, crops et skins
- **CropManager** lit `crops.yml` pour enregistrer les cultures et **CropDefinition** décrit les récompenses/paramètres par crop.
- **HoeItemManager** construit la houe personnalisée (nom, lore, NBT) et applique les skins via **SkinManager**/**SkinDefinition**; la sélection se fait via les menus de skin.

## Boosts & prestiges
- **BoostManager** charge les boosts temporaires, gère leurs icônes (**BoostIconConfig**) et l’activation (**ActiveBoost**, **BoostUseListener**). Les modes (**BoostMode**, **ItemBoostConflictMode**, **BoostType**) encodent la logique d’empilement.
- **PrestigeBonusService** fournit les multiplicateurs liés au prestige et alimente `RewardFormulaService` et les perks du prestige shop.

## Hooks & intégrations
- **EconomyHook** (Vault) et **JobsHook** sont initialisés depuis la config; **ProtocolLibEffectsUtil** protège les effets côté ProtocolLib. Les dépendances sont annoncées au démarrage via `debugDependencies`.【F:src/main/java/fr/batistou15/prestigehoe/PrestigeHoePlugin.java†L64-L558】

## Listeners et commandes
- Les listeners enregistrés couvrent la connexion joueur, la protection/menus de la hoe, la vitesse, le farm, l’usage de boosts, les TNT explosives et l’ouverture de menu par clic droit.【F:src/main/java/fr/batistou15/prestigehoe/PrestigeHoePlugin.java†L152-L205】
- Les commandes `/prestigehoe` et son alias `/essencehoe` utilisent **PrestigeHoeCommand** pour le reload, la distribution de hoe, l’édition de stats et l’ouverture de menus.【F:src/main/java/fr/batistou15/prestigehoe/PrestigeHoePlugin.java†L206-L228】

## Recap, leaderboards et utilitaires
- **RecapService** programme les recaps périodiques des gains; **LeaderboardService** agrège les statistiques (prestige, essence, crops) et alimente le menu dédié.
- Les utilitaires (**MessageUtil**, **NumberFormatUtil**, **ProtocolLibEffectsUtil**, **SkullUtil**) factorisent la coloration, le format numérique, la validation ProtocolLib et la génération de skulls personnalisées.

## Points d’attention
- L’auto-save asynchrone utilise `playerDataManager.saveAll()` sans verrou par fichier, ce qui peut créer des écritures concurrentes si un quit survient pendant l’auto-save.【F:src/main/java/fr/batistou15/prestigehoe/PrestigeHoePlugin.java†L463-L515】【F:src/main/java/fr/batistou15/prestigehoe/data/JsonDataStorage.java†L68-L86】
- Le reload des skins est appelé deux fois dans `reloadAllConfigs`, pouvant multiplier les I/O si un cache est ajouté.【F:src/main/java/fr/batistou15/prestigehoe/PrestigeHoePlugin.java†L344-L365】
- `FarmService.reloadConfig()` est vide, donc les changements de formules/enchant resteront en cache tant que les services spécialisés ne rechargent pas eux-mêmes leurs données.【F:src/main/java/fr/batistou15/prestigehoe/farm/FarmService.java†L146-L152】

