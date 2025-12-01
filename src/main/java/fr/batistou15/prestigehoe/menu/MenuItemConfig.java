package fr.batistou15.prestigehoe.menu;

import java.util.List;

public class MenuItemConfig {

    private final String key;
    private final int slot;
    private final MenuIconConfig icon;
    private final String name;
    private final List<String> lore;
    private final MenuAction action;
    private final String actionValue;

    public MenuItemConfig(String key,
                          int slot,
                          MenuIconConfig icon,
                          String name,
                          List<String> lore,
                          MenuAction action,
                          String actionValue) {
        this.key = key;
        this.slot = slot;
        this.icon = icon;
        this.name = name;
        this.lore = lore;
        this.action = action;
        this.actionValue = actionValue;
    }

    public String getKey() {
        return key;
    }

    public int getSlot() {
        return slot;
    }

    public MenuIconConfig getIcon() {
        return icon;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public MenuAction getAction() {
        return action;
    }

    public String getActionValue() {
        return actionValue;
    }
}
