package io.github.e33epus.fgmplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.render.BreastRenderCommand;
import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.render.RenderCapture;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.UnaryOperator;

/**
 * The custom vertex emitter for FGM 5's breast geometry — two features in the
 * one hook where every pass (skin, jacket-wear, armor, trim, glint) crosses:
 * the back-flatten clamp ported from 1.20.1, and the Shape Studio roundness
 * superellipsoid mesh ({@link RoundBreastMesh}). With roundness at 0 the
 * flatten path is bit-identical to the original render.
 *
 * <p>Every vertex whose body-space z lies beyond the torso-back plane is pulled
 * straight back onto it along the body-z axis; everything in front of the plane
 * keeps its transformed position. UVs, light, overlay and the trim consumer
 * operator pass through untouched.</p>
 *
 * <p>FGM 5 routes ALL breast geometry (skin, jacket-wear, armor, trim, glint)
 * through this one command, and every transform chain involved runs through the
 * common GenderLayer#setupTransformations body frame, so one hook here covers
 * all passes. The body view is captured per command at construction time (see
 * RenderCapture) because the submit/collect pipeline defers the actual vertex
 * work; commands constructed while no capture window is active — or a stale one,
 * in pathological third-party construction orders — render with whatever window
 * they captured, and the clamp simply never triggers for far-away geometry.</p>
 */
@Mixin(value = BreastRenderCommand.class, remap = false)
public abstract class BreastRenderCommandMixin {

	@Shadow @Final private WildfireModelRenderer.ModelBox model;
	@Shadow @Final private int light;
	@Shadow @Final private int overlay;
	@Shadow @Final private int color;
	@Shadow @Final @Nullable private UnaryOperator<VertexConsumer> consumerOperator;

	//outer layers (jacket-wear, armor) clamp a hair OUTSIDE the body plane so the
	//back faces of layers drawn later win the depth compare cleanly instead of
	//z-fighting with the breast body when everything is flattened onto one plane
	@Unique private static final float fp$CLAMP_BACK_Z = 2.125F * 0.0625F;
	@Unique private static final float fp$LAYER_EPSILON = 0.003F;

	@Inject(method = "<init>(Lcom/wildfire/render/WildfireModelRenderer$ModelBox;IIIILjava/util/function/UnaryOperator;)V",
			at = @At("TAIL"), require = 1, remap = false)
	private void fgmplus$captureWindow(WildfireModelRenderer.ModelBox model, int light, int overlay, int color,
			int outline, UnaryOperator<VertexConsumer> consumerOperator, CallbackInfo ci) {
		RenderCapture.registerCommand(this);
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
	private void fgmplus$flattenBack(PoseStack.Pose entry, VertexConsumer vertexConsumer, CallbackInfo ci) {
		RenderCapture.BodyWindow window = RenderCapture.forCommand(this);
		if(window == null || window.inv() == null) {
			return; //outside the capture window or degenerate pose -> original render
		}
		VertexConsumer buffer = this.consumerOperator != null
				? this.consumerOperator.apply(vertexConsumer) : vertexConsumer;
		Matrix4f pose = entry.pose();
		Matrix3f normalMat = entry.normal();
		float planeZ = window.layerIndex() == 0 ? fp$CLAMP_BACK_Z : fp$CLAMP_BACK_Z + fp$LAYER_EPSILON;
		//Constant per call: the view-space direction of the body-z axis (the pull
		//direction). Using the un-normalized linear part keeps everything affine
		//and scale-consistent: p' = p + (0,0,Δ) maps back to q' = q + R·(0,0,Δ)
		Vector3f pullDir = window.view().transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
		float roundness = window.roundness();
		if(roundness > 0.0F) {
			//Shape Studio roundness: emit the superellipsoid mesh instead of the flat
			//box quads. Same back-flatten clamp applies afterwards — from here on a
			//mesh vertex is just another local-space point like a box vertex.
			RoundBreastMesh mesh = RoundBreastMesh.of(this.model, roundness);
			float[] d = mesh.data;
			for(int quad = 0; quad < mesh.quadCount; quad++) {
				for(int v = 0; v < 4; v++) {
					int o = quad * 32 + v * 8;
					Vector4f viewPos = new Vector4f(d[o], d[o + 1], d[o + 2], 1.0F).mul(pose);
					Vector3f body = window.inv().transformPosition(new Vector3f(viewPos.x(), viewPos.y(), viewPos.z()));
					if(body.z > planeZ) {
						float depth = body.z - planeZ;
						viewPos.x -= pullDir.x * depth;
						viewPos.y -= pullDir.y * depth;
						viewPos.z -= pullDir.z * depth;
					}
					Vector3f normal = new Vector3f(d[o + 3], d[o + 4], d[o + 5]).mul(normalMat);
					buffer.addVertex(viewPos.x(), viewPos.y(), viewPos.z(), this.color,
							d[o + 6], d[o + 7], this.overlay, this.light,
							normal.x(), normal.y(), normal.z());
				}
			}
			ci.cancel();
			return;
		}
		for(WildfireModelRenderer.TexturedQuad quad : this.model.quads) {
			//UVLayout can leave null slots for disabled faces; the mesh branch filters
			//them, so the flat branch must too (upstream renderBox would NPE here)
			if(quad == null || (quad.uvs[0] == 0.0F && quad.uvs[1] == 0.0F && quad.uvs[2] == 0.0F && quad.uvs[3] == 0.0F)) {
				continue;
			}
			Vector3f normal = new Vector3f(quad.normal.x(), quad.normal.y(), quad.normal.z()).mul(normalMat);
			for(WildfireModelRenderer.PositionTextureVertex vertex : quad.vertexPositions) {
				Vector4f viewPos = new Vector4f(vertex.x() / 16.0F, vertex.y() / 16.0F, vertex.z() / 16.0F, 1.0F).mul(pose);
				Vector3f body = window.inv().transformPosition(new Vector3f(viewPos.x(), viewPos.y(), viewPos.z()));
				if(body.z > planeZ) {
					float d = body.z - planeZ;
					viewPos.x -= pullDir.x * d;
					viewPos.y -= pullDir.y * d;
					viewPos.z -= pullDir.z * d;
				}
				buffer.addVertex(viewPos.x(), viewPos.y(), viewPos.z(), this.color,
						vertex.u(), vertex.v(), this.overlay, this.light,
						normal.x(), normal.y(), normal.z());
			}
		}
		ci.cancel();
	}
}
