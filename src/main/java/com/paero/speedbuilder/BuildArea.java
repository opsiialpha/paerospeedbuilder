package com.paero.speedbuilder;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where the build area is. Before /pb setup was ever used it is the old fixed area at the world origin
 * (-7, 0, -7) .. (7, 15, 7). /pb setup stores the area above the platform it builds, and the position is
 * saved in the world folder so it survives restarts.
 *
 * The area is 15 x 16 x 15; "origin" is its minimum (lowest, north-west) corner.
 */
public final class BuildArea {
    public static final int SIZE_XZ = 15;
    public static final int HEIGHT = 16;
    /** Build mode only lets you place blocks in the lowest 12 layers (y 0..11 relative to the area). */
    public static final int PLACE_HEIGHT = 12;

    private static final int LEGACY_X = -7;
    private static final int LEGACY_Y = 0;
    private static final int LEGACY_Z = -7;
    private static final String FILE_NAME = "paerospeedbuilder_area.properties";

    private static Path loadedFrom;
    private static boolean set;
    private static String dimension = "";
    private static int x = LEGACY_X;
    private static int y = LEGACY_Y;
    private static int z = LEGACY_Z;

    private BuildArea() {
    }

    // ---------------------------------------------------------------- persistence

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    /** (Re)loads the saved area when a different world than the cached one is asked for. */
    private static synchronized void ensureLoaded(MinecraftServer server) {
        Path path = file(server);
        if (path.equals(loadedFrom)) {
            return;
        }
        loadedFrom = path;
        set = false;
        dimension = "";
        x = LEGACY_X;
        y = LEGACY_Y;
        z = LEGACY_Z;
        if (!Files.exists(path)) {
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            if (Boolean.parseBoolean(props.getProperty("set", "false"))) {
                x = Integer.parseInt(props.getProperty("x"));
                y = Integer.parseInt(props.getProperty("y"));
                z = Integer.parseInt(props.getProperty("z"));
                dimension = props.getProperty("dimension", "");
                set = true;
                // Areas saved by older versions were 7 wide and centred on the spot where /pb setup was used.
                // The area is 15 wide now, so move its corner out to keep the same centre.
                int savedSize = Integer.parseInt(props.getProperty("size", "7"));
                if (savedSize != SIZE_XZ) {
                    int shift = (SIZE_XZ - savedSize) / 2;
                    x -= shift;
                    z -= shift;
                }
            }
        } catch (Exception e) {
            SpeedBuilderMod.LOGGER.error("Failed to read the saved build area: " + e.getMessage());
            set = false;
            x = LEGACY_X;
            y = LEGACY_Y;
            z = LEGACY_Z;
        }
    }

