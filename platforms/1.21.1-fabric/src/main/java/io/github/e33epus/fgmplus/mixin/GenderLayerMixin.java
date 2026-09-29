package io.github.e33epus.fgmplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.render.BreastSide;
import com.wildfire.render.GenderArmorLayer;
import com.wildfire.render.GenderLayer;
import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.github.e33epus.fgmplus.shape.ShapeRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bust sizes above the vanilla slider cap (0.8) never grow the breast geometry:
 * the model box is 4x5x3..5 and the tilt rotation is clamped, so everything past
 * the cap is faked with positional shifts. This mixin freezes those positional
 * terms at the vanilla cap level and applies a real scale to the breast model
 * instead, pivoting on the box corner that touches the chest.
 *
 * <p>On top of that it layers the per-player ShapeData (three-axis scale + perkiness
 * + position offsets + roundness). Back overflow is cured by fp$flattenBack: every
 * vertex whose body-space z crosses the torso-back plane is pulled back onto it
 * along the body-z axis, so the back corner can never poke through while the front
 * fit stays exactly where the user put it.</p>
 *
 * <p>Call-site facts verified against the loom-remapped (mojmap) FGM 3.2.1+1.21 jar:
 * 3.2.1 split 3.1's monolithic renderBreastWithTransforms into
 * render() -> renderSides() -> [setupTransformations() + renderBreast()] per side,
 * with armor moved out to GenderArmorLayer (which EXTENDS GenderLayer and calls the
 * same protected static renderBox through its own consumer, so the single renderBox
 * hook still covers body, jacket-wear and armor boxes). Inside setupTransformations
 * the PoseStack.translate(FFF) sites sit at ordinals 0..8: 0 = baby bodyYOffset,
 * 1 = body*0.0625, 2/3 = physics X/Y, 4 = the depth sink
 * (zOffset - 0.125 + breastOffsetZ*0.0625), 5 = uniboob, 6 = bounce x shift,
 * 7 = the hang shift (0, -0.035*breastSize, 0), 8 = chestplate breathing; the
 * method ends with the unconditional scale(0.9995,1,1) z-fighting fix, which is
 * the fingerprint this mixin redirects. setupRender still folds
 * breastSize = bSize + |bSize - 0.7| and zOffset = 0.0625*(1-bSize) exactly like
 * 3.1, so the fold inversion in fp$realScale ports unchanged.</p>
 */
@Mixin(GenderLayer.class)
public abstract class GenderLayerMixin {

    // Vanilla FloatConfigKey BUST_SIZE upper bound (Configuration <clinit>: max 0.8)
    @Unique private static final float fp$VANILLA_BUST_CAP = 0.8F;
    // setupRender passes breastSize = bSize + |bSize - 0.7| (verified identical in the
    // 3.2.1 bytecode), which folds to a constant 0.7 for bSize <= 0.7 and is invertible
    // above that
    @Unique private static final float fp$PARAM_FOLD = 0.7F;
    // Replaces the retired bustScaleGain/bustScaleMax config keys (same values as their defaults)
    @Unique private static final float fp$SCALE_GAIN = 1.0F;
    @Unique private static final float fp$SCALE_MAX = 3.0F;
    // Torso BACK clamp plane, in body space. Front = -z and back = +z (established
    // empirically on the 3.1 render lineage and re-derived from the 3.2.1 bytecode:
    // the depth-sink translate pushes +z with growing bust). The plane sits 0.125 px
    // outside the vanilla torso back (+2 px) so flattened geometry neither z-fights
    // the skin nor the jacket skin layer at +2.25 px. Vertices with a body z beyond
    // it are pulled back onto the plane along the body-z axis ("flattened"), never
    // clipped: front-side geometry is untouched, so the chest stays fitted to the
    // front exactly as the user required.
    @Unique private static final float fp$CLAMP_BACK_Z = 2.125F * 0.0625F;
    // FGM's droop: -35 deg times totalRotation (verified: rotationXYZ(-35 * totalRot)
    // right before the trailing scale(0.9995) call)
    @Unique private static final float fp$DROOP_DEG = 35.0F;

    // Fields the redirects fingerprint against (all verified protected on GenderLayer)
    @Shadow(remap = false) protected float breastSize;
    @Shadow(remap = false) protected float zOffset;
    @Shadow(remap = false) protected float breastOffsetZ;

    @Unique private ShapeData fp$shape;

    //Body view (entry pose + baby/body-part transform) and its inverse, both built
    //once per side per pass in fp$capture; fp$flattenBack clamps against the inverse,
    //fp$drawDebugPlane lifts the panel through the forward one. Static because
    //renderBox is static; the instance capture mirrors them into the statics.
    @Unique private static Matrix4f fp$bodyView;
    @Unique private static Matrix4f fp$bodyViewInv;
    //Roundness of the shape captured for the current window (renderBox is static,
    //so the instance shape field is mirrored here)
    @Unique private static volatile float fp$roundness;
    //True while GenderArmorLayer's renderSides pass is running (this instanceof
    //GenderArmorLayer at capture time): armor boxes clamp a hair OUTSIDE the body plane
    @Unique private static boolean fp$armorPass;
    //0-based renderBox call index inside the current capture window (body, then
    //jacket-wear; the armor window restarts per side); reset per side in fp$capture
    @Unique private static int fp$boxIndex;

