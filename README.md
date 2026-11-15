# Citizen Vote (citizenvote)

Mod **serveur uniquement** pour NeoForge 21.1.215 / Minecraft 1.21.1 / Java 21.
Les joueurs n'ont rien à installer : ça marche aussi pour les joueurs console (Geyser).

## Ce que ça fait
- `/vote` : menu dans le chat avec un bouton **[➜ VOTER]** par site (ouvre la page de vote) + le lien en clair pour les joueurs console.
- Vérification **automatique** du vote (toutes les 30 s pendant 10 min après `/vote`), ou bouton **[✔ J'ai voté]** (`/vote verifier`).
- Récompense : **10 PO** en pièces Citizen Economy par vote, **10 PO maximum par jour** et par joueur (`/vote admin po <par vote> <max par jour>`).
- Annonce dans le chat : « X a voté pour le serveur sur Top-Serveurs ! [Voter aussi] ».
- **Annonce Discord** dans **#🗳️-vote**, postée par le **bot de Citizen Discord** (même token). Le compte Discord du joueur est mentionné grâce à la whitelist (`/pseudo`). Webhook possible en secours.
- `/vote top` (classement du mois), `/vote stats`, rappels à la connexion et toutes les 60 min.

## Mise en route (en jeu, OP)
1. `/vote admin types` → trouve ton site de vote et la clé qu'il demande.
2. `/vote admin site ajouter <type> <clé> <url de ta page de vote>`
   ex. `/vote admin site ajouter top-serveurs.net ABCD1234 https://top-serveurs.net/minecraft/vote/citizen`
3. Discord : donne au bot l'accès au salon #🗳️-vote (Voir le salon + Envoyer des messages), puis
   `/vote admin discord statut` et `/vote admin discord test`.
4. Autre salon : `/vote admin discord salon <id>`.

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
