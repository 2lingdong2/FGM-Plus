package io.github.e33epus.fgmplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.render.GenderLayer;
import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.FgmPlusMod;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.github.e33epus.fgmplus.shape.ShapeRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
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
 * <p>On top of that it layers the per-player ShapeData (three-axis scale + perkiness
 * + position offsets + roundness). Back overflow is cured by fp$flattenBack: every
 * vertex whose body-space z crosses the torso-back plane is pulled back onto it
 * along the body-z axis, so the back corner can never poke through while the front
 * fit stays exactly where the user put it.</p>
 *
 * <p>Call-site facts verified against the FGM 3.2.2 jar (mojmap-named, NeoForge
 * production runtime uses the same namespace, so no refmap is needed):
 * renderBreastWithTransforms has 24 params (the trailing boolean selects the
 * jacket-wear box set); the PoseStack.scale(0.9995,1,1) fingerprint call sits
 * right before the renderBreast call and the baby-scale call upstream is filtered
 * by the fingerprint guard; the depth-sink translate is FFF-ordinal 3 and the
 * hang-shift translate is FFF-ordinal 6, exactly as in FGM 3.1 (the baby block's
 * bodyYOffset translate is (DDD)V and does not enter the ordinal count); all
 * renderBox call sites (body, jacket-wear, armor layers, trim, glint) live inside
 * renderBreast, which renderBreastWithTransforms invokes inside this capture
 * window.</p>
 */
@Mixin(GenderLayer.class)
public abstract class GenderLayerMixin {

    // Vanilla FloatConfigKey BUST_SIZE upper bound
    @Unique private static final float fp$VANILLA_BUST_CAP = 0.8F;
    // GenderLayer#render passes breastSize = bSize + |bSize - 0.7| (verified identical
    // in the 3.2.2 bytecode), which folds to a constant 0.7 for bSize <= 0.7 and is
    // invertible above that
    @Unique private static final float fp$PARAM_FOLD = 0.7F;
    // Replaces the retired bustScaleGain/bustScaleMax config keys (same values as their defaults)
    @Unique private static final float fp$SCALE_GAIN = 1.0F;
    @Unique private static final float fp$SCALE_MAX = 3.0F;
    // Torso BACK clamp plane, in body space. Front = -z and back = +z (established
    // empirically on the 3.1 render lineage and re-derived from the 3.2.2 bytecode:
    // the depth-sink translate pushes +z with growing bust). The plane sits 0.125 px
    // outside the vanilla torso back (+2 px) so flattened geometry neither z-fights
    // the skin nor the jacket skin layer at +2.25 px. Vertices with a body z beyond
    // it are pulled back onto the plane along the body-z axis ("flattened"), never
    // clipped: front-side geometry is untouched, so the chest stays fitted to the
    // front exactly as the user required.
    @Unique private static final float fp$CLAMP_BACK_Z = 2.125F * 0.0625F;
    // FGM's droop: -35 deg times totalRotation (verified: rotationXYZ(-35 * totalRot)
    // right before the scale call)
    @Unique private static final float fp$DROOP_DEG = 35.0F;

    @Unique private float fp$breastSize;
    @Unique private float fp$breastOffsetZ;
    @Unique private float fp$zOff;
    @Unique private ShapeData fp$shape;

    //Body view (entry pose + baby/body-part transform) and its inverse, both built
    //once per breast per frame in fp$captureSize; fp$flattenBack clamps against the
    //inverse, fp$drawDebugPlane lifts the panel through the forward one. The window
    //lives until the next capture replaces it (only the drawn debug plane drops it
    //early); null = outside the window -> vanilla path. Static because renderBox
    //is static.
    @Unique private static Matrix4f fp$bodyView;
    @Unique private static Matrix4f fp$bodyViewInv;
    //Roundness of the shape captured for the current window (renderBox is static,
    //so the instance shape field is mirrored here)
    @Unique private static float fp$roundness;
    //0-based renderBox call index inside the current capture window (body, then
    //jacket-wear, then armor passes); reset per breast in fp$captureSize
    @Unique private static int fp$boxIndex;
    //Field verdict counters for the live debugging ritual (PLAN P1-6): hits count
    //renderBox calls that actually clamped inside a live window, fallbacks count
    //the silent vanilla-path escapes. Flushed at most once per second at debug level.
    @Unique private static int fp$flattenHits;
    @Unique private static int fp$flattenFallbacks;
    @Unique private static long fp$nextFlattenLogMs;

