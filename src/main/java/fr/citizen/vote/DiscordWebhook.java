package fr.citizen.vote;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/** Envoie un message sur un salon Discord via un webhook (aucun bot à héberger). */
public final class DiscordWebhook {
    private DiscordWebhook() {}

    public static CompletableFuture<Integer> annoncerVote(String joueur, VoteConfig.Site site, int ceMois, int total,
                                                          int serveurMois, boolean test) {
        VoteConfig c = VoteConfig.get();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", (test ? "🧪 [TEST] " : "🗳️ ") + "Nouveau vote pour " + c.nomServeur + " !");
        embed.addProperty("description", "**" + joueur + "** vient de voter sur **" + site.nom + "**.\n"
                + "Merci, voyageur ! Le royaume te récompense.");
        if (site.url != null && !site.url.isBlank()) embed.addProperty("url", site.url);
        embed.addProperty("color", c.discordCouleur);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject thumb = new JsonObject();
        thumb.addProperty("url", "https://mc-heads.net/avatar/" + joueur + "/96");
        embed.add("thumbnail", thumb);

        JsonArray fields = new JsonArray();
        fields.add(field("Ses votes ce mois", String.valueOf(ceMois), true));
        fields.add(field("Ses votes au total", String.valueOf(total), true));
        fields.add(field("Votes du serveur ce mois", String.valueOf(serveurMois), true));
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
        mentions.add("parse", new JsonArray()); // jamais de @everyone involontaire
        payload.add("allowed_mentions", mentions);
        return envoyer(payload);
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    /** @return code HTTP (204 = OK), -1 = pas de webhook, -2 = erreur réseau */
    public static CompletableFuture<Integer> envoyer(JsonObject payload) {
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
