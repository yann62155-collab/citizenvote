package fr.citizen.vote;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Donne des PO sous forme de pièces physiques de Citizen Economy (platine 1000, or 100, argent 10, bronze 1). */
public final class Pieces {
    private Pieces() {}

    private static final String[] IDS = {"coin_platinum", "coin_gold", "coin_silver", "coin_bronze"};
    private static final int[] VALEURS = {1000, 100, 10, 1};

    public static boolean economieDisponible() {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("citizeneconomy", "coin_bronze"));
    }

    /** @return true si les pièces ont été données */
    public static boolean donner(ServerPlayer p, int po) {
        if (po <= 0) return true;
        if (!economieDisponible()) {
            CitizenVote.LOGGER.warn("[CitizenVote] Citizen Economy absent : impossible de donner {} PO à {}", po, p.getGameProfile().getName());
            return false;
        }
        int reste = po;
        for (int i = 0; i < IDS.length; i++) {
            int n = reste / VALEURS[i];
            reste %= VALEURS[i];
            if (n <= 0) continue;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("citizeneconomy", IDS[i]));
            if (item == Items.AIR) continue;
            while (n > 0) {
                int lot = Math.min(n, 64);
                n -= lot;
                ItemStack stack = new ItemStack(item, lot);
                if (!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false); // inventaire plein : au sol
            }
        }
        p.inventoryMenu.broadcastChanges();
        return true;
    }
}
