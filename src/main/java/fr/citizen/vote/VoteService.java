package fr.citizen.vote;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Logique : menu /vote, vérification, récompense, annonces. */
public final class VoteService {
    private VoteService() {}

    /** Joueurs dont on vérifie le vote automatiquement (UUID -> fin de la vérification, ms). */
    static final Map<UUID, Long> EN_ATTENTE = new ConcurrentHashMap<>();
    private static final Set<UUID> EN_COURS = ConcurrentHashMap.newKeySet();

    // ---------- Textes ----------
    static MutableComponent prefix() {
        return Component.literal("⚜ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Vote").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY));
    }

    static MutableComponent msg(String text, ChatFormatting color) {
        return prefix().append(Component.literal(text).withStyle(color));
    }

    static String duree(long ms) {
        long min = (ms + 59_999) / 60_000;
        if (min < 60) return min + " min";
        return (min / 60) + " h " + String.format("%02d", min % 60) + " min";
    }

    static MutableComponent bouton(String label, ChatFormatting color, ClickEvent click, String hover) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    // ---------- Menu /vote ----------
    public static void ouvrirMenu(ServerPlayer p) {
        VoteConfig c = VoteConfig.get();
        List<VoteConfig.Site> sites = c.sitesPrets();
        p.sendSystemMessage(Component.literal("━━━━━━━ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("⚜ Votez pour " + c.nomServeur + " ⚜").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" ━━━━━━━").withStyle(ChatFormatting.DARK_GRAY)));
        if (sites.isEmpty()) {
            p.sendSystemMessage(msg("Aucun site de vote n'est encore configuré. Préviens un admin !", ChatFormatting.RED));
            return;
        }
        boolean auMoinsUn = false;
        for (VoteConfig.Site s : sites) {
            long restant = VoteData.restant(p.getUUID(), s);
            MutableComponent line = Component.literal(" ✦ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(s.nom + " ").withStyle(ChatFormatting.YELLOW));
            if (restant <= 0) {
                auMoinsUn = true;
                line.append(bouton("[➜ VOTER]", ChatFormatting.GREEN, new ClickEvent(ClickEvent.Action.OPEN_URL, s.url),
                        "Ouvre la page de vote de " + s.nom));
            } else {
                line.append(Component.literal("✔ voté — à nouveau dans " + duree(restant)).withStyle(ChatFormatting.GRAY));
            }
            p.sendSystemMessage(line);
            if (restant <= 0) // lien en clair pour les joueurs console (liens non cliquables sur Bedrock)
                p.sendSystemMessage(Component.literal("    " + s.url).withStyle(ChatFormatting.DARK_GRAY)
                        .withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, s.url))));
        }
        if (auMoinsUn) {
            p.sendSystemMessage(Component.literal(" ").append(
                    bouton("[✔ J'ai voté]", ChatFormatting.GOLD, new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vote verifier"),
                            "Vérifie ton vote et récupère ta récompense"))
                    .append(Component.literal("  la vérification se fait aussi toute seule").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            EN_ATTENTE.put(p.getUUID(), System.currentTimeMillis() + Math.max(1, c.verificationAutoMinutes) * 60_000L);
        }
        VoteData.Joueur j = VoteData.find(p.getUUID());
        int mois = j == null ? 0 : j.ceMois();
        p.sendSystemMessage(Component.literal(" Tes votes ce mois : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(mois)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("   ").withStyle(ChatFormatting.GRAY))
                .append(bouton("[🏆 Classement]", ChatFormatting.YELLOW, new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vote top"),
                        "Meilleurs voteurs du mois")));
    }

    // ---------- Vérification ----------
    /** @param manuel true = le joueur a cliqué/tapé /vote verifier (on lui répond même si rien n'est trouvé) */
    public static void verifier(ServerPlayer p, boolean manuel) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        UUID id = p.getUUID();
        if (!EN_COURS.add(id)) {
            if (manuel) p.sendSystemMessage(msg("Vérification déjà en cours…", ChatFormatting.GRAY));
            return;
        }
        List<VoteConfig.Site> aVerifier = new ArrayList<>();
        for (VoteConfig.Site s : VoteConfig.get().sitesPrets())
            if (VoteData.restant(id, s) <= 0) aVerifier.add(s);
        if (aVerifier.isEmpty()) {
            EN_COURS.remove(id);
            EN_ATTENTE.remove(id);
            if (manuel) p.sendSystemMessage(msg("Tu as déjà voté partout, merci ! Reviens plus tard (/vote).", ChatFormatting.GREEN));
            return;
        }
        String ip = p.getIpAddress();
        String nom = p.getGameProfile().getName();
        boolean ipLocale = VoteChecker.ipLocale(ip);
        if (manuel) p.sendSystemMessage(msg("Vérification de ton vote…", ChatFormatting.GRAY));

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (VoteConfig.Site s : aVerifier) futures.add(VoteChecker.check(s, ip, nom));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, t) -> server.execute(() -> {
            EN_COURS.remove(id);
            ServerPlayer joueur = server.getPlayerList().getPlayer(id);
            if (joueur == null) return;
            int trouves = 0;
            for (int i = 0; i < aVerifier.size(); i++) {
                Boolean ok = futures.get(i).getNow(false);
                if (Boolean.TRUE.equals(ok)) {
                    trouves++;
                    VoteChecker.claim(aVerifier.get(i), ip, nom);
                    valider(server, joueur, aVerifier.get(i), false);
                }
            }
            if (trouves == 0 && manuel) {
                joueur.sendSystemMessage(msg("Vote pas encore détecté. Termine le vote sur le site (captcha), "
                        + "puis attends 1 à 2 minutes : la vérification est automatique.", ChatFormatting.YELLOW));
                if (ipLocale) {
                    boolean parIp = false;
                    for (VoteConfig.Site s : aVerifier) {
                        VoteChecker.Provider pr = VoteChecker.provider(s.type);
                        if (pr != null && pr.parIp()) parIp = true;
                    }
                    if (parIp) joueur.sendSystemMessage(msg("Ta connexion passe par un relais (console ?) : le vote par IP "
                            + "ne peut pas être vérifié automatiquement. Demande à un admin de le valider.", ChatFormatting.GOLD));
                }
            }
            boolean resteDispo = false;
            for (VoteConfig.Site s : VoteConfig.get().sitesPrets())
                if (VoteData.restant(id, s) <= 0) resteDispo = true;
            if (!resteDispo) EN_ATTENTE.remove(id);
        }));
    }

    /** Vote confirmé : enregistrement, récompenses, annonces chat + Discord. */
    public static void valider(MinecraftServer server, ServerPlayer p, VoteConfig.Site site, boolean parAdmin) {
        VoteConfig c = VoteConfig.get();
        String nom = p.getGameProfile().getName();
        VoteData.Joueur j = VoteData.enregistrer(p.getUUID(), nom, site);

        for (String cmd : c.recompenses) {
            if (cmd == null || cmd.isBlank()) continue;
            String line = cmd.replace("{player}", nom).replace("{site}", site.nom);
            if (line.startsWith("/")) line = line.substring(1);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), line);
            } catch (Exception e) {
                CitizenVote.LOGGER.warn("[CitizenVote] Commande de récompense en erreur « {} » : {}", line, e.getMessage());
            }
        }

        p.sendSystemMessage(msg("Merci pour ton vote sur " + site.nom + " ! Ta récompense t'a été remise.", ChatFormatting.GREEN));
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);

        if (c.annonceChat) {
            MutableComponent annonce = prefix()
                    .append(Component.literal(nom).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" a voté pour le serveur sur ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(site.nom).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" ! ").withStyle(ChatFormatting.YELLOW))
                    .append(bouton("[Voter aussi]", ChatFormatting.GREEN, new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vote"),
                            "Ouvre le menu de vote"));
            for (ServerPlayer other : server.getPlayerList().getPlayers())
                if (other != p) other.sendSystemMessage(annonce);
        }
        CitizenVote.LOGGER.info("[CitizenVote] Vote de {} sur {}{}", nom, site.type, parAdmin ? " (validé par un admin)" : "");
        DiscordWebhook.annoncerVote(nom, site, j.ceMois(), j.total, VoteData.totalServeurCeMois(), false);
    }

    /** Rappel « tu peux voter » (seulement s'il reste un site disponible). */
    public static void rappel(ServerPlayer p) {
        int dispo = 0;
        for (VoteConfig.Site s : VoteConfig.get().sitesPrets())
            if (VoteData.restant(p.getUUID(), s) <= 0) dispo++;
        if (dispo == 0) return;
        p.sendSystemMessage(prefix()
                .append(Component.literal("Soutiens " + VoteConfig.get().nomServeur + " : " + dispo
                        + (dispo > 1 ? " votes disponibles" : " vote disponible") + " avec récompense ! ").withStyle(ChatFormatting.YELLOW))
                .append(bouton("[/vote]", ChatFormatting.GREEN, new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vote"),
                        "Ouvre le menu de vote")));
    }
}
