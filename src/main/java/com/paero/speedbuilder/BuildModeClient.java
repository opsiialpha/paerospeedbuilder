package com.paero.speedbuilder;

public class BuildModeClient {
    private static boolean inBuildMode = false;

    public static boolean isInBuildMode() {
        return inBuildMode;
    }

    public static void setBuildMode(boolean enabled) {
        inBuildMode = enabled;
    }
}
