package fr.batistou15.prestigehoe.menu;

import java.util.Map;

public class MenuConfig {

    private final String id;
    private final String title;
    private final int size;
    private final Map<Integer, MenuItemConfig> items;

    public MenuConfig(String id, String title, int size, Map<Integer, MenuItemConfig> items) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.items = items;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public Map<Integer, MenuItemConfig> getItems() {
        return items;
    }

    public MenuItemConfig getItem(int slot) {
        return items != null ? items.get(slot) : null;
    }
}
