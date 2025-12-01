package fr.batistou15.prestigehoe.menu;

public enum MenuAction {
    NONE,
    CLOSE,
    OPEN_MENU,
    UPGRADE_ENCHANT,
    DISENCHANT_ENCHANT,
    OPEN_DISENCHANT_MENU,
    PRESTIGE_UP,
    PRESTIGE_SHOP_BUY,

    // Leaderboards
    OPEN_LEADERBOARD,
    LEADERBOARD_PREVIOUS_PAGE,
    LEADERBOARD_NEXT_PAGE,

    // Toggles menu Settings
    TOGGLE_RECAP,
    TOGGLE_NOTIF_CHAT,
    TOGGLE_NOTIF_ENCHANT_PROC,
    TOGGLE_NOTIF_LEVELUP,
    TOGGLE_NOTIF_ACTIONBAR,
    TOGGLE_NOTIF_TITLE,

    // Toggle des messages de proc pour UN enchant précis (cloche)
    TOGGLE_ENCHANT_NOTIF
}
