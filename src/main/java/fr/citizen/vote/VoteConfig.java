package fr.citizen.vote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configuration : config/citizenvote/config.json (modifiable aussi en jeu avec /vote admin). */
public final class VoteConfig {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static VoteConfig instance = new VoteConfig();

    // ---- Général ----
    public String nomServeur = "Citizen";
    /** Annonce « X a voté » à tout le serveur. */
    public boolean annonceChat = true;
    /** Rappel « tu peux voter » toutes les X minutes (0 = jamais). */
    public int rappelMinutes = 60;
    /** Rappel X secondes après la connexion (0 = jamais). */
    public int rappelConnexionSecondes = 60;
    /** Après /vote, le mod vérifie tout seul toutes les 30 s pendant X minutes. */
    public int verificationAutoMinutes = 10;

    // ---- Récompense en pièces (Citizen Economy) ----
    /** PO donnés pour un vote (pièces physiques de citizeneconomy). */
    public int recompensePO = 10;
    /** Maximum de PO gagnés par les votes, par joueur et par jour (heure de Paris). 0 = pas de limite. */
    public int plafondPOParJour = 10;

    // ---- Discord ----
    /** Salon où annoncer les votes (#🗳️-vote). Utilise le bot de Citizen Discord (même token). */
    public String discordSalonId = "1571188304626589816";
    /** true = poste avec le bot de Citizen Discord ; sinon (ou si le bot est absent) utilise discordWebhook. */
    public boolean discordViaBotCitizen = true;
    /** URL du webhook Discord (Salon > Modifier > Intégrations > Webhooks > Copier l'URL). Vide = désactivé. */
    public String discordWebhook = "";
    public String discordNom = "Citizen • Votes";
    public String discordAvatar = "";
    /** Couleur de l'encadré (or ancien). */
    public int discordCouleur = 0xC9A227;

    // ---- Récompenses : commandes lancées par la console, {player} = pseudo, {site} = nom du site ----
    /** Commandes en plus des PO (lancées seulement quand des PO sont donnés). */
    public List<String> recompenses = new ArrayList<>();

    public int versionConfig = 0;

    // ---- Sites de vote ----
    public List<Site> sites = new ArrayList<>(List.of(
            new Site("top-serveurs.net", "Top-Serveurs", "", "", 120),
            new Site("serveur-prive.net", "Serveur-Privé", "", "", 90)
    ));

    public static final class Site {
        /** Identifiant unique du site dans la config (par défaut = type ; ex. "personnalise-2"). */
        public String id = "";
        /** Type du site (voir /vote admin types). */
        public String type;
        /** Nom affiché en jeu et sur Discord. */
        public String nom;
        /** Page de vote ouverte par le joueur. */
        public String url;
        /** Token / clé API / ID du serveur fourni par le site. */
        public String cle;
        /** Délai entre deux votes sur ce site. */
        public int cooldownMinutes;
        public boolean actif = true;
        // Pour type "personnalise" uniquement : {key} {ip} {name}
        public String apiUrl = "";
        public String jsonChemin = "";
        public String valeurAttendue = "";

        public Site() {}

        public Site(String type, String nom, String url, String cle, int cooldownMinutes) {
            this.type = type;
            this.nom = nom;
            this.url = url;
            this.cle = cle;
            this.cooldownMinutes = cooldownMinutes;
        }

        public String id() { return id == null || id.isBlank() ? type : id; }

        public boolean pret() {
            return actif && url != null && !url.isBlank() && cle != null && !cle.isBlank()
                    && VoteChecker.provider(type) != null;
        }
    }

    public static VoteConfig get() { return instance; }

    public Site site(String type) {
        for (Site s : sites) if (s.id().equalsIgnoreCase(type)) return s;
        for (Site s : sites) if (s.type.equalsIgnoreCase(type)) return s;
        return null;
    }

    public List<Site> sitesPrets() {
        List<Site> out = new ArrayList<>();
        for (Site s : sites) if (s.pret()) out.add(s);
        return out;
    }

    static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("citizenvote"); }
    private static Path file() { return dir().resolve("config.json"); }

    public static synchronized void load() {
        try {
            Files.createDirectories(dir());
            if (Files.exists(file())) {
                VoteConfig c = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), VoteConfig.class);
                if (c != null) {
                    if (c.sites == null) c.sites = new ArrayList<>();
                    if (c.recompenses == null) c.recompenses = new ArrayList<>();
                    if (c.versionConfig < 2) { // v1 : la récompense par défaut était 8 fioles d'XP
                        c.recompenses.remove("give {player} minecraft:experience_bottle 8");
                        if (c.discordSalonId == null || c.discordSalonId.isBlank()) c.discordSalonId = "1571188304626589816";
                        c.versionConfig = 2;
                    }
                    instance = c;
                }
            }
            instance.versionConfig = 2;
            save(); // écrit les nouvelles options éventuelles
        } catch (Exception e) {
            CitizenVote.LOGGER.error("[CitizenVote] config.json illisible, valeurs par défaut utilisées", e);
            instance = new VoteConfig();
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(file(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CitizenVote.LOGGER.error("[CitizenVote] Impossible d'écrire config.json", e);
        }
    }
}
