package fr.citizen.vote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Vérifie auprès de l'API du site si le joueur a voté.
 * Les adresses d'API reprennent celles du plugin Vote d'Azuriom (CMS français très utilisé).
 */
public final class VoteChecker {
    enum Mode { JSON, BODY_EQUALS, BODY_NOT_TRUE }

    /** {key} = clé/token du site, {ip} = IP du joueur, {name} = pseudo. */
    record Provider(String id, String api, Mode mode, String path, String expected,
                    boolean bearer, String claimUrl, String aide) {
        boolean parIp() { return api.contains("{ip}") && !api.contains("{name}"); }
    }

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<>();

    private static void add(String id, String api, Mode mode, String path, String expected, String aide) {
        PROVIDERS.put(id, new Provider(id, api, mode, path, expected, false, null, aide));
    }

    static {
        add("top-serveurs.net", "https://api.top-serveurs.net/v1/votes/check-ip?server_token={key}&ip={ip}",
                Mode.JSON, "code", "200", "Token du serveur (panel Top-Serveurs)");
        add("serveur-prive.net", "https://serveur-prive.net/api/v1/servers/{key}/votes/{ip}",
                Mode.JSON, "success", "true", "Clé API (panel Serveur-Privé)");
        add("serveurs-minecraft.org", "https://www.serveurs-minecraft.org/api/is_valid_vote.php?id={key}&ip={ip}&duration=5&format=json",
                Mode.JSON, "votes", "1", "ID du serveur (dans l'adresse vote.php?id=XXXX)");
        add("serveur-minecraft.com", "https://serveur-minecraft.com/api/1/vote/{key}/{ip}/json",
                Mode.JSON, "vote", "1", "ID du serveur (serveur-minecraft.com/XXXX)");
        add("serveur-minecraft-vote.fr", "https://serveur-minecraft-vote.fr/api/v1/servers/{key}/vote/{ip}",
                Mode.JSON, "canVote", "false", "ID du serveur (serveur-minecraft-vote.fr/serveur/XXXX)");
        add("liste-serv-minecraft.fr", "https://liste-serv-minecraft.fr/api/check?server={key}&ip={ip}",
                Mode.JSON, "status", "200", "ID du serveur (serveur?id=XXXX)");
        add("serveursminecraft.org", "https://www.serveursminecraft.org/sm_api/peutVoter.php?id={key}&ip={ip}",
                Mode.BODY_NOT_TRUE, null, null, "ID du serveur (serveursminecraft.org/serveur/XXXX)");
        add("liste-serveurs.fr", "https://www.liste-serveurs.fr/api/checkVote/{key}/{ip}",
                Mode.JSON, "success", "true", "ID du serveur (nom.XXXX dans l'adresse)");
        add("liste-serveurs-minecraft.org", "https://api.liste-serveurs-minecraft.org/vote/vote_verification.php?server_id={key}&ip={ip}&duration=5",
                Mode.BODY_EQUALS, null, "1", "ID du serveur");
        add("top-games.net", "https://api.top-games.net/v1/votes/check-ip?server_token={key}&ip={ip}",
                Mode.JSON, "code", "200", "Token du serveur");
        add("serveurliste.com", "https://serveurliste.com/api/vote?ip_address={ip}&api_token={key}",
                Mode.JSON, "data.voted", "true", "Token API");
        add("serveurly.com", "https://serveurly.com/api/v1/votes/check?token={key}&username={name}&ip={ip}",
                Mode.JSON, "data.has_voted", "true", "Token");
        add("minecraft.fr", "https://minecraft.fr/wp-json/serveursmc/v1/votes/{name}?api_key={key}",
                Mode.JSON, "has_voted", "true", "Clé API (vote au pseudo)");
        PROVIDERS.put("minecraft-serveurs.com", new Provider("minecraft-serveurs.com",
                "https://minecraft-serveurs.com/api/v1/vote-check?ip={ip}&name={name}",
                Mode.JSON, "voted", "true", true, null, "Clé API (Bearer)"));
        // Sites anglais (vote au pseudo) : la vérification renvoie 1 = voté pas encore récupéré
        for (String d : List.of("minecraft-mp.com", "minecraftpocket-servers.com")) {
            PROVIDERS.put(d, new Provider(d, "https://" + d + "/api/?object=votes&element=claim&key={key}&username={name}",
                    Mode.BODY_EQUALS, null, "1", false,
                    "https://" + d + "/api/?action=post&object=votes&element=claim&key={key}&username={name}",
                    "Clé API du serveur (vote au pseudo)"));
        }
        PROVIDERS.put("minecraft-server.eu", new Provider("minecraft-server.eu",
                "https://minecraft-server.eu/api/v1/?object=votes&element=claim&key={key}&username={name}",
                Mode.BODY_EQUALS, null, "1", false,
                "https://minecraft-server.eu/api/v1/?action=post&object=votes&element=claim&key={key}&username={name}",
                "Clé API du serveur (vote au pseudo)"));
        // CubeVote : vérification au pseudo, votes "en attente" puis marqués comme récompensés (claim)
        PROVIDERS.put("cubevote.net", new Provider("cubevote.net",
                "https://cubevote.net/api/v1/vote?username={name}",
                Mode.JSON, "hasPendingVote", "true", true,
                "https://cubevote.net/api/v1/vote/claim", "Clé API (page de gestion du serveur sur cubevote.net/dashboard)"));
        // Site non listé : remplir apiUrl / jsonChemin / valeurAttendue dans config.json
        add("personnalise", "", Mode.JSON, null, null, "apiUrl + jsonChemin + valeurAttendue dans config.json");
    }