    @Unique private static float fp$realScale(float param) {
        float bSize = param > fp$PARAM_FOLD ? (param + fp$PARAM_FOLD) * 0.5F : param;
        if (bSize <= fp$VANILLA_BUST_CAP) {
            return 1.0F;
        }
        return Math.min(1.0F + (bSize - fp$VANILLA_BUST_CAP) * fp$SCALE_GAIN, fp$SCALE_MAX);
    }

    //Rate-limited (1/s) debug verdict for the live ritual: hits > 0 while the mesh
    //is visible = the clamp window is alive; hits == 0 = hunt the capture path
    @Unique
    private static void fp$logFlattenVerdict() {
        long now = System.currentTimeMillis();
        if (now < fp$nextFlattenLogMs) {
            return;
        }
        fp$nextFlattenLogMs = now + 1000L;
        FgmPlusMod.LOGGER.debug("FGM Plus flatten verdict (1s): hits={}, fallbacks={}", fp$flattenHits, fp$flattenFallbacks);
        fp$flattenHits = 0;
        fp$flattenFallbacks = 0;
    }

    //Parameter list mirrors the 3.2.2 descriptor exactly:
    //(LivingEntity, HumanoidModel, ItemStack, PoseStack, MultiBufferSource, RenderType,
    // int light, int overlay, float alpha, boolean bounceEnabled,
    // float totalX, float totalY, float bounceRotation, float breastSize,
    // float breastOffsetX, float breastOffsetY, float breastOffsetZ, float zOff,
    // float outwardAngle, boolean uniboob, boolean airGate, boolean chestplateOccupied,
    // boolean left, boolean jacketWear)
    @Inject(method = "renderBreastWithTransforms", at = @At("HEAD"), require = 1, remap = false)
    private void fp$captureSize(LivingEntity entity, HumanoidModel<?> model, ItemStack armorStack, PoseStack matrixStack, MultiBufferSource bufferSource,
        RenderType breastRenderType, int packedLightIn, int packedOverlayIn, float alpha, boolean bounceEnabled, float totalX, float totalY,
        float bounceRotation, float breastSize, float breastOffsetX, float breastOffsetY, float breastOffsetZ, float zOff, float outwardAngle,
        boolean uniboob, boolean airGate, boolean chestplateOccupied, boolean left, boolean jacketWear, CallbackInfo ci) {
        fp$breastSize = breastSize;
        fp$breastOffsetZ = breastOffsetZ;
        fp$zOff = zOff;
        //Same lookup GenderLayer#render does; the shape is only read when a breast is actually rendered
        fp$shape = null;
        fp$roundness = 0.0F;
        PlayerConfig plr = WildfireGender.getPlayerById(entity.getUUID());
        if (plr != null) {
            ShapeData shape = ((ShapeHolder) plr).fgmplus$getShape();
            fp$shape = shape;
            fp$roundness = shape.getRoundness();
        }
        //Body view = entry pose (the stack is untouched at HEAD) + the transforms FGM
        //applies right after (verified in the 3.2.2 bytecode: optional baby
        //scale(babyBodyScale) + translate(0, bodyYOffset/16, 0), then
        //translate(body.xyz * 0.0625) then guarded zRot/yRot/xRot mulPose). Two
        //consumers inside the capture window: fp$flattenBack (the back-flatten
        //clamp, always on) clamps against the inverse, and fp$drawDebugPlane lifts
        //the torso-back plane through the forward one.
        //The PlayerConfig lookup above is load-bearing for the redirects and stays
        //unconditional; the capture itself must be unconditional too (the clamp
        //depends on it regardless of the diagnostic toggle)
        fp$boxIndex = 0;
        fp$bodyView = null;
        fp$bodyViewInv = null;
        try {
            Matrix4f view = new Matrix4f(matrixStack.last().pose());
            if (entity.isBaby()) {
                //babyBodyScale/bodyYOffset are private final on AgeableListModel in
                //1.21.1 (FGM reads them through its own accesstransformer.cfg) —
                //this accessor keeps the access mixin-local.
                //FGM's forward chain is scale(babyBodyScale) then the bodyYOffset
                //translate (3.2.2 bytecode offsets 28/44; baseline 1.21.11 does the
                //same) — the old 1/scale inversion mismatched it
                AgeableListModelAccessor modelAccess = (AgeableListModelAccessor) model;
                float s = modelAccess.fgmplus$babyBodyScale();
                view.scale(s, s, s);
                view.translate(0.0F, modelAccess.fgmplus$bodyYOffset() / 16.0F, 0.0F);
            }
            ModelPart body = model.body;
            view.translate(body.x * 0.0625F, body.y * 0.0625F, body.z * 0.0625F);
            view.rotateZ(body.zRot);
            view.rotateY(body.yRot);
            view.rotateX(body.xRot);
            Matrix4f inv = view.invert(new Matrix4f());
            fp$bodyView = view;
            fp$bodyViewInv = inv;
        } catch (Exception e) {
            //degenerate pose: clamp and panel fall back to vanilla rendering —
            //never silent: this fallback is the P1 suspect behind "pokes out a bit"
            fp$flattenFallbacks++;
            fp$logFlattenVerdict();
            FgmPlusMod.LOGGER.debug("FGM Plus: capture window lost (degenerate pose), vanilla fallback", e);
        }
    }

