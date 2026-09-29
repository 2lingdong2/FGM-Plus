package io.github.e33epus.fgmplus.mixin;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.GenderPlayer;
import com.wildfire.main.WildfireGender;
import com.wildfire.render.GenderLayer;
import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.github.e33epus.fgmplus.shape.ShapeRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bust sizes above the vanilla slider cap (0.8) never grow the breast geometry:
 * the model box is a fixed 4x5x4 and the tilt rotation is clamped, so everything
 * past the cap is faked with positional shifts. This mixin freezes those
 * positional terms at the vanilla cap level and applies a real scale to
 * the breast model instead, pivoting on the box corner that touches the chest.
 *
 * On top of that it layers the per-player ShapeData (three-axis scale + perkiness
 * + position offsets). Back overflow is cured by fp$flattenBack: every vertex
 * whose body-space z crosses the torso-back plane is pulled back onto it along
 * the body-z axis, so the back corner can never poke through while the front
 * fit stays exactly where the user put it. (The 1.2.x-1.3.5 translate-based
 * "recovery" aid is retired — it moved the whole box and broke the front fit.)
 */
@Mixin(GenderLayer.class)
public abstract class GenderLayerMixin {

    // Vanilla ClientConfiguration.BUST_SIZE upper bound
    @Unique private static final float fp$VANILLA_BUST_CAP = 0.8F;
    // GenderLayer#render passes breastSize = bSize + |bSize - 0.7|, which folds to a
    // constant 0.7 for bSize <= 0.7 and is invertible above that
    @Unique private static final float fp$PARAM_FOLD = 0.7F;
    // Replaces the retired bustScaleGain/bustScaleMax config keys (same values as their defaults)
    @Unique private static final float fp$SCALE_GAIN = 1.0F;
    @Unique private static final float fp$SCALE_MAX = 3.0F;
    // Torso BACK clamp plane, in body space. Front = -z and back = +z (established
    // empirically: the diagnostic panel at z = -2.25 px rendered on the chest front,
    // and the 1.3.0 clip keeping z > -2.25 px cut away the front). The plane sits
    // 0.125 px outside the vanilla torso back (+2 px) so flattened geometry neither
    // z-fights the skin nor the jacket skin layer at +2.25 px. Vertices with a body
    // z beyond it are pulled back onto the plane along the body-z axis
    // ("flattened"), never clipped: front-side geometry is untouched, so the chest
    // stays fitted to the front exactly as the user required.
    //
    // When it triggers (reviewer-recomputed, FGM 3.1 values): full vanilla slider
    // (bust 0.8, folded size 0.9, droop -31.5 deg) tops out at +1.61 px static —
    // inside the plane. FGM's true default (bust 0.6) sits at +2.04 px (+2.06 px
    // with the breathing animation) — still inside. Smaller busts, the bounce
    // animation's backward swing and large scaleZ/scaleY DO cross the plane —
    // and those are exactly the configurations where the vanilla geometry already
    // pokes out of the back, i.e. precisely what flattening is here to cure.
    @Unique private static final float fp$CLAMP_BACK_Z = 2.125F * 0.0625F;
    // FGM's droop: -35 deg times totalRotation (totalRotation is clamped to min(breastSize + 0.2, 1))
    @Unique private static final float fp$DROOP_DEG = 35.0F;

    @Unique private float fp$breastSize;
    @Unique private float fp$breastOffsetZ;
    @Unique private float fp$zOff;
    @Unique private ShapeData fp$shape;

    // Diagnostic back-plane state, written in fp$captureSize and consumed (and
    // nulled) by fp$drawDebugPlane at TAIL. Static because the TAIL handler for
    // the static-friendly capture chain mirrors the previous clip design; the
    // same entry pose + body transform is what the upcoming back-flatten vertex
    // clamp will be validated against.
    //Body view (entry pose + body-part transform) and its inverse, both built once
    //per breast per frame in fp$captureSize; fp$flattenBack clamps against the
    //inverse, fp$drawDebugPlane lifts the panel through the forward one. Both are
    //nulled at the capture-window TAIL; null = outside the window -> vanilla path
    @Unique private static Matrix4f fp$bodyView;
    @Unique private static Matrix4f fp$bodyViewInv;
    //0-based renderBox call index inside the current capture window (body, then
    //jacket-wear, then armor passes); reset per breast in fp$captureSize
    @Unique private static int fp$boxIndex;
    //Per-player roundness for the current capture window (0 = vanilla flat box ->
    //fp$flattenBack stays bit-identical to FGM's render); read in fp$flattenBack
    //to swap the flat quad emission for the superellipsoid mesh
    @Unique private static volatile float fp$roundness;
    //Cleavage-bridge toggle captured alongside it (default on = the shipped look)
    @Unique private static volatile boolean fp$cleavage = true;