    public static Provider provider(String type) {
        return type == null ? null : PROVIDERS.get(type.toLowerCase());
    }

    public static List<String> types() { return new ArrayList<>(PROVIDERS.keySet()); }

    public static boolean ipLocale(String ip) {
        return ip == null || ip.isBlank() || ip.startsWith("127.") || ip.equals("localhost")
                || ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("0:0:0:0") || ip.equals("::1")
                || ip.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    private static String fill(String tpl, VoteConfig.Site site, String ip, String name) {
        return tpl.replace("{key}", enc(site.cle)).replace("{ip}", enc(ip)).replace("{name}", enc(pseudoNet(name)));
    }

    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }

    /** Vérification asynchrone : ne bloque jamais le serveur. */
    public static CompletableFuture<Boolean> check(VoteConfig.Site site, String ip, String name) {
        Provider p = provider(site.type);
        if (p == null) return CompletableFuture.completedFuture(false);
        String api = p.id().equals("personnalise") ? site.apiUrl : p.api();
        String path = p.id().equals("personnalise") ? site.jsonChemin : p.path();
        String expected = p.id().equals("personnalise") ? site.valeurAttendue : p.expected();
        if (api == null || api.isBlank()) return CompletableFuture.completedFuture(false);
        HttpRequest.Builder rb;
        try {
            rb = HttpRequest.newBuilder(URI.create(fill(api, site, ip, name)))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "CitizenVote/1.0 (Minecraft NeoForge)")
                    .header("Accept", "application/json, text/plain, */*")
                    .GET();
        } catch (Exception e) {
            CitizenVote.LOGGER.warn("[CitizenVote] URL d'API invalide pour {} : {}", site.type, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
        if (p.bearer()) rb.header("Authorization", "Bearer " + site.cle);
        final Mode mode = p.mode();
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> evaluate(mode, path, expected, resp.statusCode(), resp.body()))
                .exceptionally(t -> {
                    CitizenVote.LOGGER.warn("[CitizenVote] API {} injoignable : {}", site.type, t.getMessage());
                    return false;
                });
    }

    static boolean evaluate(Mode mode, String path, String expected, int status, String body) {
        if (body == null) return false;
        String b = body.trim();
        switch (mode) {
            case BODY_EQUALS:
                return b.equals(expected);
            case BODY_NOT_TRUE:
                return status == 200 && !b.isEmpty() && b.length() < 64 && !b.equalsIgnoreCase("true") && !b.startsWith("<");
            case JSON:
            default:
                try {
                    JsonElement el = JsonParser.parseString(b);
                    for (String part : path.split("\\.")) {
                        if (el == null || !el.isJsonObject()) return false;
                        JsonObject o = el.getAsJsonObject();
                        el = o.get(part);
                    }
                    if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return false;
                    String v = el.getAsString();
                    if (v.equalsIgnoreCase(expected)) return true;
                    // 200 vs 200.0, 1 vs "1"
                    try { return Double.parseDouble(v) == Double.parseDouble(expected); }
                    catch (NumberFormatException ignored) { return false; }
                } catch (Exception e) {
                    return false;
                }
        }
    }

    // ------------------------------------------------------------ vérification par pseudo (classement)

    /** Sites qui publient le classement des voteurs du mois par pseudo (le pseudo est demandé au moment du vote). */
    private static final Map<String, String> CLASSEMENT = Map.of(
            "top-serveurs.net", "https://api.top-serveurs.net/v1/servers/{key}/players-ranking?type=current",
            "top-games.net", "https://api.top-games.net/v1/servers/{key}/players-ranking?type=current");

