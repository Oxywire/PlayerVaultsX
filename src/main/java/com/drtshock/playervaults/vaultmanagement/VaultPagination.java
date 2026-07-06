package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

public final class VaultPagination {
    public static final int STORAGE_ROWS = 5;
    public static final int STORAGE_SIZE = STORAGE_ROWS * 9;
    public static final int PAGINATED_SIZE = 6 * 9;
    public static final int NAVIGATION_START = STORAGE_SIZE;
    public static final int PREVIOUS_SLOT = NAVIGATION_START;
    public static final int PAGE_SLOT = NAVIGATION_START + 4;
    public static final int NEXT_SLOT = NAVIGATION_START + 8;
    private static boolean warnedItemModelUnsupported;

    private VaultPagination() {
    }

    public static int displaySize(boolean pagination) {
        return pagination ? PAGINATED_SIZE : STORAGE_SIZE;
    }

    public static boolean isNavigationSlot(int rawSlot, Inventory inventory) {
        return inventory.getSize() > STORAGE_SIZE && rawSlot >= NAVIGATION_START && rawSlot < inventory.getSize();
    }

    public static Inventory createStorageInventory(Inventory inventory) {
        Inventory storage = Bukkit.createInventory(null, STORAGE_SIZE);
        for (int slot = 0; slot < Math.min(STORAGE_SIZE, inventory.getSize()); slot++) {
            storage.setItem(slot, inventory.getItem(slot));
        }
        return storage;
    }

    public static void applyStorageContents(Inventory inventory, ItemStack[] deserialized) {
        Inventory storage = Bukkit.createInventory(null, STORAGE_SIZE);
        if (deserialized.length > STORAGE_SIZE) {
            for (ItemStack stack : deserialized) {
                if (stack != null && stack.getType() != Material.AIR) {
                    storage.addItem(stack);
                }
            }
        } else {
            for (int slot = 0; slot < deserialized.length; slot++) {
                storage.setItem(slot, deserialized[slot]);
            }
        }

        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            inventory.setItem(slot, storage.getItem(slot));
        }
    }

    public static void addNavigation(Inventory inventory, int currentVault, int vaultCount) {
        addNavigation(inventory, currentVault, vaultCount, currentVault > 1, currentVault < vaultCount);
    }

    public static void addNavigation(Inventory inventory, int currentVault, int vaultCount, boolean hasPrevious, boolean hasNext) {
        if (inventory.getSize() <= STORAGE_SIZE || vaultCount <= 1) {
            return;
        }

        Config.Pagination pagination = PlayerVaults.getInstance().getConf().getPagination();
        ItemStack filler = item(pagination.getBarrierItems());
        for (int slot = NAVIGATION_START; slot < PAGINATED_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        if (hasPrevious) {
            inventory.setItem(PREVIOUS_SLOT, item(pagination.getPreviousPage()));
        }
        if (hasNext) {
            inventory.setItem(NEXT_SLOT, item(pagination.getNextPage()));
        }
    }

    private static ItemStack item(Config.Pagination.ButtonItem config) {
        Material material = Material.matchMaterial(config.getType());
        if (material == null) {
            material = Material.BARRIER;
            PlayerVaults.getInstance().getLogger().warning("Invalid pagination button material: " + config.getType());
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (config.getDisplayName() != null && !config.getDisplayName().isEmpty()) {
                meta.setDisplayName(legacy(config.getDisplayName()));
            }
            List<String> lore = config.getLore();
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(VaultPagination::legacy).collect(Collectors.toList()));
            }
            applyItemModel(meta, config.getItemModel());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String legacy(String miniMessage) {
        return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static void applyItemModel(ItemMeta meta, String itemModel) {
        if (itemModel == null || itemModel.isEmpty()) {
            return;
        }

        Object key = createNamespacedKey(itemModel);
        if (key == null) {
            PlayerVaults.getInstance().getLogger().warning("Invalid pagination button item model: " + itemModel);
            return;
        }

        try {
            Method method = meta.getClass().getMethod("setItemModel", key.getClass());
            method.invoke(meta, key);
        } catch (ReflectiveOperationException ignored) {
            if (!warnedItemModelUnsupported) {
                warnedItemModelUnsupported = true;
                PlayerVaults.getInstance().getLogger().warning("Pagination button item models are not supported by this server version.");
            }
        }
    }

    private static Object createNamespacedKey(String value) {
        try {
            Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
            try {
                return keyClass.getMethod("fromString", String.class).invoke(null, value);
            } catch (NoSuchMethodException ignored) {
                String[] parts = value.split(":", 2);
                if (parts.length != 2) {
                    return null;
                }
                return keyClass.getConstructor(String.class, String.class).newInstance(parts[0], parts[1]);
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