    @Unique private static float fp$realScale(float param) {
        float bSize = param > fp$PARAM_FOLD ? (param + fp$PARAM_FOLD) * 0.5F : param;
        if (bSize <= fp$VANILLA_BUST_CAP) {
            return 1.0F;
        }
        return Math.min(1.0F + (bSize - fp$VANILLA_BUST_CAP) * fp$SCALE_GAIN, fp$SCALE_MAX);
    }

    @Inject(method = "renderBreastWithTransforms", at = @At("HEAD"), require = 1, remap = false)
    private void fp$captureSize(AbstractClientPlayer entity, ModelPart body, ItemStack armorStack, PoseStack matrixStack, MultiBufferSource bufferSource,
        RenderType breastRenderType, int packedLightIn, int combineTex, float alpha, boolean bounceEnabled, float totalX, float total, float bounceRotation,
        float breastSize, float breastOffsetX, float breastOffsetY, float breastOffsetZ, float zOff, float outwardAngle, boolean uniboob, boolean isChestplateOccupied,
        boolean breathingAnimation, boolean left, CallbackInfo ci) {
        fp$breastSize = breastSize;
        fp$breastOffsetZ = breastOffsetZ;
        fp$zOff = zOff;
        //Same lookup GenderLayer#render does; the shape is only read when a breast is actually rendered
        fp$shape = null;
        GenderPlayer plr = WildfireGender.getPlayerById(entity.getUUID());
        if (plr != null) {
            fp$shape = ((ShapeHolder) plr).fgmplus$getShape();
        }
        fp$roundness = fp$shape != null ? fp$shape.getRoundness() : 0.0F;
        fp$cleavage = fp$shape == null || fp$shape.isCleavage();
        //Body view = entry pose (the stack is untouched at HEAD) + the body-part
        //transform FGM applies right after (GenderLayer source:
        //translate(body.xyz * 0.0625) then guarded zRot/yRot/xRot mulPose). Two
        //consumers inside the capture window: fp$flattenBack (the back-flatten
        //clamp, always on) clamps against the inverse, and fp$drawDebugPlane lifts
        //the torso-back plane through the forward view. Both were proven in-game:
        //the user confirmed the panel hugs the torso back from every angle.
        //The GenderPlayer lookup above is load-bearing for the redirects and stays
        //unconditional; the capture itself must be unconditional too (the clamp
        //depends on it regardless of the diagnostic toggle)
        fp$boxIndex = 0;
        fp$bodyView = null;
        fp$bodyViewInv = null;
        try {
            Matrix4f view = new Matrix4f(matrixStack.last().pose());
            view.translate(body.x * 0.0625F, body.y * 0.0625F, body.z * 0.0625F);
            view.rotateZ(body.zRot);
            view.rotateY(body.yRot);
            view.rotateX(body.xRot);
            Matrix4f inv = view.invert(new Matrix4f());
            fp$bodyView = view;
            fp$bodyViewInv = inv;
        } catch (Exception ignored) {
            //degenerate pose: clamp and panel fall back to vanilla rendering
        }
    }

