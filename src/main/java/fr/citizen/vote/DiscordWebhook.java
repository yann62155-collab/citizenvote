package fr.citizen.vote;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Annonce des votes sur Discord.
 * 1) Par défaut : avec le bot de Citizen Discord (whitelist), dans le salon #🗳️-vote. Le pseudo est relié au
 *    compte Discord du joueur grâce à la whitelist (/pseudo), qui est affiché en mention (sans ping).
 * 2) Sinon : par un webhook Discord (discordWebhook).
 */
public final class DiscordWebhook {
    private DiscordWebhook() {}

    // ------------------------------------------------------------ lien avec Citizen Discord

    /** Token du bot lu dans config/citizendiscord/config.json (null si absent). */
    static String tokenBotCitizen() {
        try {
            Path f = FMLPaths.CONFIGDIR.get().resolve("citizendiscord").resolve("config.json");
            if (!Files.exists(f)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.has("token_bot")) return null;
            String t = o.get("token_bot").getAsString().trim();
            return t.length() > 30 && !t.startsWith("COLLE_ICI") ? t : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** ID Discord du joueur d'après la whitelist de Citizen Discord (null si inconnu ou mod absent). */
    static String discordIdDe(String pseudo) {
        try {
            Class<?> c = Class.forName("fr.citizen.discord.WhitelistStore", true, DiscordWebhook.class.getClassLoader());
            Method m = c.getMethod("ownerOf", String.class);
            Object r = m.invoke(null, pseudo);
            return r instanceof String s && s.matches("\\d{15,25}") ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean botDisponible() {
        VoteConfig c = VoteConfig.get();
        return c.discordViaBotCitizen && c.discordSalonId != null && c.discordSalonId.matches("\\d{15,25}") && tokenBotCitizen() != null;
    }

    public static boolean actif() {
        return botDisponible() || (VoteConfig.get().discordWebhook != null && !VoteConfig.get().discordWebhook.isBlank());
    }

    // ------------------------------------------------------------ annonce

    public static CompletableFuture<Integer> annoncerVote(String joueur, VoteConfig.Site site, int ceMois, int total,
                                                          int serveurMois, int po, boolean test) {
        if (botDisponible()) return annoncerParBot(joueur, site, ceMois, serveurMois, po, test);
        return annoncerParWebhook(joueur, site, ceMois, total, serveurMois, po, test);
    }

    private static CompletableFuture<Integer> annoncerParBot(String joueur, VoteConfig.Site site, int ceMois,
                                                             int serveurMois, int po, boolean test) {
        VoteConfig c = VoteConfig.get();
        String id = discordIdDe(joueur);
        StringBuilder t = new StringBuilder();
        if (test) t.append("🧪 *[TEST]* ");
        t.append("🗳️ **").append(joueur).append("**");
        if (id != null) t.append(" (<@").append(id).append(">)");
        t.append(" vient de voter pour **").append(c.nomServeur).append("** sur **").append(site.nom).append("** !");
        if (po > 0) t.append(" 💰 **+").append(po).append(" PO**");
        t.append("\n📊 Ses votes ce mois : **").append(ceMois).append("** · Votes du serveur ce mois : **").append(serveurMois).append("**");
        t.append("\n👉 Vote toi aussi : `/vote` en jeu");
        if (site.url != null && !site.url.isBlank()) t.append(" ou <").append(site.url).append(">");

        JsonObject body = new JsonObject();
        body.addProperty("content", t.toString());
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray()); // la mention s'affiche mais ne ping personne
        body.add("allowed_mentions", mentions);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/channels/" + c.discordSalonId + "/messages"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bot " + tokenBotCitizen())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "DiscordBot (https://github.com/yann62155-collab, 1.0) CitizenVote")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            return VoteChecker.HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        if (r.statusCode() >= 300) {
                            String aide = r.statusCode() == 403 ? " → le bot n'a pas accès au salon (Modifier le salon → Permissions → ajoute le bot avec « Voir le salon » et « Envoyer des messages »)"
                                    : r.statusCode() == 404 ? " → ID de salon introuvable (discordSalonId)" : "";
                            CitizenVote.LOGGER.warn("[CitizenVote] Discord a refusé l'annonce ({}){} : {}", r.statusCode(), aide, r.body());
                        }
                        return r.statusCode();
                    })
                    .exceptionally(e -> {
                        CitizenVote.LOGGER.warn("[CitizenVote] Discord injoignable : {}", e.getMessage());
                        return -2;
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(-2);
        }
    }

    private static CompletableFuture<Integer> annoncerParWebhook(String joueur, VoteConfig.Site site, int ceMois, int total,
                                                                 int serveurMois, int po, boolean test) {
        VoteConfig c = VoteConfig.get();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", (test ? "🧪 [TEST] " : "🗳️ ") + "Nouveau vote pour " + c.nomServeur + " !");
        embed.addProperty("description", "**" + joueur + "** vient de voter sur **" + site.nom + "**."
                + (po > 0 ? "\n💰 Récompense : **+" + po + " PO**" : ""));
        if (site.url != null && !site.url.isBlank()) embed.addProperty("url", site.url);
        embed.addProperty("color", c.discordCouleur);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject thumb = new JsonObject();
        thumb.addProperty("url", "https://mc-heads.net/avatar/" + joueur + "/96");
        embed.add("thumbnail", thumb);

        JsonArray fields = new JsonArray();
        fields.add(field("Ses votes ce mois", String.valueOf(ceMois)));
        fields.add(field("Ses votes au total", String.valueOf(total)));
        fields.add(field("Votes du serveur ce mois", String.valueOf(serveurMois)));
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Vote toi aussi : tape /vote en jeu");
        embed.add("footer", footer);

        JsonObject payload = new JsonObject();
        if (c.discordNom != null && !c.discordNom.isBlank()) payload.addProperty("username", c.discordNom);
        if (c.discordAvatar != null && !c.discordAvatar.isBlank()) payload.addProperty("avatar_url", c.discordAvatar);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        payload.add("allowed_mentions", mentions);
        return envoyerWebhook(payload);
    }

    private static JsonObject field(String name, String value) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", true);
        return f;
    }

    /** @return code HTTP (2xx = OK), -1 = rien de configuré, -2 = erreur réseau */
    static CompletableFuture<Integer> envoyerWebhook(JsonObject payload) {
        String url = VoteConfig.get().discordWebhook;
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(-1);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "CitizenVote/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();
            return VoteChecker.HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        if (r.statusCode() >= 300)
                            CitizenVote.LOGGER.warn("[CitizenVote] Discord a refusé le message ({}) : {}", r.statusCode(), r.body());
                        return r.statusCode();
                    })
                    .exceptionally(t -> {
                        CitizenVote.LOGGER.warn("[CitizenVote] Discord injoignable : {}", t.getMessage());
                        return -2;
                    });
        } catch (Exception e) {
            CitizenVote.LOGGER.warn("[CitizenVote] URL de webhook Discord invalide : {}", e.getMessage());
            return CompletableFuture.completedFuture(-2);
        }
    }
}
