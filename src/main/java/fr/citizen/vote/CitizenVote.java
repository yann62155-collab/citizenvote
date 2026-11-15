package fr.citizen.vote;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Citizen Vote : /vote en jeu, vérification automatique sur les sites de vote,
 * récompenses, annonce chat + Discord (webhook).
 * Tout se passe côté serveur : rien à installer chez les joueurs (PC et console).
 */
@Mod(CitizenVote.MOD_ID)
public class CitizenVote {
    public static final String MOD_ID = "citizenvote";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CitizenVote(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(VoteCommand::register);
        NeoForge.EVENT_BUS.addListener(VoteEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(VoteEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(VoteEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(VoteEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(VoteEvents::onServerTick);
    }
}