    @Redirect(method = "renderBreastWithTransforms", require = 1, remap = false,
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", remap = true))
    private void fp$applyRealScale(PoseStack stack, float x, float y, float z) {
        //Fingerprint guard: vanilla's z-fighting fix is scale(0.9995,1,1) at this point;
        //any other signature means upstream changed the call site order - stand down
        if (Math.abs(x - 0.9995F) > 1.0e-4F || y != 1.0F || z != 1.0F) {
            stack.scale(x, y, z);
            return;
        }
        ShapeData shape = fp$shape != null ? fp$shape : new ShapeData();
        float s = fp$realScale(fp$breastSize);
        //Perkiness first: rotates about the same X axis as FGM's hardcoded -35 deg droop,
        //positive values counteract the droop
        float perk = shape.getPerkiness();
        if (perk != 0.0F) {
            stack.mulPose(new Quaternionf().rotationXYZ(perk * Mth.DEG_TO_RAD, 0.0F, 0.0F));
        }
        //No recovery translate anymore: the translate-based aid (retired in 1.4.0)
        //moved the whole box toward the chest front, which broke the front fit the
        //user explicitly wants. Back overflow is now handled per-vertex by
        //fp$flattenBack, which leaves the front side untouched.
        //Each breast box already spans the full half-torso width, so the real-scale
        //growth (s) never applies to the X axis by default; the per-player scaleX
        //slider is the only way to widen (1.0 = vanilla width)
        stack.scale(x * shape.getScaleX(), y * s * shape.getScaleY(), z * s * shape.getScaleZ());
    }

    //4th translate call: the positional sink that deepens with bust size
    //(zOff = 0.0625 * (1 - bSize)); freeze it at the vanilla cap level
    @Redirect(method = "renderBreastWithTransforms", require = 1, remap = false,
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 3, remap = true))
    private void fp$freezeDepthSink(PoseStack stack, float x, float y, float z) {
        ShapeData shape = fp$shape != null ? fp$shape : new ShapeData();
        float expectedZ = fp$zOff - 0.0625F * 2F + fp$breastOffsetZ * 0.0625F;
        if (Math.abs(z - expectedZ) > 1.0e-4F) {
            //Upstream changed the call site order; degrade to vanilla but keep the
            //body-space offsets, which do not depend on FGM's own z math
            stack.translate(x + shape.getOffsetX(), y + shape.getOffsetY(), z + shape.getOffsetZ());
            return;
        }
        float zOff = Math.max(fp$zOff, (1.0F - fp$VANILLA_BUST_CAP) * 0.0625F);
        //Offsets are applied in body space (before the tilt rotations and the later
        //scale call), so they are never magnified by the scale; front = -z, back =
        //+z (user-measured via the diagnostic panel), so NEGATIVE offsetZ protrudes
        //toward the chest front and positive offsetZ pulls back toward the torso
        stack.translate(x + shape.getOffsetX(), y + shape.getOffsetY(),
            zOff - 0.0625F * 2F + fp$breastOffsetZ * 0.0625F + shape.getOffsetZ());
    }