    @Redirect(method = "renderBreastWithTransforms", require = 1, remap = false,
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", remap = true))
    private void fp$applyRealScale(PoseStack stack, float x, float y, float z) {
        //Fingerprint guard: FGM's z-fighting fix is scale(0.9995,1,1) at this point;
        //any other signature (e.g. the baby scale upstream) means a different call
        //site - stand down
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
        //Back overflow is handled per-vertex by fp$flattenBack, which leaves the front
        //side untouched. Each breast box already spans the full half-torso width, so
        //the real-scale growth (s) never applies to the X axis by default; the
        //per-player scaleX slider is the only way to widen (1.0 = vanilla width)
        stack.scale(x * shape.getScaleX(), y * s * shape.getScaleY(), z * s * shape.getScaleZ());
    }

    //FFF-ordinal 3: the positional sink that deepens with bust size
    //(zOff - 0.125 + breastOffsetZ * 0.0625, verified in the 3.2.2 bytecode);
    //freeze it at the vanilla cap level
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
        //toward the chest front and positive offsetZ pulls back toward the torso.
        //The depth recompute deliberately uses FGM 5's per-unit coefficient 0.0425
        //instead of 3.2.x's 0.0625: an intentional deviation from the local upstream
        //so the rendered body matches the 1.21.11 baseline at equal slider values
        //(expectedZ above must keep 0.0625 — it fingerprints FGM's actual argument).
        stack.translate(x + shape.getOffsetX(), y + shape.getOffsetY(),
            zOff - 0.0625F * 2F + fp$breastOffsetZ * 0.0425F + shape.getOffsetZ());
    }

    //FFF-ordinal 6: the hang shift that keeps growing with bust size
    //(-0.035 * breastSize, verified in the 3.2.2 bytecode); clamp the size term
    //at its vanilla cap level
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
     * The back-flatten clamp + Shape Studio roundness emitter. Replaces FGM's
     * renderBox with a vertex-processed version:
     *  - roundness 0: every vertex whose body-space z lies beyond fp$CLAMP_BACK_Z is
     *    pulled straight back onto the plane along the body-z axis; everything in
     *    front of the plane is bit-identical to the original render.
     *  - roundness > 0: the superellipsoid mesh ({@link RoundBreastMesh}) is emitted
     *    instead of the flat box quads, then the same clamp applies — from the
     *    clamp's point of view a mesh vertex is just another local-space point.
     * UVs, color, light and overlay pass through untouched. All renderBox call
     * sites (breast, jacket-wear, armor incl. trim/glint) are processed together so
     * outer layers follow the body instead of poking through it. Outside the
     * capture window the original render runs.
     *
     * <p>3.2.2 signature: (ModelBox, PoseStack, VertexConsumer, int light,
     * int overlay, int colorARGB) — color is a packed int in this generation.</p>
     */
    @Inject(method = "renderBox", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void fp$flattenBack(WildfireModelRenderer.ModelBox model, PoseStack matrixStack, VertexConsumer bufferIn, int packedLightIn, int packedOverlayIn,
        int color, CallbackInfo ci) {
        Matrix4f bodyView = fp$bodyView;
        Matrix4f bodyInv = fp$bodyViewInv;
        if (bodyView == null || bodyInv == null) {
            //outside the capture window or degenerate pose -> original render
            fp$flattenFallbacks++;
            fp$logFlattenVerdict();
            return;
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
        float roundness = fp$roundness;
        fp$flattenHits++;
        fp$logFlattenVerdict();
        if (roundness > 0.0F) {
            //Shape Studio roundness: emit the superellipsoid mesh instead of the flat
            //box quads, then clamp exactly like the flat path
            RoundBreastMesh mesh = RoundBreastMesh.of(model, roundness);
            float[] d = mesh.data;
            for (int quad = 0; quad < mesh.quadCount; quad++) {
                for (int v = 0; v < 4; v++) {
                    int o = quad * 32 + v * 8;
                    Vector4f viewPos = new Vector4f(d[o], d[o + 1], d[o + 2], 1.0F).mul(pose);
                    Vector3f body = bodyInv.transformPosition(new Vector3f(viewPos.x(), viewPos.y(), viewPos.z()));
                    if (body.z > planeZ) {
                        float depth = body.z - planeZ;
                        viewPos.x -= pullDir.x * depth;
                        viewPos.y -= pullDir.y * depth;
                        viewPos.z -= pullDir.z * depth;
                    }
                    Vector3f normal = new Vector3f(d[o + 3], d[o + 4], d[o + 5]).mul(normalMat);
                    bufferIn.addVertex(viewPos.x(), viewPos.y(), viewPos.z());
                    bufferIn.setColor(color);
                    bufferIn.setUv(d[o + 6], d[o + 7]);
                    bufferIn.setOverlay(packedOverlayIn);
                    bufferIn.setLight(packedLightIn);
                    bufferIn.setNormal(normal.x(), normal.y(), normal.z());
                }
            }
            ci.cancel();
            return;
        }
        for (WildfireModelRenderer.TexturedQuad quad : model.quads) {
            if (quad == null) {
                continue;
            }
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
                    float dpt = body.z - planeZ;
                    view.x -= pullDir.x * dpt;
                    view.y -= pullDir.y * dpt;
                    view.z -= pullDir.z * dpt;
                }
                bufferIn.addVertex(view.x, view.y, view.z);
                bufferIn.setColor(color);
                bufferIn.setUv(vertex.texturePositionX(), vertex.texturePositionY());
                bufferIn.setOverlay(packedOverlayIn);
                bufferIn.setLight(packedLightIn);
                bufferIn.setNormal(normal.x(), normal.y(), normal.z());
            }
        }
        ci.cancel();
    }

