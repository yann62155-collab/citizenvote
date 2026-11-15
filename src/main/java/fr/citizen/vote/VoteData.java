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
        Long last = j.dernierVote.get(site.type.toLowerCase());
        if (last == null) return 0;
        long fin = last + site.cooldownMinutes * 60_000L;
        return Math.max(0, fin - System.currentTimeMillis());
    }

    public static synchronized Joueur enregistrer(UUID id, String nom, VoteConfig.Site site) {
        Joueur j = get(id, nom);
        j.total++;
        j.mois.merge(YearMonth.now().toString(), 1, Integer::sum);
        j.dernierVote.put(site.type.toLowerCase(), System.currentTimeMillis());
        save();
        return j;
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
