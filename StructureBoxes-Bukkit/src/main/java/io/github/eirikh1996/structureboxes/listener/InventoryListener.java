package io.github.eirikh1996.structureboxes.listener;

import io.github.eirikh1996.structureboxes.Structure;
import io.github.eirikh1996.structureboxes.StructureManager;
import io.github.eirikh1996.structureboxes.localisation.I18nSupport;
import io.github.eirikh1996.structureboxes.utils.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.github.eirikh1996.structureboxes.utils.ChatUtils.COMMAND_PREFIX;
import static java.lang.System.currentTimeMillis;

public class InventoryListener implements Listener {
    private final Map<UUID, Long> playerTimeMap = new HashMap<>();

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
        final Player p = (Player) event.getPlayer();
        Structure structure = StructureManager.getInstance().getStructureAt(MathUtils.bukkit2SBLoc(event.getInventory().getLocation()));
        if (structure == null)
            return;
        if (!playerTimeMap.containsKey(p.getUniqueId()) || currentTimeMillis() - playerTimeMap.get(p.getUniqueId()) > 5000) {
            p.sendMessage(COMMAND_PREFIX + I18nSupport.getInternationalisedString("Inventory - Editing will expire session"));
            event.setCancelled(true);
        }
        playerTimeMap.put(p.getUniqueId(), currentTimeMillis());
    }

    @EventHandler
    public void onItemMove(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Structure structure = StructureManager.getInstance().getStructureAt(MathUtils.bukkit2SBLoc(source.getLocation()));
        if (structure == null)
            return;
        Player p = Bukkit.getPlayer(structure.getOwner());
        if (p != null)
            p.sendMessage(COMMAND_PREFIX + I18nSupport.getInternationalisedString("Inventory - Expired due to editing inventory"));
        StructureManager.getInstance().removeStructure(structure);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.NOTHING)
            return;
        Structure structure = StructureManager.getInstance().getStructureAt(MathUtils.bukkit2SBLoc(event.getClickedInventory().getLocation()));
        if (structure == null)
            return;
        event.getWhoClicked().sendMessage(COMMAND_PREFIX + I18nSupport.getInternationalisedString("Inventory - Expired due to editing inventory"));
        StructureManager.getInstance().removeStructure(structure);
    }
}
