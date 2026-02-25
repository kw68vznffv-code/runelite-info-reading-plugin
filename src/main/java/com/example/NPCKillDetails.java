package com.example;

import com.google.gson.Gson;
import net.runelite.api.NPC;

import java.time.Instant;

// POJO for kill details (similar to your EventDetails/BossKill)
public class NPCKillDetails {
    private transient NPC npc;
    private int npcId = -1;
    private String npcName;
    private int npcCombatLevel = -1;
    protected transient Instant startTime;
    private String started;
    protected transient Instant killTime;
    private String killed;
    private long durationMs = 0;
    private String duration;
    private int kc = 0;

    // Getters & Setters (add @Data from Lombok if preferred)
    public int getNpcId() { return npcId; }
    public void setNpcId(int npcId) { this.npcId = npcId; }

    public String getName() { return npcName; }
    public void setName(String name) { this.npcName = name; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
        this.started = startTime.toString();
    }

    public Instant getKillTime() { return killTime; }
    public void setKillTime(Instant killTime) {
        this.killTime = killTime;
        this.killed = killTime.toString();
        this.npcCombatLevel = npc.getCombatLevel();
    }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        this.duration = getFormattedDuration();
    }
    public String getFormattedDuration() {
        if (durationMs <= 0) {
            return "00:00.000";
        }

        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = durationMs % 1000;

        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    public int getKc() { return kc; }
    public void setKc(int kc) { this.kc = kc; }

    // JSON export (Gson is built into RuneLite)
    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return String.format("Kill: %s (ID:%d) | Duration:%dms | KC:%d",
                npcName, npcId, durationMs, kc);
    }

    public void setNPC(NPC npc) {
        this.npc = npc;
    }
}