    //7th translate call: the hang shift that keeps growing with bust size
    //(-0.035 * breastSize); clamp the size term at its vanilla cap level
    @Redirect(method = "renderBreastWithTransforms", require = 1, remap = false,
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 6, remap = true))
    private void fp$freezeHangShift(PoseStack stack, float x, float y, float z) {
        if (x != 0.0F || z != 0.0F) {
            //Upstream changed the call site order; degrade to vanilla instead of corrupting another call
            stack.translate(x, y, z);
            return;
        }
        float paramCap = fp$VANILLA_BUST_CAP * 2F - fp$PARAM_FOLD;
        stack.translate(x, -0.035F * Math.min(fp$breastSize, paramCap), z);
    }

    /**
     * The back-flatten clamp ("削平背端"): replaces FGM's renderBox with a
     * vertex-clamped version. Every vertex whose body-space z lies beyond
     * fp$CLAMP_BACK_Z is pulled straight back onto the plane along the body-z
     * axis; everything in front of the plane is bit-identical to the original
     * render. UVs, normals, light and overlay pass through untouched, so the
     * flattened region simply reads as squashed against the back instead of
     * poking out of it. This is the user's "削平" design: the box keeps its
     * front fit while its back corner can never cross the torso back.
     *
     * <p>When the capture window carries a nonzero roundness, the flat quads are
     * replaced outright by {@link RoundBreastMesh}'s superellipsoid mesh (same
     * subdivision for every pass, so skin/jacket/armor morph together); the
     * back-flatten clamp then applies to the mesh vertices exactly as to box
     * vertices. Roundness 0 skips this branch entirely — the flat path below is
     * bit-identical to FGM's renderBox emission.</p>
     *
     * Body-space mapping uses the verified chain from fp$captureSize (same lift
     * the diagnostic panel renders). Only the z excursion is clamped — x/y stay
     * put, so the silhouette from the front does not change. All six renderBox
     * call sites (breast, jacket-wear, armor incl. overlay/trim/glint) are
     * flattened together so outer layers follow the body instead of poking
     * through it. Outside the capture window the original render runs.
     */
    @Inject(method = "renderBox", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void fp$flattenBack(WildfireModelRenderer.ModelBox model, PoseStack matrixStack, VertexConsumer bufferIn, int packedLightIn, int packedOverlayIn,
        float red, float green, float blue, float alpha, CallbackInfo ci) {
        Matrix4f bodyView = fp$bodyView;
        Matrix4f bodyInv = fp$bodyViewInv;
        if (bodyView == null || bodyInv == null) {
            return; //outside the capture window or degenerate pose -> original render
        }
        Matrix4f pose = matrixStack.last().pose();
        Matrix3f normalMat = matrixStack.last().normal();
        //Outer layers (jacket-wear, armor) clamp a hair OUTSIDE the body plane so
        //the back faces of layers drawn later win the depth compare cleanly instead
        //of z-fighting with the breast body when everything is flattened onto one
        //plane (extreme scaleZ). Draw order is body -> wear -> armor, so layer
        //index 0 keeps the base plane and later layers step outward.
        float planeZ = fp$boxIndex++ == 0 ? fp$CLAMP_BACK_Z : fp$CLAMP_BACK_Z + 0.003F;
        //Constant per call: the view-space direction of the body-z axis (the pull
        //direction). Using the un-normalized linear part keeps everything affine
        //and scale-consistent: p' = p + (0,0,Δ) maps back to q' = q + R·(0,0,Δ)
        Vector3f pullDir = bodyView.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
        if (fp$roundness > 0.0F) {
            //Shape Studio roundness: emit the superellipsoid mesh instead of the flat
            //box quads. Same back-flatten clamp applies afterwards — from here on a
            //mesh vertex is just another local-space point like a box vertex
            RoundBreastMesh mesh = RoundBreastMesh.of(model, fp$roundness, fp$cleavage);
            float[] d = mesh.data;
            for (int quad = 0; quad < mesh.quadCount; quad++) {
                for (int v = 0; v < 4; v++) {
                    int o = quad * 32 + v * 8;
                    Vector3f view = pose.transformPosition(new Vector3f(d[o], d[o + 1], d[o + 2]));
                    Vector3f body = bodyInv.transformPosition(new Vector3f(view));
                    if (body.z > planeZ) {
                        float depth = body.z - planeZ;
                        view.x -= pullDir.x * depth;
                        view.y -= pullDir.y * depth;
                        view.z -= pullDir.z * depth;
                    }
                    Vector3f normal = new Vector3f(d[o + 3], d[o + 4], d[o + 5]).mul(normalMat);
                    bufferIn.vertex(view.x, view.y, view.z);
                    bufferIn.color(red, green, blue, alpha);
                    bufferIn.uv(d[o + 6], d[o + 7]);
                    bufferIn.overlayCoords(packedOverlayIn);
                    bufferIn.uv2(packedLightIn);
                    bufferIn.normal(normal.x(), normal.y(), normal.z());
                    bufferIn.endVertex();
                }
            }
            ci.cancel();
            return;
        }
        for (WildfireModelRenderer.TexturedQuad quad : model.quads) {
            Vector3f normal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
            normal.mul(normalMat);
            WildfireModelRenderer.PositionTextureVertex[] verts = quad.vertexPositions;
            for (WildfireModelRenderer.PositionTextureVertex vertex : verts) {
                float lx = vertex.x() / 16.0F;
                float ly = vertex.y() / 16.0F;
                float lz = vertex.z() / 16.0F;
                Vector3f view = pose.transformPosition(new Vector3f(lx, ly, lz));
                Vector3f body = bodyInv.transformPosition(new Vector3f(view));
                if (body.z > planeZ) {
                    float d = body.z - planeZ;
                    view.x -= pullDir.x * d;
                    view.y -= pullDir.y * d;
                    view.z -= pullDir.z * d;
                }
                bufferIn.vertex(view.x, view.y, view.z);
                bufferIn.color(red, green, blue, alpha);
                bufferIn.uv(vertex.texturePositionX(), vertex.texturePositionY());
                bufferIn.overlayCoords(packedOverlayIn);
                bufferIn.uv2(packedLightIn);
                bufferIn.normal(normal.x(), normal.y(), normal.z());
                bufferIn.endVertex();
            }
        }
        ci.cancel();
    }

    /**
     * Diagnostic aid: draws the torso-back plane exactly as FGM Plus computes it
     * (the same chain fp$flattenBack clamps against), so its in-game fit can be
     * re-verified any time via the Shape Studio toggle. Vertices are submitted
     * in body-space local coordinates through the lifted body view — the same
     * structure FGM itself uses (vertex(pose, local)) — so there is no
     * double- or missing-transform ambiguity.
     *
     * Runs at TAIL, i.e. after FGM's popPose with no early returns upstream,
     * so the buffer source is live and the captured entry pose matches the
     * stack state the body transform was applied on top of.
     */
    @Inject(method = "renderBreastWithTransforms", at = @At("TAIL"), require = 1, remap = false)
    private void fp$drawDebugPlane(AbstractClientPlayer entity, ModelPart body, ItemStack armorStack, PoseStack matrixStack, MultiBufferSource bufferSource,
        RenderType breastRenderType, int packedLightIn, int combineTex, float alpha, boolean bounceEnabled, float totalX, float total, float bounceRotation,
        float breastSize, float breastOffsetX, float breastOffsetY, float breastOffsetZ, float zOff, float outwardAngle, boolean uniboob, boolean isChestplateOccupied,
        boolean breathingAnimation, boolean left, CallbackInfo ci) {
        //Consume the capture window: a frame without a capture must not draw a stale
        //plane, and boxes after this point run the vanilla path
        Matrix4f bodyView = fp$bodyView;
        fp$bodyView = null;
        fp$bodyViewInv = null;
        fp$roundness = 0.0F;
        fp$cleavage = true;
        //FGM calls renderBreastWithTransforms twice per player (left + right) with
        //the same body and stack; drawing once is enough
        if (!left || !ShapeRenderState.debugPlane || bodyView == null) {
            return;
        }
        //Torso BACK plane: front = -z and back = +z (established empirically — the
        //first panel version at z = -2.25 px rendered on the chest FRONT per user
        //measurement, and the 1.3.0 clip keeping z > -2.25 px cut away the front).
        //The panel sits 0.25 px outside the torso back (z = +2.25 px) so it stays
        //visible beside the skin instead of z-fighting with it; full torso
        //silhouette x = +-4 px, y = 0..12 px in body space
        float backZ = 2.25F * 0.0625F;
        float xHalf = 4.0F * 0.0625F;
        float yTop = 0.0F;
        float yBottom = 12.0F * 0.0625F;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.debugQuads());
        //Both windings: debugQuads' cull state is not contractual, the panel
        //must be visible no matter which side the camera is on
        fp$debugQuad(buffer, bodyView, -xHalf, yTop, backZ, xHalf, yBottom);
        fp$debugQuad(buffer, bodyView, xHalf, yTop, backZ, -xHalf, yBottom);
    }

    @Unique
    private static void fp$debugQuad(VertexConsumer buffer, Matrix4f bodyView, float x0, float y0, float z, float x1, float y1) {
        fp$debugVertex(buffer, bodyView, x0, y0, z);
        fp$debugVertex(buffer, bodyView, x1, y0, z);
        fp$debugVertex(buffer, bodyView, x1, y1, z);
        fp$debugVertex(buffer, bodyView, x0, y1, z);
    }

    @Unique
    private static void fp$debugVertex(VertexConsumer buffer, Matrix4f bodyView, float x, float y, float z) {
        buffer.vertex(bodyView, x, y, z);
        buffer.color(0.2F, 1.0F, 0.4F, 0.35F);
        buffer.endVertex();
    }

}
