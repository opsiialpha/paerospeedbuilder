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
 * A build is only complete when no extra block (anything not part of the build) is left in the area.
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
        // Every block of the build is right; it only counts when nothing else is standing in the area either.
        return checked > 0 && extraBlocks(world, session, origin, null, 0) == 0;
    }

    /**
     * Counts the blocks inside the whole build area that are NOT part of the build when it sits at {@code origin}:
     * anything solid where the build wants air (inside the build's own box, so holes and gaps are checked too)
     * and anything at all outside the build's box. Fills {@code details} with the first {@code limit} of them.
     */
    public static int extraBlocks(ServerLevel world, spbAccuracy.BuildSession session, BlockVector3 origin, List<String> details, int limit) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return 0;
        }
        Clipboard clipboard = session.clipboard;
        BlockVector3 min = session.min;
        BlockVector3 max = session.max;
        BlockPos corner = BuildArea.minPos(server);
        int extra = 0;
        for (int y = 0; y < BuildArea.HEIGHT; ++y) {
            for (int x = 0; x < BuildArea.SIZE_XZ; ++x) {
                for (int z = 0; z < BuildArea.SIZE_XZ; ++z) {
                    BlockPos pos = corner.offset(x, y, z);
                    net.minecraft.world.level.block.state.BlockState actual = world.getBlockState(pos);
                    if (actual.isAir()) {
                        continue;
                    }
                    int sx = min.x() + (pos.getX() - origin.x());
                    int sy = min.y() + (pos.getY() - origin.y());
                    int sz = min.z() + (pos.getZ() - origin.z());
                    if (sx >= min.x() && sx <= max.x() && sy >= min.y() && sy <= max.y() && sz >= min.z() && sz <= max.z()) {
                        BlockState expected = clipboard.getBlock(BlockVector3.at(sx, sy, sz));
                        if (!expected.getBlockType().getId().equals("minecraft:air")) {
                            continue; // part of the build (already checked block by block)
                        }
                    }
                    ++extra;
                    if (details != null && details.size() < limit) {
                        details.add("\u00a77 (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") \u00a7cextra \u00a7f" + spbAccuracy.describeActual(actual));
                    }
                }
            }
        }
        return extra;
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
        List<String> extraDetails = new ArrayList<>();
        int extras = extraBlocks(world, session, best, extraDetails, 8);
        lines.add(total == 0 && extras == 0
            ? "\u00a7aEvery block matches at the best spot and nothing extra is in the area - the build counts as finished."
            : "\u00a7e" + total + " block(s) differ and " + extras + " extra block(s) in the area at the best spot:");
        lines.addAll(details);
        lines.addAll(extraDetails);
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