    private static void save(MinecraftServer server) {
        Path path = file(server);
        try {
            if (!set) {
                Files.deleteIfExists(path);
                return;
            }
            Properties props = new Properties();
            props.setProperty("set", "true");
            props.setProperty("size", String.valueOf(SIZE_XZ));
            props.setProperty("dimension", dimension);
            props.setProperty("x", String.valueOf(x));
            props.setProperty("y", String.valueOf(y));
            props.setProperty("z", String.valueOf(z));
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "PaeroBuilder build area (minimum corner)");
            }
        } catch (IOException e) {
            SpeedBuilderMod.LOGGER.error("Failed to save the build area: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- state

    /** True once /pb setup has placed an area in this world. */
    public static synchronized boolean isSet(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        ensureLoaded(server);
        return set;
    }

    /** Dimension the area was set up in (for example minecraft:overworld), or "" for the default area. */
    public static synchronized String dimension(MinecraftServer server) {
        if (server == null) {
            return "";
        }
        ensureLoaded(server);
        return dimension;
    }

    public static synchronized void set(MinecraftServer server, String dimensionId, BlockPos minCorner) {
        ensureLoaded(server);
        set = true;
        dimension = dimensionId;
        x = minCorner.getX();
        y = minCorner.getY();
        z = minCorner.getZ();
        save(server);
    }

    /** Forgets the saved area; the default area at the world origin is used again. */
    public static synchronized void clear(MinecraftServer server) {
        ensureLoaded(server);
        set = false;
        dimension = "";
        x = LEGACY_X;
        y = LEGACY_Y;
        z = LEGACY_Z;
        save(server);
    }

    // ---------------------------------------------------------------- geometry

    private static synchronized int[] corner(MinecraftServer server) {
        if (server == null) {
            return new int[]{LEGACY_X, LEGACY_Y, LEGACY_Z};
        }
        ensureLoaded(server);
        return new int[]{x, y, z};
    }

    /** Minimum corner of the whole build area. Builds are pasted with originFor(...) so they sit in the middle. */
    public static BlockVector3 origin(MinecraftServer server) {
        int[] c = corner(server);
        return BlockVector3.at(c[0], c[1], c[2]);
    }

    /**
     * Where a build of the given size is pasted: centred inside the area (a 7 wide build in the 15 wide
     * area starts 4 blocks in from each side). A build as wide as the area (or wider) starts at the corner.
     */
    public static BlockVector3 originFor(MinecraftServer server, BlockVector3 min, BlockVector3 max) {
        int[] c = corner(server);
        int width = max.x() - min.x() + 1;
        int depth = max.z() - min.z() + 1;
        return BlockVector3.at(c[0] + centreOffset(width), c[1], c[2] + centreOffset(depth));
    }

    /** /pb view: the paste point that makes the schematic's minimum corner land in the centred spot. */
    public static BlockVector3 viewPaste(MinecraftServer server, Clipboard clipboard) {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        BlockVector3 target = originFor(server, min, max);
        BlockVector3 o = clipboard.getOrigin();
        return target.add(o.x() - min.x(), o.y() - min.y(), o.z() - min.z());
    }

    /** How far a build of the given width is moved in from the area's edge to sit in the middle. */
    public static int centreOffset(int width) {
        return Math.max(0, (SIZE_XZ - width) / 2);
    }

    public static BlockPos minPos(MinecraftServer server) {
        int[] c = corner(server);
        return new BlockPos(c[0], c[1], c[2]);
    }

    public static BlockPos maxPos(MinecraftServer server) {
        int[] c = corner(server);
        return new BlockPos(c[0] + SIZE_XZ - 1, c[1] + HEIGHT - 1, c[2] + SIZE_XZ - 1);
    }

    /** Build mode: may blocks be placed here? */
    public static boolean contains(MinecraftServer server, BlockPos pos) {
        int[] c = corner(server);
        return pos.getX() >= c[0] && pos.getX() < c[0] + SIZE_XZ
            && pos.getY() >= c[1] && pos.getY() < c[1] + PLACE_HEIGHT
            && pos.getZ() >= c[2] && pos.getZ() < c[2] + SIZE_XZ;
    }

    /** Build mode: blocks below the build area (the platform / ground it sits on) can't be broken. */
    public static boolean isBelowFloor(MinecraftServer server, BlockPos pos) {
        return pos.getY() < corner(server)[1];
    }

    /** Fluids don't spread in and around the build area (7 blocks around it, 50 high). */
    public static boolean isNearForFluid(MinecraftServer server, BlockPos pos) {
        if (server == null) {
            return false;
        }
        int[] c = corner(server);
        int margin = 7;
        return pos.getX() >= c[0] - margin && pos.getX() <= c[0] + SIZE_XZ - 1 + margin
            && pos.getZ() >= c[2] - margin && pos.getZ() <= c[2] + SIZE_XZ - 1 + margin
            && pos.getY() >= c[1] && pos.getY() <= c[1] + 50;
    }

    // ---------------------------------------------------------------- text

    /** "(x, y, z) to (x, y, z)" with chat colours, for messages. */
    public static String rangeText(MinecraftServer server) {
        BlockPos min = minPos(server);
        BlockPos max = maxPos(server);
        return "\u00a7e(" + min.getX() + ", " + min.getY() + ", " + min.getZ() + ") \u00a77to \u00a7e("
            + max.getX() + ", " + max.getY() + ", " + max.getZ() + ")";
    }
}
