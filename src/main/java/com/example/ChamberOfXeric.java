package com.example;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChamberOfXeric {

    private final boolean challengeMode;
    private final String raidType;          // "Regular" or "Challenge Mode"
    private final String status;            // "Completed", "Aborted", "In Progress"
    private final Instant startTime;
    private final Instant endTime;          // null if ongoing
    private final Duration duration;        // null if ongoing
    private final int partySize;
    private final List<String> partyMembers;
    private final int personalPoints;       // -1 if unknown/not parsed yet
    private final int totalPoints;

    public ChamberOfXeric(
            boolean challengeMode,
            String status,
            Instant startTime,
            Instant endTime,
            int partySize,
            List<String> partyMembers,
            int personalPoints,
            int totalPoints) {

        this.challengeMode = challengeMode;
        this.raidType = challengeMode ? "Challenge Mode" : "Regular";
        this.status = status != null ? status : "In Progress";
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = (startTime != null && endTime != null)
                ? Duration.between(startTime, endTime)
                : null;
        this.partySize = partySize;
        this.partyMembers = partyMembers != null ? new ArrayList<>(partyMembers) : new ArrayList<>();
        this.personalPoints = personalPoints;
        this.totalPoints = totalPoints;
    }

    public boolean isChallengeMode() {
        return challengeMode;
    }

    public String getRaidType() {
        return raidType;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        return duration;
    }

    public String getDurationFormatted() {
        if (duration == null) return "In progress";
        long min = duration.toMinutes();
        long sec = duration.getSeconds() % 60;
        return String.format("%d min %02d sec", min, sec);
    }

    public String getStartTimeFormatted() {
        if (startTime == null) return "Unknown";
        return startTime.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public int getPartySize() {
        return partySize;
    }

    public List<String> getPartyMembers() {
        return new ArrayList<>(partyMembers);
    }

    public int getPersonalPoints() {
        return personalPoints;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    // JSON-friendly view (use with gson.toJson(raid.toJsonView()))
    public static class JsonView {
        public final String raidType;
        public final String status;
        public final String startTimeISO;
        public final String endTimeISO;
        public final String durationISO;
        public final String durationHuman;
        public final int partySize;
        public final List<String> partyMembers;
        public final int personalPoints;
        public final int totalPoints;

        public JsonView(ChamberOfXeric raid) {
            this.raidType = raid.raidType;
            this.status = raid.status;
            this.startTimeISO = raid.startTime != null ? raid.startTime.toString() : null;
            this.endTimeISO = raid.endTime != null ? raid.endTime.toString() : null;
            this.durationISO = raid.duration != null ? raid.duration.toString() : null;
            this.durationHuman = raid.getDurationFormatted();
            this.partySize = raid.partySize;
            this.partyMembers = raid.getPartyMembers();
            this.personalPoints = raid.personalPoints;
            this.totalPoints = raid.totalPoints;
        }
    }

    public JsonView toJsonView() {
        return new JsonView(this);
    }

    @Override
    public String toString() {
        return String.format("CoX %s | %s | Start: %s | Duration: %s | Party: %d | Points: %d personal / %d total | Members: %s",
                raidType, status, getStartTimeFormatted(), getDurationFormatted(),
                partySize, personalPoints, totalPoints, String.join(", ", partyMembers));
    }
}