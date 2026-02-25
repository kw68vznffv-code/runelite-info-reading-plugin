package com.example;

import com.example.NPCKillDetails;
import com.google.gson.Gson;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.util.Text;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Clean tracker utility (instance-based for Client access; static fields avoided for thread-safety/best practice)
public class NPCKillTracker {

    static int[] TRACKED_NPC_IDS = {
            // Your existing ones
            15626, // BRUTUS (custom / recent boss?)
            14176, // YAMA
            5779, // GIANT_MOLE
            NpcID.VORKATH,
    };

    private final Client client;
    private final Set<Integer> trackedNpcIds;
    private final Map<NPC, NPCKillDetails> activeKills = new ConcurrentHashMap<>();
    private final List<NPCKillDetails> killHistory = new ArrayList<>();

    public NPCKillTracker(Client client, int... trackedIds) {
        this.client = client;
        this.trackedNpcIds = IntStream.of(trackedIds).boxed().collect(Collectors.toSet());
    }

    public NPCKillTracker(Client client, Set<Integer> trackedNpcIds) {
        this.client = client;
        this.trackedNpcIds = trackedNpcIds;
    }

    public static int[] getTrackedNpcIds() {
        return TRACKED_NPC_IDS;
    }

    public static void setTrackedNpcIds(int[] trackedNpcIds) {
        TRACKED_NPC_IDS = trackedNpcIds;
    }

    /**
     * Called on NpcSpawned: Starts tracking if ID matches.
     */
    public void track(NpcSpawned event) {
        final NPC npc = event.getNpc();
        if (!trackedNpcIds.contains(npc.getId())) {
            return;
        }

        final NPCKillDetails details = new NPCKillDetails();
        details.setNPC(npc);
        details.setNpcId(npc.getId());
        details.setName(Text.removeTags(npc.getName()));  // Clean name (removes color tags)
        details.setStartTime(Instant.now());

        activeKills.put(npc, details);
        // Optional: Trigger overlay/timer start here
    }

    /**
     * Called on ActorDeath: Ends tracking, calculates duration, adds to history.
     * Returns details if matched kill, else null.
     */
    public NPCKillDetails killed(ActorDeath event) {
        final Actor actor = event.getActor();
        NPC npc = (NPC) event.getActor();

        if (!(actor instanceof NPC)) {
            return null;
        }

        final NPCKillDetails details = activeKills.remove(npc);
        if (details == null) {
            return null;
        }

        details.setKillTime(Instant.now());
        details.setDurationMs(Duration.between(details.getStartTime(), details.getKillTime()).toMillis());
        killHistory.add(details);

        // Optional: Trigger overlay "kill complete"
        return details;
    }

    /**
     * Update KC from async ChatMessage (call in onChatMessage after parsing).
     * Matches last kill by normalized boss name.
     */
    public void updateKc(String chatBossName, int newKc) {
        final String normalized = Text.standardize(chatBossName).toLowerCase();  // RuneLite helper for clean matching
        for (int i = killHistory.size() - 1; i >= 0; i--) {
            final NPCKillDetails details = killHistory.get(i);
            if (Text.standardize(details.getName()).toLowerCase().contains(normalized)) {
                details.setKc(newKc);
                return;
            }
        }
    }

    public List<NPCKillDetails> getKillHistory() {
        return new ArrayList<>(killHistory);  // Defensive copy
    }

    public void clearHistory() {
        killHistory.clear();
    }
}


