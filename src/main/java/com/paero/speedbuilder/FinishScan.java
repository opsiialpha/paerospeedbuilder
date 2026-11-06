package com.paero.speedbuilder;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Finish detection helper. The build area is bigger than the builds, so a build counts as finished when it is
 * complete at the spot where the preview was shown, or complete anywhere else inside the build area.
 */
public final class FinishScan {
    private FinishScan() {
    }

    /** The paste origin the accuracy check should use: the session's own spot, or another spot with a complete build. */
    public static BlockVector3 resolve(ServerLevel world, spbAccuracy.BuildSession session) {
        BlockVector3 base = session.worldOrigin;
        try {
            if (matches(world, session, base)) {
                return base;
            }
            // Looking at every other spot is more work, so it is only done a few times a second.
            if (world.getGameTime() % 4L != 0L) {
                return base;
            }
            MinecraftServer server = world.getServer();
            if (server == null) {
                return base;
            }
            BlockVector3 corner = BuildArea.origin(server);
            int spanX = BuildArea.SIZE_XZ - (session.max.x() - session.min.x() + 1);
            int spanZ = BuildArea.SIZE_XZ - (session.max.z() - session.min.z() + 1);
            for (int dx = 0; dx <= spanX; ++dx) {
                for (int dz = 0; dz <= spanZ; ++dz) {
                    BlockVector3 candidate = BlockVector3.at(corner.x() + dx, base.y(), corner.z() + dz);
                    if (candidate.x() == base.x() && candidate.z() == base.z()) {
                        continue;
                    }
                    if (matches(world, session, candidate)) {
                        return candidate;
                    }
                }
            }
        } catch (Exception ignored) {
            // detection must never break because of this helper
        }
        return base;
    }

    /** True when every block of the build is correctly placed with the build's minimum corner at the given origin. */
    private static boolean matches(ServerLevel world, spbAccuracy.BuildSession session, BlockVector3 origin) {
        Clipboard clipboard = session.clipboard;
        BlockVector3 min = session.min;
        BlockVector3 max = session.max;
        int checked = 0;
        for (int y = min.y(); y <= max.y(); ++y) {
            for (int x = min.x(); x <= max.x(); ++x) {
                for (int z = min.z(); z <= max.z(); ++z) {
                    BlockState expected = clipboard.getBlock(BlockVector3.at(x, y, z));
                    String expectedId = expected.getBlockType().getId();
                    if (expectedId.equals("minecraft:air")) {
                        continue;
                    }
                    ++checked;
                    BlockPos pos = new BlockPos(origin.x() + (x - min.x()), origin.y() + (y - min.y()), origin.z() + (z - min.z()));
                    if (!spbAccuracy.withinBlockMatch(expectedId, expected, world.getBlockState(pos))) {
                        return false;
                    }
                }
            }
        }
        return checked > 0;
    }

    /**
     * Text lines for /pb finish debug: where the build was looked for, how many blocks are wrong at the best
     * spot, and the first few wrong blocks (expected vs found).
     */
    public static List<String> report(ServerLevel world, spbAccuracy.BuildSession session) {
        List<String> lines = new ArrayList<>();
        BlockVector3 base = session.worldOrigin;
        BlockVector3 best = base;
        int bestWrong = mismatches(world, session, base, null, 0);
        try {
            MinecraftServer server = world.getServer();
            if (server != null) {
                BlockVector3 corner = BuildArea.origin(server);
                int spanX = BuildArea.SIZE_XZ - (session.max.x() - session.min.x() + 1);
                int spanZ = BuildArea.SIZE_XZ - (session.max.z() - session.min.z() + 1);
                for (int dx = 0; dx <= spanX; ++dx) {
                    for (int dz = 0; dz <= spanZ; ++dz) {
                        BlockVector3 candidate = BlockVector3.at(corner.x() + dx, base.y(), corner.z() + dz);
                        int wrong = mismatches(world, session, candidate, null, 0);
                        if (wrong < bestWrong) {
                            bestWrong = wrong;
                            best = candidate;
                        }
                    }
                }
            }
        } catch (Exception e) {
            lines.add("\u00a7cScan error: " + e);
        }
        List<String> details = new ArrayList<>();
        int total = mismatches(world, session, best, details, 8);
        lines.add("\u00a76Finish debug \u00a77build: \u00a7f" + session.buildName + " \u00a77completed flag: \u00a7f" + session.isCompleted);
        lines.add("\u00a77Preview spot: \u00a7f" + base.x() + " " + base.y() + " " + base.z()
            + " \u00a77best spot: \u00a7f" + best.x() + " " + best.y() + " " + best.z());
        lines.add(total == 0
            ? "\u00a7aEvery block matches at the best spot - the build counts as finished."
            : "\u00a7e" + total + " block(s) differ at the best spot:");
        lines.addAll(details);
        return lines;
    }

    /** Counts blocks that do not match at the origin; fills {@code details} with the first {@code limit} of them. */
    private static int mismatches(ServerLevel world, spbAccuracy.BuildSession session, BlockVector3 origin, List<String> details, int limit) {
        Clipboard clipboard = session.clipboard;
        BlockVector3 min = session.min;
        BlockVector3 max = session.max;
        int wrong = 0;
        for (int y = min.y(); y <= max.y(); ++y) {
            for (int x = min.x(); x <= max.x(); ++x) {
                for (int z = min.z(); z <= max.z(); ++z) {
                    BlockState expected = clipboard.getBlock(BlockVector3.at(x, y, z));
                    String expectedId = expected.getBlockType().getId();
                    if (expectedId.equals("minecraft:air")) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(origin.x() + (x - min.x()), origin.y() + (y - min.y()), origin.z() + (z - min.z()));
                    net.minecraft.world.level.block.state.BlockState actual = world.getBlockState(pos);
                    if (spbAccuracy.withinBlockMatch(expectedId, expected, actual)) {
                        continue;
                    }
                    ++wrong;
                    if (details != null && details.size() < limit) {
                        details.add("\u00a77 (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") \u00a7aexpected \u00a7f"
                            + spbAccuracy.describeExpected(expected) + " \u00a7cfound \u00a7f" + spbAccuracy.describeActual(actual));
                    }
                }
            }
        }
        return wrong;
    }
}
