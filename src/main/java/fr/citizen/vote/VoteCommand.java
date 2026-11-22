package fr.citizen.vote;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Joueurs : /vote, /vote verifier, /vote top, /vote stats
 * Admins (OP 2) : /vote admin ...
 */
public final class VoteCommand {
    private VoteCommand() {}

    private static final SuggestionProvider<CommandSourceStack> TYPES =
            (ctx, b) -> SharedSuggestionProvider.suggest(VoteChecker.types(), b);
    private static final SuggestionProvider<CommandSourceStack> SITES = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        for (VoteConfig.Site s : VoteConfig.get().sites) l.add(s.id());
        return SharedSuggestionProvider.suggest(l, b);
    };

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vote")
                .executes(ctx -> { VoteService.ouvrirMenu(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("verifier")
                        .executes(ctx -> { VoteService.verifier(ctx.getSource().getPlayerOrException(), true); return 1; }))
                .then(Commands.literal("top").executes(ctx -> top(ctx.getSource())))
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .executes(ctx -> aide(ctx.getSource()))
                        .then(Commands.literal("reload").executes(ctx -> {
                            VoteConfig.load();
                            ok(ctx, "Configuration rechargée (" + VoteConfig.get().sitesPrets().size() + " site(s) prêt(s)).");
                            return 1;
                        }))
                        .then(Commands.literal("types").executes(ctx -> types(ctx.getSource())))
                        .then(Commands.literal("discord")
                                .then(Commands.literal("test").executes(VoteCommand::discordTest))
                                .then(Commands.literal("salon")
                                        .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            if (!id.matches("\\d{15,25}")) { err(ctx, "ID de salon invalide (clic droit sur le salon → Copier l'identifiant)."); return 0; }
                                            VoteConfig.get().discordSalonId = id;
                                            VoteConfig.get().discordViaBotCitizen = true;
                                            VoteConfig.save();
                                            ok(ctx, "Salon d'annonce enregistré. Teste avec /vote admin discord test");
                                            return 1;
                                        })))
                                .then(Commands.literal("statut").executes(ctx -> {
                                    VoteConfig c = VoteConfig.get();
                                    ok(ctx, "Bot Citizen Discord : " + (DiscordWebhook.tokenBotCitizen() != null ? "token trouvé ✔" : "introuvable ✖ (Citizen Discord installé et configuré ?)"));
                                    ok(ctx, "Salon : " + c.discordSalonId + (c.discordViaBotCitizen ? "" : " (désactivé)"));
                                    ok(ctx, "Webhook de secours : " + (c.discordWebhook.isBlank() ? "aucun" : "oui"));
                                    ok(ctx, "Récompense : " + c.recompensePO + " PO par vote, " + (c.plafondPOParJour > 0 ? c.plafondPOParJour + " PO max par jour" : "sans limite")
                                            + (Pieces.economieDisponible() ? "" : " — ✖ Citizen Economy absent !"));
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    VoteConfig.get().discordWebhook = "";
                                    VoteConfig.get().discordViaBotCitizen = false;
                                    VoteConfig.save();
                                    ok(ctx, "Annonces Discord désactivées.");
                                    return 1;
                                }))
                                .then(Commands.argument("webhook", StringArgumentType.greedyString()).executes(ctx -> {
                                    String url = StringArgumentType.getString(ctx, "webhook").trim();
                                    if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) {
                                        err(ctx, "Ce n'est pas une URL de webhook Discord (https://discord.com/api/webhooks/...).");
                                        return 0;
                                    }
                                    VoteConfig.get().discordWebhook = url;
                                    VoteConfig.save();
                                    ok(ctx, "Webhook Discord enregistré. Teste-le avec /vote admin discord test");
                                    return 1;
                                })))
                        .then(Commands.literal("site")
                                .then(Commands.literal("liste").executes(ctx -> listeSites(ctx.getSource())))
                                .then(Commands.literal("ajouter")
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                                                .then(Commands.argument("cle", StringArgumentType.string())
                                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                                .executes(VoteCommand::ajouterSite)))))
                                .then(Commands.literal("cooldown")
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SITES)
                                                .then(Commands.argument("minutes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 10080))
                                                        .executes(ctx -> {
                                                            VoteConfig.Site s = VoteConfig.get().site(StringArgumentType.getString(ctx, "type"));
                                                            if (s == null) { err(ctx, "Site inconnu."); return 0; }
                                                            s.cooldownMinutes = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "minutes");
                                                            VoteConfig.save();
                                                            ok(ctx, s.nom + " : un vote toutes les " + s.cooldownMinutes + " min.");
                                                            return 1;
                                                        }))))
                                .then(Commands.literal("nom")
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SITES)
                                                .then(Commands.argument("nom", StringArgumentType.greedyString()).executes(ctx -> {
                                                    VoteConfig.Site s = VoteConfig.get().site(StringArgumentType.getString(ctx, "type"));
                                                    if (s == null) { err(ctx, "Site inconnu (/vote admin site liste)."); return 0; }
                                                    s.nom = StringArgumentType.getString(ctx, "nom").trim();
                                                    VoteConfig.save();
                                                    ok(ctx, "Nom affiché : " + s.nom);
                                                    return 1;
                                                }))))
                                .then(Commands.literal("retirer")
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SITES)
                                                .executes(ctx -> {
                                                    String t = StringArgumentType.getString(ctx, "type");
                                                    boolean r = VoteConfig.get().sites.removeIf(s -> s.id().equalsIgnoreCase(t));
                                                    VoteConfig.save();
                                                    if (r) ok(ctx, "Site " + t + " retiré."); else err(ctx, "Site inconnu.");
                                                    return r ? 1 : 0;
                                                }))))
                        .then(Commands.literal("po")
                                .then(Commands.argument("parVote", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100000))
                                        .then(Commands.argument("maxParJour", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100000))
                                                .executes(ctx -> {
                                                    VoteConfig c = VoteConfig.get();
                                                    c.recompensePO = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "parVote");
                                                    c.plafondPOParJour = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "maxParJour");
                                                    VoteConfig.save();
                                                    ok(ctx, c.recompensePO + " PO par vote, " + (c.plafondPOParJour > 0 ? c.plafondPOParJour + " PO max par jour." : "sans limite."));
                                                    return 1;
                                                }))))
                        .then(Commands.literal("diagnostic")
                                .then(Commands.argument("joueur", EntityArgument.player()).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "joueur");
                                    String ip = p.getIpAddress();
                                    CommandSourceStack src = ctx.getSource();
                                    ok(ctx, p.getGameProfile().getName() + " — IP vue par le serveur : " + ip
                                            + (VoteChecker.ipLocale(ip) ? " (locale : relais/console)" : "") + (ip.contains(":") ? " (IPv6)" : " (IPv4)"));
                                    for (VoteConfig.Site s : VoteConfig.get().sitesPrets()) {
                                        VoteChecker.check(s, ip, p.getGameProfile().getName()).thenAcceptBoth(
                                                VoteChecker.votesDuMois(s, p.getGameProfile().getName()), (parIp, n) ->
                                                src.getServer().execute(() -> src.sendSystemMessage(VoteService.msg(s.nom + " : vote par IP "
                                                        + (parIp ? "✔ détecté" : "✖ non détecté")
                                                        + (VoteChecker.aClassement(s) ? " · votes du mois au pseudo : " + (n < 0 ? "API indisponible" : n)
                                                        + " (référence " + VoteData.base(p.getUUID(), s.id()) + ")" : "")
                                                        + " · prochain vote possible dans " + VoteService.duree(VoteData.restant(p.getUUID(), s)),
                                                        ChatFormatting.YELLOW))));
                                    }
                                    return 1;
                                })))
                        .then(Commands.literal("valider")
                                .then(Commands.argument("joueur", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SITES)
                                                .executes(VoteCommand::validerAdmin))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("pseudo", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "pseudo");
                                            UUID id = VoteData.parNom(n);
                                            if (id == null) { err(ctx, "Aucun vote enregistré pour " + n + "."); return 0; }
                                            VoteData.reset(id);
                                            ok(ctx, "Votes de " + n + " remis à zéro.");
                                            return 1;
                                        })))));
    }

    // ---------- Joueurs ----------
    private static int top(CommandSourceStack src) {
        List<VoteData.Joueur> top = VoteData.top(10);
        src.sendSystemMessage(Component.literal("━━━ 🏆 Meilleurs voteurs du mois ━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (top.isEmpty()) src.sendSystemMessage(Component.literal(" Personne n'a encore voté ce mois-ci. Sois le premier : /vote").withStyle(ChatFormatting.GRAY));
        int i = 1;
        for (VoteData.Joueur j : top) {
            ChatFormatting c = i == 1 ? ChatFormatting.GOLD : i == 2 ? ChatFormatting.WHITE : i == 3 ? ChatFormatting.RED : ChatFormatting.GRAY;
            src.sendSystemMessage(Component.literal(" " + i + ". ").withStyle(c)
                    .append(Component.literal(j.nom).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" — " + j.ceMois() + " vote" + (j.ceMois() > 1 ? "s" : "")).withStyle(ChatFormatting.GRAY)));
            i++;
        }
        src.sendSystemMessage(Component.literal(" Total du serveur ce mois : " + VoteData.totalServeurCeMois()).withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int stats(ServerPlayer p) {
        VoteData.Joueur j = VoteData.find(p.getUUID());
        int m = j == null ? 0 : j.ceMois(), t = j == null ? 0 : j.total;
        p.sendSystemMessage(VoteService.msg("Ce mois : " + m + " vote(s) — Total : " + t + " vote(s).", ChatFormatting.YELLOW));
        return 1;
    }

    // ---------- Admin ----------
    private static int aide(CommandSourceStack s) {
        String[] lignes = {
                "/vote admin site ajouter <type> <clé> <url de la page de vote>",
                "/vote admin site liste | retirer <id> | cooldown <id> <minutes> | nom <id> <nom>",
                "/vote admin types  — sites pris en charge et clé à fournir",
                "/vote admin discord statut | test | salon <id> | off",
                "/vote admin discord <url d'un webhook>  — seulement sans le bot Citizen Discord",
                "/vote admin po <par vote> <max par jour>",
                "/vote admin valider <joueur> <type>  — valide un vote à la main",
                "/vote admin diagnostic <joueur>  — pourquoi un vote n'est pas détecté",
                "/vote admin reset <pseudo>",
                "/vote admin reload  — recharge config/citizenvote/config.json"};
        s.sendSystemMessage(Component.literal("⚜ Citizen Vote — administration").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String l : lignes) s.sendSystemMessage(Component.literal(" " + l).withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int types(CommandSourceStack s) {
        s.sendSystemMessage(Component.literal("Sites pris en charge :").withStyle(ChatFormatting.GOLD));
        for (String t : VoteChecker.types()) {
            VoteChecker.Provider p = VoteChecker.provider(t);
            s.sendSystemMessage(Component.literal(" • " + t).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" — " + p.aide()).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int listeSites(CommandSourceStack s) {
        List<VoteConfig.Site> sites = VoteConfig.get().sites;
        if (sites.isEmpty()) s.sendSystemMessage(Component.literal("Aucun site.").withStyle(ChatFormatting.GRAY));
        for (VoteConfig.Site site : sites) {
            String etat = site.pret() ? "✔ prêt" : (VoteChecker.provider(site.type) == null ? "✖ type inconnu"
                    : (site.cle == null || site.cle.isBlank()) ? "✖ clé manquante"
                    : (site.url == null || site.url.isBlank()) ? "✖ url manquante" : "✖ désactivé");
            s.sendSystemMessage(Component.literal(" • " + site.nom + " [" + site.id() + (site.id().equals(site.type) ? "" : ", " + site.type) + "] ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(etat + " — " + site.cooldownMinutes + " min").withStyle(site.pret() ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
        return 1;
    }

    private static int ajouterSite(CommandContext<CommandSourceStack> ctx) {
        String type = StringArgumentType.getString(ctx, "type").toLowerCase();
        VoteChecker.Provider p = VoteChecker.provider(type);
        if (p == null) { err(ctx, "Type inconnu. Liste : /vote admin types"); return 0; }
        String cle = StringArgumentType.getString(ctx, "cle");
        String url = StringArgumentType.getString(ctx, "url").trim();
        if (!url.startsWith("http")) { err(ctx, "L'URL de la page de vote doit commencer par https://"); return 0; }
        VoteConfig c = VoteConfig.get();
        // Même site = même type ET même clé (ou même lien) : on le met à jour. Sinon, c'est un nouveau site.
        VoteConfig.Site s = null;
        for (VoteConfig.Site x : c.sites)
            if (x.type.equalsIgnoreCase(type) && (cle.equals(x.cle) || url.equalsIgnoreCase(x.url))) s = x;
        if (s == null) {
            s = new VoteConfig.Site(type, nomJoli(type, url), url, cle, defautCooldown(type));
            String id = type;
            for (int n = 2; c.site(id) != null && c.site(id).id().equalsIgnoreCase(id); n++) id = type + "-" + n;
            s.id = id;
            c.sites.add(s);
        } else {
            s.cle = cle;
            s.url = url;
            s.actif = true;
        }
        VoteConfig.save();
        ok(ctx, s.nom + " configuré [" + s.id() + "] (" + s.cooldownMinutes + " min entre deux votes). Les joueurs le voient dans /vote.");
        ok(ctx, "Sites actuels : " + c.sites.size() + " (/vote admin site liste). Pour changer le nom affiché : /vote admin site nom " + s.id() + " <nom>");
        return 1;
    }

    private static int validerAdmin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "joueur");
        VoteConfig.Site s = VoteConfig.get().site(StringArgumentType.getString(ctx, "type"));
        if (s == null) { err(ctx, "Site inconnu (/vote admin site liste)."); return 0; }
        VoteService.valider(ctx.getSource().getServer(), p, s, true);
        ok(ctx, "Vote de " + p.getGameProfile().getName() + " sur " + s.nom + " validé.");
        return 1;
    }

    private static int discordTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!DiscordWebhook.actif()) { err(ctx, "Discord non configuré : il faut Citizen Discord (token du bot) + /vote admin discord salon <id>, ou un webhook."); return 0; }
        String nom = src.getPlayer() != null ? src.getPlayer().getGameProfile().getName() : "Ishawakiii";
        VoteConfig.Site s = VoteConfig.get().sites.isEmpty() ? new VoteConfig.Site("test", "Site de test", "", "", 0)
                : VoteConfig.get().sites.get(0);
        DiscordWebhook.annoncerVote(nom, s, 1, 1, VoteData.totalServeurCeMois(), VoteConfig.get().recompensePO, true).thenAccept(code ->
                src.getServer().execute(() -> {
                    if (code >= 200 && code < 300) src.sendSystemMessage(VoteService.msg("Message de test envoyé sur Discord ✔", ChatFormatting.GREEN));
                    else src.sendSystemMessage(VoteService.msg("Échec de l'envoi Discord (code " + code + ")" + (code == 403 ? " : le bot n'a pas accès au salon." : code == 404 ? " : salon introuvable." : "."), ChatFormatting.RED));
                }));
        return 1;
    }

    // ---------- Utilitaires ----------
    private static String nomJoli(String type, String url) {
        if (type.equals("personnalise")) {
            try { return java.net.URI.create(url).getHost().replaceFirst("^www\\.", ""); } catch (Exception e) { return "Site de vote"; }
        }
        return switch (type) {
            case "top-serveurs.net" -> "Top-Serveurs";
            case "serveur-prive.net" -> "Serveur-Privé";
            case "serveurs-minecraft.org" -> "Serveurs-Minecraft.org";
            case "serveur-minecraft.com" -> "Serveur-Minecraft.com";
            case "serveur-minecraft-vote.fr" -> "Serveur-Minecraft-Vote";
            case "minecraft-mp.com" -> "Minecraft-MP";
            case "cubevote.net" -> "CubeVote";
            case "personnalise" -> "Site de vote";
            default -> type;
        };
    }

    private static int defautCooldown(String type) {
        return switch (type) {
            case "serveur-prive.net" -> 90;
            case "top-serveurs.net", "top-games.net" -> 120;
            case "minecraft-mp.com", "minecraft-server.eu", "minecraftpocket-servers.com", "minecraft.fr" -> 1440;
            case "cubevote.net" -> 120; // CubeVote ne garde que les votes en attente : un vote = une récompense
            default -> 180;
        };
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, String m) {
        ctx.getSource().sendSystemMessage(VoteService.msg(m, ChatFormatting.GREEN));
    }

    private static void err(CommandContext<CommandSourceStack> ctx, String m) {
        ctx.getSource().sendSystemMessage(VoteService.msg(m, ChatFormatting.RED));
    }
}
