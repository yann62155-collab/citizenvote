package fr.citizen.vote;

import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Votes enregistrés : config/citizenvote/votes.json */
public final class VoteData {
    public static final class Joueur {
        public String nom = "";
        public int total = 0;
        /** "2026-11" -> nombre de votes */
        public Map<String, Integer> mois = new HashMap<>();
        /** type du site -> date du dernier vote (ms) */
        public Map<String, Long> dernierVote = new HashMap<>();

        /** "2026-11-15" -> PO reçus ce jour-là grâce aux votes */
        public Map<String, Integer> poJour = new HashMap<>();

        /** type du site -> nombre de votes du mois vu dans le classement du site (référence pour détecter un nouveau vote) */
        public Map<String, Integer> baseClassement = new HashMap<>();

        public int ceMois() { return mois.getOrDefault(YearMonth.now().toString(), 0); }
    }

    private static Map<String, Joueur> joueurs = new HashMap<>();

    private static Path file() { return VoteConfig.dir().resolve("votes.json"); }

    public static synchronized void load() {
        try {
            if (Files.exists(file())) {
                Map<String, Joueur> m = VoteConfig.GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Joueur>>() {}.getType());
                if (m != null) joueurs = new HashMap<>(m);
            }
        } catch (Exception e) {
            CitizenVote.LOGGER.error("[CitizenVote] votes.json illisible", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(VoteConfig.dir());
            Files.writeString(file(), VoteConfig.GSON.toJson(joueurs), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CitizenVote.LOGGER.error("[CitizenVote] Impossible d'écrire votes.json", e);
        }
    }

    public static synchronized Joueur get(UUID id, String nom) {
        Joueur j = joueurs.computeIfAbsent(id.toString(), k -> new Joueur());
        if (nom != null) j.nom = nom;
        return j;
    }

    public static synchronized Joueur find(UUID id) { return joueurs.get(id.toString()); }

    /** Millisecondes avant de pouvoir revoter sur ce site (0 = disponible). */
    public static synchronized long restant(UUID id, VoteConfig.Site site) {
        Joueur j = joueurs.get(id.toString());
        if (j == null) return 0;
        Long last = j.dernierVote.get(site.id().toLowerCase());
        if (last == null) return 0;
        long fin = last + site.cooldownMinutes * 60_000L;
        return Math.max(0, fin - System.currentTimeMillis());
    }

    public static synchronized Joueur enregistrer(UUID id, String nom, VoteConfig.Site site) {
        Joueur j = get(id, nom);
        j.total++;
        j.mois.merge(YearMonth.now().toString(), 1, Integer::sum);
        j.dernierVote.put(site.id().toLowerCase(), System.currentTimeMillis());
        save();
        return j;
    }

    public static String aujourdhui() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).toString();
    }

    /** PO restant à gagner aujourd'hui. */
    public static synchronized int poRestantAujourdhui(UUID id, int plafond) {
        if (plafond <= 0) return Integer.MAX_VALUE;
        Joueur j = joueurs.get(id.toString());
        int deja = j == null || j.poJour == null ? 0 : j.poJour.getOrDefault(aujourdhui(), 0);
        return Math.max(0, plafond - deja);
    }

    /** Réserve les PO du vote en respectant le plafond du jour. @return PO à donner (0 si plafond atteint) */
    public static synchronized int accorderPO(UUID id, String nom, int voulu, int plafond) {
        Joueur j = get(id, nom);
        if (j.poJour == null) j.poJour = new HashMap<>();
        String jour = aujourdhui();
        j.poJour.keySet().removeIf(k -> !k.equals(jour)); // on ne garde que le jour en cours
        int donne = Math.max(0, Math.min(voulu, poRestantAujourdhui(id, plafond)));
        if (donne > 0) j.poJour.merge(jour, donne, Integer::sum);
        save();
        return donne;
    }

    /** @return la référence enregistrée, ou null s'il n'y en a pas */
    public static synchronized Integer base(UUID id, String site) {
        Joueur j = joueurs.get(id.toString());
        return j == null || j.baseClassement == null ? null : j.baseClassement.get(site.toLowerCase());
    }

    public static synchronized void setBase(UUID id, String nom, String site, int valeur) {
        Joueur j = get(id, nom);
        if (j.baseClassement == null) j.baseClassement = new HashMap<>();
        Integer old = j.baseClassement.put(site.toLowerCase(), valeur);
        if (old == null || old != valeur) save();
    }

    public static synchronized boolean reset(UUID id) {
        boolean ok = joueurs.remove(id.toString()) != null;
        save();
        return ok;
    }

    public static synchronized UUID parNom(String nom) {
        for (Map.Entry<String, Joueur> e : joueurs.entrySet())
            if (e.getValue().nom.equalsIgnoreCase(nom)) return UUID.fromString(e.getKey());
        return null;
    }

    /** Classement du mois. */
    public static synchronized List<Joueur> top(int n) {
        List<Joueur> l = new ArrayList<>(joueurs.values());
        l.removeIf(j -> j.ceMois() <= 0);
        l.sort((a, b) -> Integer.compare(b.ceMois(), a.ceMois()));
        return l.subList(0, Math.min(n, l.size()));
    }

    public static synchronized int totalServeurCeMois() {
        int t = 0;
        for (Joueur j : joueurs.values()) t += j.ceMois();
        return t;
    }
}
