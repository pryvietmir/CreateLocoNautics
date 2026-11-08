package com.lycoris.loconautics.client.connected;

import com.lycoris.loconautics.client.QuadVertices;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class ConnectedModel implements IDynamicBakedModel {
    public static final ModelProperty<ConnectedModel.Shape> SHAPE = new ModelProperty();
    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(new RenderType[]{RenderType.cutout()});
    private static final int STRIP_SINGLE = 0;
    private static final int STRIP_START = 1;
    private static final int STRIP_MIDDLE = 2;
    private static final int STRIP_END = 3;
    private static final int STRIP_TILES = 4;
    private static final int CAP_COLUMNS = 2;
    private static final int CAP_ROWS = 4;
    private static final int CORNER_OUTER = 0;
    private static final int CORNER_INNER = 1;
    private static final int EDGE_HORIZONTAL = 2;
    private static final int EDGE_VERTICAL = 3;
    private static final int BACKGROUND = 4;
    private static final int FANCY_BACKGROUND = 5;
    private static final int FANCY_CORNER = 6;
    private static final float INNER_INSET = 6.25E-4F;
    private static final float OVERSHOOT = 0.01F;
    @Nullable
    private final TextureAtlasSprite strip;
    @Nullable
    private final TextureAtlasSprite cap;
    @Nullable
    private final TextureAtlasSprite inner;
    @Nullable
    private final TextureAtlasSprite innerCap;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;

    public ConnectedModel(
        @Nullable TextureAtlasSprite strip,
        @Nullable TextureAtlasSprite cap,
        @Nullable TextureAtlasSprite inner,
        @Nullable TextureAtlasSprite innerCap,
        TextureAtlasSprite particle,
        ItemTransforms transforms
    ) {
        this.strip = strip;
        this.cap = cap;
        this.inner = inner;
        this.innerCap = innerCap;
        this.particle = particle;
        this.transforms = transforms;
    }

    @NotNull
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        if (!(state.getBlock() instanceof ConnectedBlock block)) {
            return modelData;
        } else {
            Set<BlockPos> group = block.connectionBounds().group(block, level, state, block.groupAnchor(level, state, pos));
            if (!group.contains(pos)) {
                group = Set.of(pos.immutable());
            }

            boolean[] neighbors = new boolean[4];

            for (Direction dir : Plane.HORIZONTAL) {
                neighbors[dir.get2DDataValue()] = group.contains(pos.relative(dir));
            }

            return modelData.derive()
                .with(SHAPE, new ConnectedModel.Shape(capChunks(group, pos, Direction.UP), capChunks(group, pos, Direction.DOWN), neighbors))
                .build();
        }
    }

    private static int[] capChunks(Set<BlockPos> group, BlockPos pos, Direction face) {
        Direction up = face == Direction.UP ? Direction.NORTH : Direction.SOUTH;
        Direction down = up.getOpposite();
        Direction right = Direction.EAST;
        Direction left = Direction.WEST;
        boolean hasUp = group.contains(pos.relative(up));
        boolean hasDown = group.contains(pos.relative(down));
        boolean hasLeft = group.contains(pos.relative(left));
        boolean hasRight = group.contains(pos.relative(right));
        return applyFancy(
            new int[]{
                resolveChunk(hasUp, hasLeft, group.contains(pos.relative(up).relative(left))),
                resolveChunk(hasUp, hasRight, group.contains(pos.relative(up).relative(right))),
                resolveChunk(hasDown, hasLeft, group.contains(pos.relative(down).relative(left))),
                resolveChunk(hasDown, hasRight, group.contains(pos.relative(down).relative(right)))
            }
        );
    }

    private static int resolveChunk(boolean cardinalA, boolean cardinalB, boolean diagonal) {
        if (!cardinalA && !cardinalB) {
            return 0;
        } else if (cardinalA && !cardinalB) {
            return 3;
        } else if (!cardinalA) {
            return 2;
        } else {
            return diagonal ? 4 : 1;
        }
    }

    private static int[] applyFancy(int[] chunks) {
        int outerCount = 0;
        int backgroundCount = 0;

        for (int chunk : chunks) {
            if (chunk == 0) {
                outerCount++;
            }

            if (chunk == 4) {
                backgroundCount++;
            }
        }

        if (backgroundCount == chunks.length) {
            return new int[]{5, 5, 5, 5};
        }

        if (outerCount == 1) {
            for (int i = 0; i < chunks.length; i++) {
                if (chunks[i] == 0) {
                    chunks[i] = 6;
                }
            }
        }

        return chunks;
    }

    @NotNull
    public List<BakedQuad> getQuads(
        @Nullable BlockState state, @Nullable Direction face, @NotNull RandomSource rand, @NotNull ModelData modelData, @Nullable RenderType renderType
    ) {
        if (state == null) {
            return Collections.emptyList();
        } else {
            ConnectedModel.Shape shape = (ConnectedModel.Shape)modelData.get(SHAPE);
            if (face == null) {
                return this.innerQuads(state, shape);
            } else if (face.getAxis() == Axis.Y) {
                return capQuads(face, face == Direction.UP ? 1.0F : 0.0F, chunksOf(shape, face), this.cap, false);
            } else if (this.strip != null && !isOpen(state, shape, face)) {
                float u0 = stripTile(shape, face) / 4.0F;
                return List.of(quad(face, nearPlane(face) ? 0.0F : 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, this.strip, u0, 0.0F, u0 + 0.25F, 1.0F));
            } else {
                return Collections.emptyList();
            }
        }
    }

    private static int[] chunksOf(@Nullable ConnectedModel.Shape shape, Direction face) {
        if (shape == null) {
            return new int[]{0, 0, 0, 0};
        } else {
            return face == Direction.UP ? shape.up() : shape.down();
        }
    }

    private static List<BakedQuad> capQuads(Direction face, float plane, int[] chunks, @Nullable TextureAtlasSprite sprite, boolean mirror) {
        if (sprite == null) {
            return Collections.emptyList();
        }

        List<BakedQuad> quads = new ArrayList<>(4);

        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int xOffset = quadrant % 2;
            int yOffset = quadrant / 2;
            float u0 = (chunks[quadrant] % 2 * 2.0F + xOffset) / 4.0F;
            float v0 = (chunks[quadrant] / 2 * 2.0F + yOffset) / 8.0F;
            float u1 = u0 + 0.25F;
            float minX = xOffset * 0.5F;
            float minY = (1 - yOffset) * 0.5F;
            quads.add(quad(face, plane, minX, minY, minX + 0.5F, minY + 0.5F, sprite, mirror ? u1 : u0, v0, mirror ? u0 : u1, v0 + 0.125F));
        }

        return quads;
    }

    private List<BakedQuad> innerQuads(BlockState state, @Nullable ConnectedModel.Shape shape) {
        List<BakedQuad> quads = new ArrayList<>(12);
        if (this.inner != null) {
            for (Direction dir : Plane.HORIZONTAL) {
                if (!isOpen(state, shape, dir)) {
                    float plane = nearPlane(dir) ? 6.25E-4F : 0.999375F;
                    float u0 = stripTile(shape, dir) / 4.0F;
                    quads.add(quad(dir.getOpposite(), plane, -0.01F, -0.01F, 1.01F, 1.01F, this.inner, u0 + 0.25F, 0.0F, u0, 1.0F));
                }
            }
        }

        quads.addAll(capQuads(Direction.DOWN, 0.999375F, chunksOf(shape, Direction.UP), this.innerCap, true));
        quads.addAll(capQuads(Direction.UP, 6.25E-4F, chunksOf(shape, Direction.DOWN), this.innerCap, true));
        return quads;
    }

    private static boolean nearPlane(Direction dir) {
        return dir == Direction.NORTH || dir == Direction.WEST;
    }

    private static boolean isOpen(BlockState state, @Nullable ConnectedModel.Shape shape, Direction face) {
        return state.getBlock() instanceof ConnectedBlock block && block.isOpenFace(state, face) ? true : shape != null && shape.neighbor(face);
    }

    private static int stripTile(@Nullable ConnectedModel.Shape shape, Direction face) {
        if (shape == null) {
            return 0;
        } else {
            boolean towardU0 = shape.neighbor(face.getClockWise());
            boolean towardU1 = shape.neighbor(face.getCounterClockWise());
            if (!towardU0 && !towardU1) {
                return 0;
            } else if (!towardU0) {
                return 1;
            } else {
                return towardU1 ? 2 : 3;
            }
        }
    }

    private static BakedQuad quad(
        Direction face, float plane, float minX, float minY, float maxX, float maxY, TextureAtlasSprite sprite, float u0, float v0, float u1, float v1
    ) {
        Vector3f[] vertices = faceVertices(face, plane, minX, minY, maxX, maxY);
        float su0 = sprite.getU0() + u0 * (sprite.getU1() - sprite.getU0());
        float su1 = sprite.getU0() + u1 * (sprite.getU1() - sprite.getU0());
        float sv0 = sprite.getV0() + v0 * (sprite.getV1() - sprite.getV0());
        float sv1 = sprite.getV0() + v1 * (sprite.getV1() - sprite.getV0());
        int[] data = new int[32];
        putVertex(data, 0, vertices[0], su0, sv0, face);
        putVertex(data, 1, vertices[1], su0, sv1, face);
        putVertex(data, 2, vertices[2], su1, sv1, face);
        putVertex(data, 3, vertices[3], su1, sv0, face);
        return new BakedQuad(data, -1, face, sprite, true);
    }

    private static Vector3f[] faceVertices(Direction face, float p, float minX, float minY, float maxX, float maxY) {
        return switch (face) {
            case UP -> new Vector3f[]{
                new Vector3f(minX, p, 1.0F - maxY), new Vector3f(minX, p, 1.0F - minY), new Vector3f(maxX, p, 1.0F - minY), new Vector3f(maxX, p, 1.0F - maxY)
            };
            case DOWN -> new Vector3f[]{new Vector3f(minX, p, maxY), new Vector3f(minX, p, minY), new Vector3f(maxX, p, minY), new Vector3f(maxX, p, maxY)};
            case NORTH -> new Vector3f[]{
                new Vector3f(1.0F - minX, maxY, p), new Vector3f(1.0F - minX, minY, p), new Vector3f(1.0F - maxX, minY, p), new Vector3f(1.0F - maxX, maxY, p)
            };
            case SOUTH -> new Vector3f[]{new Vector3f(minX, maxY, p), new Vector3f(minX, minY, p), new Vector3f(maxX, minY, p), new Vector3f(maxX, maxY, p)};
            case WEST -> new Vector3f[]{new Vector3f(p, maxY, minX), new Vector3f(p, minY, minX), new Vector3f(p, minY, maxX), new Vector3f(p, maxY, maxX)};
            case EAST -> new Vector3f[]{
                new Vector3f(p, maxY, 1.0F - minX), new Vector3f(p, minY, 1.0F - minX), new Vector3f(p, minY, 1.0F - maxX), new Vector3f(p, maxY, 1.0F - maxX)
            };
            default -> throw new MatchException(null, null);
        };
    }

    private static void putVertex(int[] data, int index, Vector3f pos, float u, float v, Direction face) {
        QuadVertices.put(data, index, pos.x(), pos.y(), pos.z(), u, v, face);
    }

    @NotNull
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return RENDER_TYPES;
    }

    @NotNull
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, @NotNull RandomSource rand) {
        return this.getQuads(state, face, rand, ModelData.EMPTY, null);
    }

    public boolean useAmbientOcclusion() {
        return true;
    }

    public boolean isGui3d() {
        return true;
    }

    public boolean usesBlockLight() {
        return true;
    }

    public boolean isCustomRenderer() {
        return false;
    }

    @NotNull
    public ItemTransforms getTransforms() {
        return this.transforms;
    }

    @NotNull
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @NotNull
    public TextureAtlasSprite getParticleIcon() {
        return this.particle;
    }

    public record Shape(int[] up, int[] down, boolean[] neighbors) {
        public boolean neighbor(Direction dir) {
            return this.neighbors[dir.get2DDataValue()];
        }
    }
}
