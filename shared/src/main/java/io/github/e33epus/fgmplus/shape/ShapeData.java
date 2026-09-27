package io.github.e33epus.fgmplus.shape;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;

/**
 * Per-player breast shape overrides, layered on top of FGM's own config.
 * Scale multipliers apply on top of the bust-size render scale; perkiness
 * (degrees) counteracts FGM's hardcoded -35 degree droop around the X axis;
 * offsets shift the whole model in body space. Body space has front = -z and
 * back = +z, so a stored positive offsetZ pulls the model toward the torso
 * back; the Shape Studio slider negates on both read and write so that
 * dragging right always protrudes. (The class doc on the 1.20.1 port wrongly
 * claimed positive = protrude; the render math below is authoritative.)
 * Wire format: 7 floats, 28 bytes, carried in the fgmplus:shape_sync payload.
 */
public final class ShapeData {

	public static final float MIN_SCALE = 0.1F, MAX_SCALE = 3.0F;
	public static final float MIN_PERK = -30.0F, MAX_PERK = 60.0F;
	public static final float MIN_OFFSET = -1.0F, MAX_OFFSET = 1.0F; //model units, 1.0 = 16 px

	private float scaleX = 1.0F;
	private float scaleY = 1.0F;
	private float scaleZ = 1.0F;
	private float perkiness = 0.0F;
	private float offsetX = 0.0F;
	private float offsetY = 0.0F;
	private float offsetZ = 0.0F;

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public float getScaleX() {
		return scaleX;
	}

	public void setScaleX(float value) {
		this.scaleX = clamp(value, MIN_SCALE, MAX_SCALE);
	}

	public float getScaleY() {
		return scaleY;
	}

	public void setScaleY(float value) {
		this.scaleY = clamp(value, MIN_SCALE, MAX_SCALE);
	}

	public float getScaleZ() {
		return scaleZ;
	}

	public void setScaleZ(float value) {
		this.scaleZ = clamp(value, MIN_SCALE, MAX_SCALE);
	}

	public float getPerkiness() {
		return perkiness;
	}

	public void setPerkiness(float value) {
		this.perkiness = clamp(value, MIN_PERK, MAX_PERK);
	}

	public float getOffsetX() {
		return offsetX;
	}

	public void setOffsetX(float value) {
		this.offsetX = clamp(value, MIN_OFFSET, MAX_OFFSET);
	}

	public float getOffsetY() {
		return offsetY;
	}

	public void setOffsetY(float value) {
		this.offsetY = clamp(value, MIN_OFFSET, MAX_OFFSET);
	}

	/** Positive = pulled toward the torso back, negative = protrudes toward the chest front. */
	public float getOffsetZ() {
		return offsetZ;
	}

	public void setOffsetZ(float value) {
		this.offsetZ = clamp(value, MIN_OFFSET, MAX_OFFSET);
	}

	/** True when every field is at its default; default shapes are never stored or worth syncing. */
	public boolean isDefault() {
		return scaleX == 1.0F && scaleY == 1.0F && scaleZ == 1.0F && perkiness == 0.0F
				&& offsetX == 0.0F && offsetY == 0.0F && offsetZ == 0.0F;
	}

	public void write(ByteBuf buffer) {
		buffer.writeFloat(scaleX);
		buffer.writeFloat(scaleY);
		buffer.writeFloat(scaleZ);
		buffer.writeFloat(perkiness);
		buffer.writeFloat(offsetX);
		buffer.writeFloat(offsetY);
		buffer.writeFloat(offsetZ);
	}

	public static ShapeData read(ByteBuf buffer) {
		ShapeData shape = new ShapeData();
		//NaN/Infinity slip through min/max clamping (Math.min/max propagate NaN), so each
		//component is checked against Float.isFinite before it may touch a setter; a
		//malformed component simply keeps its default
		shape.setScaleX(finite(buffer.readFloat(), 1.0F));
		shape.setScaleY(finite(buffer.readFloat(), 1.0F));
		shape.setScaleZ(finite(buffer.readFloat(), 1.0F));
		shape.setPerkiness(finite(buffer.readFloat(), 0.0F));
		if(buffer.readableBytes() >= 12) {
			shape.setOffsetX(finite(buffer.readFloat(), 0.0F));
			shape.setOffsetY(finite(buffer.readFloat(), 0.0F));
			shape.setOffsetZ(finite(buffer.readFloat(), 0.0F));
		}
		return shape;
	}

	private static float finite(float value, float fallback) {
		return Float.isFinite(value) ? value : fallback;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("scaleX", scaleX);
		json.addProperty("scaleY", scaleY);
		json.addProperty("scaleZ", scaleZ);
		json.addProperty("perkiness", perkiness);
		json.addProperty("offsetX", offsetX);
		json.addProperty("offsetY", offsetY);
		json.addProperty("offsetZ", offsetZ);
		return json;
	}

	/** Missing or malformed keys fall back to their default instead of failing the whole load. */
	public static ShapeData fromJson(JsonObject json) {
		ShapeData shape = new ShapeData();
		if(json == null) {
			return shape;
		}
		shape.setScaleX(optFloat(json, "scaleX", 1.0F));
		shape.setScaleY(optFloat(json, "scaleY", 1.0F));
		shape.setScaleZ(optFloat(json, "scaleZ", 1.0F));
		shape.setPerkiness(optFloat(json, "perkiness", 0.0F));
		shape.setOffsetX(optFloat(json, "offsetX", 0.0F));
		shape.setOffsetY(optFloat(json, "offsetY", 0.0F));
		shape.setOffsetZ(optFloat(json, "offsetZ", 0.0F));
		return shape;
	}

	private static float optFloat(JsonObject json, String key, float fallback) {
		try {
			return json.get(key).getAsFloat();
		} catch(Exception e) {
			//null / wrong primitive type / nested object all fall back
			return fallback;
		}
	}

	public ShapeData copy() {
		ShapeData copy = new ShapeData();
		copy.scaleX = this.scaleX;
		copy.scaleY = this.scaleY;
		copy.scaleZ = this.scaleZ;
		copy.perkiness = this.perkiness;
		copy.offsetX = this.offsetX;
		copy.offsetY = this.offsetY;
		copy.offsetZ = this.offsetZ;
		return copy;
	}
}
