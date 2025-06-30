package com.trynocs.tryLibs.utils.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

/**
 * Ein Builder für ItemStacks, um Items einfach und lesbar zu erstellen.
 *
 * Diese Klasse kann direkt aus anderen Plugins genutzt werden:
 *   import com.trynocs.tryLibs.utils.gui.ItemBuilder;
 *   ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD).setName("§bSuper-Schwert").build();
 *
 * Beispiel:
 *   ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD)
 *       .setName("§bSuper-Schwert")
 *       .setAmount(1)
 *       .addEnchant(Enchantment.DAMAGE_ALL, 5)
 *       .build();
 */
public class ItemBuilder {
    private ItemStack item;
    private ItemMeta meta;

    /**
     * Erzeugt einen ItemBuilder für das angegebene Material und SubID.
     * @param material Das Material des Items.
     * @param subID Die SubID des Items (veraltet, meist 0).
     */
    public ItemBuilder(Material material, short subID) {
        item = new ItemStack(material, 1, subID);
        meta = item.getItemMeta();
    }

    /**
     * Erzeugt einen ItemBuilder für das angegebene Material.
     * @param material Das Material des Items.
     */
    public ItemBuilder(Material material) {
        this(material, (short) 0);
    }

    /**
     * Setzt den Anzeigenamen des Items.
     * @param name Der Anzeigename.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder setName(String name) {
        meta.setDisplayName(name);
        return this;
    }

    /**
     * Setzt den lokalisierten Namen des Items.
     * @param localName Der lokalisierte Name.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder setLocalizedName(String localName) {
        meta.setLocalizedName(localName);
        return this;
    }

    /**
     * Setzt die Lore (Beschreibung) des Items.
     * @param lore Die Zeilen der Lore.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder setLore(String... lore) {
        meta.setLore(Arrays.asList(lore));
        return this;
    }

    /**
     * Setzt die Anzahl der Items im Stack.
     * @param amount Die Anzahl.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Setzt, ob das Item unzerbrechlich ist.
     * @param unbreakable true, wenn unzerbrechlich, sonst false.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder setUnbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    /**
     * Setzt die Haltbarkeit des Items.
     * @param durability Die Haltbarkeit.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder setDurability(short durability) {
        item.setDurability(durability);
        return this;
    }

    /**
     * Fügt dem Item eine Verzauberung hinzu.
     * @param enchantment Die Verzauberung.
     * @param level Das Level der Verzauberung.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder addEnchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    /**
     * Fügt dem Item eine Verzauberung hinzu, mit Option die Levelbegrenzung zu ignorieren.
     * @param enchantment Die Verzauberung.
     * @param level Das Level der Verzauberung.
     * @param ignoreLevelRestriction true, um Levelbegrenzungen zu ignorieren.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
        meta.addEnchant(enchantment, level, ignoreLevelRestriction);
        return this;
    }

    /**
     * Fügt dem Item ItemFlags hinzu.
     * @param itemFlags Die ItemFlags.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder addItemFlags(ItemFlag... itemFlags) {
        meta.addItemFlags(itemFlags);
        return this;
    }

    /**
     * Setzt den Kopf-Besitzer für Spieler-Köpfe.
     * @param owner Der Name des Spielers.
     * @return Dieser ItemBuilder.
     */
    public ItemBuilder setHeadOwner(String owner) {
        if (item.getType() == Material.PLAYER_HEAD || item.getType() == Material.PLAYER_WALL_HEAD) {
            SkullMeta skullMeta = (SkullMeta) meta;
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(owner);
            skullMeta.setOwningPlayer(offlinePlayer);
            meta = skullMeta;
        }
        return this;
    }

    /**
     * Erstellt das ItemStack-Objekt.
     * @return Das finale ItemStack-Objekt.
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}