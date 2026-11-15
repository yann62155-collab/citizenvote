package fr.citizen.vote;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoteEvents {
    private VoteEvents() {}

    /** Rappel programmé après la connexion (UUID -> heure ms). */
    private static final Map<UUID, Long> RAPPEL_CONNEXION = new ConcurrentHashMap<>();
    private static long ticks = 0;
    private static long dernierRappel = System.currentTimeMillis();

    public static void onServerStarted(ServerStartedEvent e) {
        VoteConfig.load();
        VoteData.load();
        int prets = VoteConfig.get().sitesPrets().size();
        CitizenVote.LOGGER.info("[CitizenVote] Prêt : {} site(s) de vote configuré(s), Discord {}", prets,
                VoteConfig.get().discordWebhook.isBlank() ? "désactivé" : "activé");
    }

    public static void onServerStopping(ServerStoppingEvent e) {
        VoteData.save();
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        VoteData.Joueur j = VoteData.find(p.getUUID());
        if (j != null) j.nom = p.getGameProfile().getName();
        int s = VoteConfig.get().rappelConnexionSecondes;
        if (s > 0) RAPPEL_CONNEXION.put(p.getUUID(), System.currentTimeMillis() + s * 1000L);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        RAPPEL_CONNEXION.remove(e.getEntity().getUUID());
        VoteService.EN_ATTENTE.remove(e.getEntity().getUUID());
    }

    public static void onServerTick(ServerTickEvent.Post e) {
        ticks++;
        if (ticks % 20 != 0) return; // une fois par seconde
        MinecraftServer server = e.getServer();
        long now = System.currentTimeMillis();

        // Rappel à la connexion
        for (Iterator<Map.Entry<UUID, Long>> it = RAPPEL_CONNEXION.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            if (en.getValue() > now) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p != null) VoteService.rappel(p);
        }

        // Vérification automatique toutes les 30 s après /vote
        if (ticks % (20 * 30) == 0) {
            for (Iterator<Map.Entry<UUID, Long>> it = VoteService.EN_ATTENTE.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Long> en = it.next();
                if (en.getValue() < now) { it.remove(); continue; }
                ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
                if (p == null) { it.remove(); continue; }
                VoteService.verifier(p, false);
            }
        }

        // Rappel général
        int every = VoteConfig.get().rappelMinutes;
        if (every > 0 && now - dernierRappel >= every * 60_000L) {
            dernierRappel = now;
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (!VoteService.EN_ATTENTE.containsKey(p.getUUID())) VoteService.rappel(p);
        }
    }
}