    @Unique private static float fp$realScale(float param) {
        float bSize = param > fp$PARAM_FOLD ? (param + fp$PARAM_FOLD) * 0.5F : param;
        if (bSize <= fp$VANILLA_BUST_CAP) {
            return 1.0F;
        }
        return Math.min(1.0F + (bSize - fp$VANILLA_BUST_CAP) * fp$SCALE_GAIN, fp$SCALE_MAX);
    }

    /**
     * Capture at setupTransformations HEAD: the stack is untouched here (renderSides
     * pushes and immediately calls this), and the body-part transform FGM applies
     * right after is exactly: optional baby scale(1/babyBodyScale) +
     * translate(0, bodyYOffset/16, 0) (AgeableListModel private finals, read through
     * the accessor — FGM ships its own access widener for them), then
     * translate(body.xyz * 0.0625) then guarded zRot/yRot/xRot mulPose. The capture
     * must be unconditional (the clamp depends on it regardless of any toggle) and
     * runs for both the breast and the armor pass, so the armor boxes flatten against
     * the same body space.
     */
    @Inject(method = "setupTransformations", at = @At("HEAD"), require = 1, remap = false)
    private void fp$capture(LivingEntity entity, HumanoidModel<?> model, PoseStack matrixStack, BreastSide side, CallbackInfo ci) {
        fp$shape = null;
        fp$roundness = 0.0F;
        PlayerConfig plr = WildfireGender.getPlayerById(entity.getUUID());
        if (plr != null) {
            ShapeData shape = ((ShapeHolder) plr).fgmplus$getShape();
            fp$shape = shape;
            fp$roundness = shape.getRoundness();
        }
        fp$armorPass = ((Object) this) instanceof GenderArmorLayer;
        fp$boxIndex = 0;
        fp$bodyView = null;
        fp$bodyViewInv = null;
        try {
            Matrix4f view = new Matrix4f(matrixStack.last().pose());
            if (entity.isBaby()) {
                AgeableListModelAccessor modelAccess = (AgeableListModelAccessor) model;
                float s = 1.0F / modelAccess.fgmplus$babyBodyScale();
                view.scale(s, s, s);
                view.translate(0.0F, modelAccess.fgmplus$bodyYOffset() / 16.0F, 0.0F);
            }
            var body = model.body;
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

    @Redirect(method = "setupTransformations", require = 1, remap = false,
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", remap = true))
    private void fp$applyRealScale(PoseStack stack, float x, float y, float z) {
        //Fingerprint guard: FGM's z-fighting fix is scale(0.9995,1,1) at the method tail;
        //any other signature (e.g. the baby scale at the method head) means a different
        //call site - stand down
        if (Math.abs(x - 0.9995F) > 1.0e-4F || y != 1.0F || z != 1.0F) {
            stack.scale(x, y, z);
            return;
        }
        ShapeData shape = fp$shape != null ? fp$shape : new ShapeData();
        float s = fp$realScale(breastSize);
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

    //FFF-ordinal 4: the positional sink that deepens with bust size
    //(zOffset - 0.125 + breastOffsetZ * 0.0625, verified in the 3.2.1 bytecode);
    //freeze it at the vanilla cap level
    @Redirect(method = "setupTransformations", require = 1, remap = false,
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 4, remap = true))
    private void fp$freezeDepthSink(PoseStack stack, float x, float y, float z) {
        ShapeData shape = fp$shape != null ? fp$shape : new ShapeData();
        float expectedZ = zOffset - 0.0625F * 2F + breastOffsetZ * 0.0625F;
        if (Math.abs(z - expectedZ) > 1.0e-4F) {
            //Upstream changed the call site order; degrade to vanilla but keep the
            //body-space offsets, which do not depend on FGM's own z math
            stack.translate(x + shape.getOffsetX(), y + shape.getOffsetY(), z + shape.getOffsetZ());
            return;
        }
        float zOff = Math.max(zOffset, (1.0F - fp$VANILLA_BUST_CAP) * 0.0625F);
        //Offsets are applied in body space (before the tilt rotations and the later
        //scale call), so they are never magnified by the scale; front = -z, back =
        //+z (user-measured via the diagnostic panel), so NEGATIVE offsetZ protrudes
        //toward the chest front and positive offsetZ pulls back toward the torso
        stack.translate(x + shape.getOffsetX(), y + shape.getOffsetY(),
            zOff - 0.0625F * 2F + breastOffsetZ * 0.0625F + shape.getOffsetZ());
    }

    //FFF-ordinal 7: the hang shift that keeps growing with bust size
    //(-0.035 * breastSize, verified in the 3.2.1 bytecode); clamp the size term
    //at its vanilla cap level
    @Redirect(method = "setupTransformations", require = 1, remap = false,
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 7, remap = true))
    private void fp$freezeHangShift(PoseStack stack, float x, float y, float z) {
        if (x != 0.0F || z != 0.0F) {
            //Upstream changed the call site order; degrade to vanilla instead of corrupting another call
            stack.translate(x, y, z);
            return;
        }
        float paramCap = fp$VANILLA_BUST_CAP * 2F - fp$PARAM_FOLD;
        stack.translate(x, -0.035F * Math.min(breastSize, paramCap), z);
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
     * UVs, color, light and overlay pass through untouched. All renderBox call sites
     * (breast body, jacket-wear, and GenderArmorLayer's armor boxes incl. trim/glint,
     * which reuse this same static method) are processed together so outer layers
     * follow the body instead of poking through it. Outside the capture window the
     * original render runs.
     *
     * <p>3.2.1 signature: (ModelBox, PoseStack, VertexConsumer, int light,
     * int overlay, int colorARGB) — color is a packed int; emission mirrors
     * upstream's composite {@code addVertex(x, y, z, color, u, v, overlay, light,
     * nx, ny, nz)} call.</p>
     */
    @Inject(method = "renderBox", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void fp$flattenBack(WildfireModelRenderer.ModelBox model, PoseStack matrixStack, VertexConsumer bufferIn, int packedLightIn, int packedOverlayIn,
        int color, CallbackInfo ci) {
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
        //plane (extreme scaleZ). Draw order is body -> wear (-> armor in its own
        //window), so index 0 of the breast window keeps the base plane and every
        //later box steps outward.
        float planeZ = (!fp$armorPass && fp$boxIndex++ == 0) ? fp$CLAMP_BACK_Z : fp$CLAMP_BACK_Z + 0.003F;
        //Constant per call: the view-space direction of the body-z axis (the pull
        //direction). Using the un-normalized linear part keeps everything affine
        //and scale-consistent: p' = p + (0,0,Δ) maps back to q' = q + R·(0,0,Δ)
        Vector3f pullDir = bodyView.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
        float roundness = fp$roundness;
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
                    bufferIn.addVertex(viewPos.x(), viewPos.y(), viewPos.z(), color, d[o + 6], d[o + 7],
                        packedOverlayIn, packedLightIn, normal.x(), normal.y(), normal.z());
                }
            }
            ci.cancel();
            return;
        }
        for (WildfireModelRenderer.TexturedQuad quad : model.quads) {
            if (quad == null) {
                continue;
            }
            Vector3f normal = new Vector3f(quad.normal.x, quad.normal.y, quad.normal.z).mul(normalMat);
            float nx = normal.x, ny = normal.y, nz = normal.z;
            for (WildfireModelRenderer.PositionTextureVertex vertex : quad.vertexPositions) {
                float lx = vertex.x() / 16.0F;
                float ly = vertex.y() / 16.0F;
                float lz = vertex.z() / 16.0F;
                Vector4f viewPos = new Vector4f(lx, ly, lz, 1.0F).mul(pose);
                Vector3f body = bodyInv.transformPosition(new Vector3f(viewPos.x(), viewPos.y(), viewPos.z()));
                if (body.z > planeZ) {
                    float dpt = body.z - planeZ;
                    viewPos.x -= pullDir.x * dpt;
                    viewPos.y -= pullDir.y * dpt;
                    viewPos.z -= pullDir.z * dpt;
                }
                bufferIn.addVertex(viewPos.x(), viewPos.y(), viewPos.z(), color, vertex.u(), vertex.v(),
                    packedOverlayIn, packedLightIn, nx, ny, nz);
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
     * <p>Also closes the capture window: each side's renderBreast is bracketed by
     * its own setupTransformations capture, so clearing at TAIL keeps every renderBox
     * inside the matching window (the armor pass re-captures in its own
     * setupTransformations, so it is unaffected).</p>
     */
    @Inject(method = "renderBreast", at = @At("TAIL"), require = 1, remap = false)
    private void fp$drawDebugPlane(LivingEntity entity, PoseStack matrixStack, MultiBufferSource bufferSource,
        int packedLightIn, int packedOverlayIn, BreastSide side, CallbackInfo ci) {
        //Consume the capture window: a frame without a capture must not draw a stale
        //plane, and boxes after this point run the vanilla path
        Matrix4f bodyView = fp$bodyView;
        fp$bodyView = null;
        fp$bodyViewInv = null;
        fp$roundness = 0.0F;
        fp$armorPass = false;
        //FGM calls renderBreast once per side (left + right) with the same body and
        //stack; drawing once is enough
        if (!side.isLeft || !ShapeRenderState.debugPlane || bodyView == null) {
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
