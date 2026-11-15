# Citizen Vote (citizenvote)

Mod **serveur uniquement** pour NeoForge 21.1.215 / Minecraft 1.21.1 / Java 21.
Les joueurs n'ont rien à installer : ça marche aussi pour les joueurs console (Geyser).

## Ce que ça fait
- `/vote` : menu dans le chat avec un bouton **[➜ VOTER]** par site (ouvre la page de vote) + le lien en clair pour les joueurs console.
- Vérification **automatique** du vote (toutes les 30 s pendant 10 min après `/vote`), ou bouton **[✔ J'ai voté]** (`/vote verifier`).
- Récompense = commandes lancées par la console (`give {player} ...`, pièces PO de Citizen Economy, etc.).
- Annonce dans le chat : « X a voté pour le serveur sur Top-Serveurs ! [Voter aussi] ».
- **Message Discord** (webhook) : pseudo, tête du joueur, site, ses votes du mois et le total du serveur.
- `/vote top` (classement du mois), `/vote stats`, rappels à la connexion et toutes les 60 min.

## Mise en route (en jeu, OP)
1. `/vote admin types` → trouve ton site de vote et la clé qu'il demande.
2. `/vote admin site ajouter <type> <clé> <url de ta page de vote>`
   ex. `/vote admin site ajouter top-serveurs.net ABCD1234 https://top-serveurs.net/minecraft/vote/citizen`
3. Discord : Salon → Modifier → Intégrations → Webhooks → Nouveau → Copier l'URL, puis
   `/vote admin discord https://discord.com/api/webhooks/...` et `/vote admin discord test`.
4. Récompenses : `config/citizenvote/config.json` → `recompenses`, puis `/vote admin reload`.

## Sites pris en charge
top-serveurs.net, serveur-prive.net, serveurs-minecraft.org, serveur-minecraft.com, serveur-minecraft-vote.fr,
liste-serv-minecraft.fr, serveursminecraft.org, liste-serveurs.fr, liste-serveurs-minecraft.org, top-games.net,
serveurliste.com, serveurly.com, minecraft.fr, minecraft-serveurs.com, minecraft-mp.com, minecraftpocket-servers.com,
minecraft-server.eu, + `personnalise` (apiUrl / jsonChemin / valeurAttendue dans config.json).

## À savoir
- Le vote lui-même se fait sur le site (captcha obligatoire) : le mod ouvre la page et détecte le vote.
- La plupart des sites français vérifient **l'IP** du joueur. Si un joueur passe par un relais (console via ViaProxy),
  son IP peut être locale : un admin valide alors avec `/vote admin valider <joueur> <type>`.
- Fichiers : `config/citizenvote/config.json` (réglages) et `config/citizenvote/votes.json` (votes).