    /**
     * Diagnostic aid: draws the torso-back plane exactly as FGM Plus computes it
     * (the same chain fp$flattenBack clamps against), so its in-game fit can be
     * re-verified any time via the Shape Studio toggle. Vertices are submitted
     * in body-space local coordinates through the lifted body view.
     *
     * <p>Window lifecycle (baseline-aligned): the capture window is NOT consumed
     * here unconditionally any more — the old always-clear at TAIL stranded any
     * renderBox that arrived after a capture/timing hiccup on the vanilla path
     * (no roundness, no back clamp). The window now lives until the next
     * captureSize recapture replaces it, and is only dropped once the debug
     * plane has actually drawn (same semantics as the 1.21.11 RenderCapture
     * windows, which are only replaced by beginWindow).</p>
     */
    @Inject(method = "renderBreastWithTransforms", at = @At("TAIL"), require = 1, remap = false)
    private void fp$drawDebugPlane(LivingEntity entity, HumanoidModel<?> model, ItemStack armorStack, PoseStack matrixStack, MultiBufferSource bufferSource,
        RenderType breastRenderType, int packedLightIn, int packedOverlayIn, float alpha, boolean bounceEnabled, float totalX, float totalY,
        float bounceRotation, float breastSize, float breastOffsetX, float breastOffsetY, float breastOffsetZ, float zOff, float outwardAngle,
        boolean uniboob, boolean airGate, boolean chestplateOccupied, boolean left, boolean jacketWear, CallbackInfo ci) {
        //FGM calls renderBreastWithTransforms twice per player (left + right) with
        //the same body and stack; drawing once is enough
        if (!left || !ShapeRenderState.debugPlane) {
            return;
        }
        Matrix4f bodyView = fp$bodyView;
        if (bodyView == null) {
            return;
        }
        //Torso BACK plane: front = -z and back = +z (established empirically — the
        //panel sits 0.25 px outside the torso back so it stays visible beside the
        //skin instead of z-fighting with it; full torso silhouette x = +-4 px,
        //y = 0..12 px in body space)
        float backZ = 2.25F * 0.0625F;
        float xHalf = 4.0F * 0.0625F;
        float yTop = 0.0F;
        float yBottom = 12.0F * 0.0625F;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.debugQuads());
        //Both windings: debugQuads' cull state is not contractual, the panel
        //must be visible no matter which side the camera is on
        fp$debugQuad(buffer, bodyView, -xHalf, yTop, backZ, xHalf, yBottom);
        fp$debugQuad(buffer, bodyView, xHalf, yTop, backZ, -xHalf, yBottom);
        //Drop the window only now that the plane actually drew (a frame without a
        //capture must not draw a stale plane); until the next recapture the window
        //stays live so late renderBox calls still clamp instead of degrading
        fp$bodyView = null;
        fp$bodyViewInv = null;
        fp$roundness = 0.0F;
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
        buffer.addVertex(bodyView, x, y, z);
        buffer.setColor(0.2F, 1.0F, 0.4F, 0.35F);
    }

}