    /** Paramètre ajouté au lien de vote pour pré-remplir le pseudo. */
    private static final Map<String, String> PARAM_PSEUDO = Map.of(
            "top-serveurs.net", "pseudo",
            "top-games.net", "pseudo");

    public static boolean aClassement(VoteConfig.Site site) { return CLASSEMENT.containsKey(site.type.toLowerCase()); }

    /** Lien de vote avec le pseudo pré-rempli quand le site le permet. */
    public static String lienVote(VoteConfig.Site site, String pseudo) {
        String param = PARAM_PSEUDO.get(site.type.toLowerCase());
        if (param == null || site.url == null || site.url.contains(param + "=")) return site.url;
        return site.url + (site.url.contains("?") ? "&" : "?") + param + "=" + enc(pseudoNet(pseudo));
    }

    /** Pseudo sans le préfixe des joueurs console (Geyser/ViaProxy). */
    static String pseudoNet(String p) {
        String n = p == null ? "" : p;
        while (!n.isEmpty() && (n.charAt(0) == '.' || n.charAt(0) == '*')) n = n.substring(1);
        return n;
    }

    private record Cache(long temps, Map<String, Integer> votes) {}
    private static final Map<String, Cache> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Nombre de votes du joueur ce mois sur le site (-1 = inconnu / API indisponible). Cache de 20 s. */
    public static CompletableFuture<Integer> votesDuMois(VoteConfig.Site site, String pseudo) {
        String tpl = CLASSEMENT.get(site.type.toLowerCase());
        if (tpl == null) return CompletableFuture.completedFuture(-1);
        String cle = pseudoNet(pseudo).toLowerCase();
        Cache c = CACHE.get(site.id().toLowerCase());
        if (c != null && System.currentTimeMillis() - c.temps() < 20_000)
            return CompletableFuture.completedFuture(c.votes().getOrDefault(cle, 0));
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(tpl.replace("{key}", enc(site.cle))))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "CitizenVote/1.1 (Minecraft NeoForge)")
                    .header("Accept", "application/json").GET().build();
            return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
                try {
                    JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!o.has("players") || !o.get("players").isJsonArray()) {
                        CitizenVote.LOGGER.warn("[CitizenVote] Classement {} illisible ({}) : {}", site.type, resp.statusCode(),
                                resp.body().length() > 200 ? resp.body().substring(0, 200) : resp.body());
                        return -1;
                    }
                    Map<String, Integer> votes = new java.util.HashMap<>();
                    for (JsonElement e : o.getAsJsonArray("players")) {
                        JsonObject pl = e.getAsJsonObject();
                        if (!pl.has("playername") || !pl.has("votes")) continue;
                        votes.merge(pseudoNet(pl.get("playername").getAsString()).toLowerCase(), pl.get("votes").getAsInt(), Integer::sum);
                    }
                    CACHE.put(site.id().toLowerCase(), new Cache(System.currentTimeMillis(), votes));
                    return votes.getOrDefault(cle, 0);
                } catch (Exception ex) {
                    CitizenVote.LOGGER.warn("[CitizenVote] Classement {} illisible : {}", site.type, ex.getMessage());
                    return -1;
                }
            }).exceptionally(t -> {
                CitizenVote.LOGGER.warn("[CitizenVote] Classement {} injoignable : {}", site.type, t.getMessage());
                return -1;
            });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(-1);
        }
    }

    /** Oublie le cache (après un vote détecté, pour relire le chiffre exact). */
    public static void viderCache(VoteConfig.Site site) { CACHE.remove(site.id().toLowerCase()); }

    /** Marque le vote comme récupéré sur les sites qui le demandent (minecraft-mp…). */
    public static void claim(VoteConfig.Site site, String ip, String name) {
        Provider p = provider(site.type);
        if (p == null || p.claimUrl() == null) return;
        if (p.id().equals("cubevote.net")) {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("username", pseudoNet(name));
                HttpRequest req = HttpRequest.newBuilder(URI.create(p.claimUrl()))
                        .timeout(Duration.ofSeconds(8))
                        .header("Authorization", "Bearer " + site.cle)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "CitizenVote/1.4 (Minecraft NeoForge)")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
                HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                    if (r.statusCode() >= 300) CitizenVote.LOGGER.warn("[CitizenVote] CubeVote : claim refusé ({}) {}", r.statusCode(), r.body());
                });
            } catch (Exception ignored) {}
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(fill(p.claimUrl(), site, ip, name)))
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }
}
