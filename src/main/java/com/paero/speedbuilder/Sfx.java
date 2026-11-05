package com.paero.speedbuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Sound cues, played only to the player.
 * Sound track 1: countdown ticks, build-start level-up, long challenge-complete fanfare on finish.
 * Sound track 2: same, but the build-start tune also plays when the build is finished.
 */
public class Sfx {
    public static final int TRACK_COUNT = 2;

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    /** The sound played on build start (the tune you want to hear on finish for track 2). */
    public static SoundEvent buildStartSound() {
        return SoundEvents.PLAYER_LEVELUP;
    }

    /** The sound played when a build is finished, for the given sound track. */
    public static SoundEvent finishSound(int track) {
        return track == 2 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
    }

    /** Chat "Starting in N..." tick. */
    public static void chatTick(ServerPlayer player) {
        play(player, SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f, 1.0f);
    }

    /** On-screen countdown number (5, 4, 3, 2, 1). The last number is higher pitched. */
    public static void countdownTick(ServerPlayer player, boolean last) {
        play(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, last ? 1.5f : 1.0f);
    }

    /** Countdown reached zero: build mode starts, go build. */
    public static void buildStart(ServerPlayer player) {
        play(player, buildStartSound(), 1.0f, 1.0f);
    }

    /** Build finished at 100% accuracy. */
    public static void buildFinish(ServerPlayer player) {
        if (player == null) return;
        play(player, finishSound(SpeedBuilderConfig.getSoundTrack(player.getUUID())), 1.0f, 1.0f);
    }
}
