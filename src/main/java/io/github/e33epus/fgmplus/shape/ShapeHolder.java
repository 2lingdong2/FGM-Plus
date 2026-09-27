package io.github.e33epus.fgmplus.shape;

/**
 * Duck interface mixed into {@link com.wildfire.main.GenderPlayer}.
 */
public interface ShapeHolder {

    ShapeData fgmplus$getShape();

    void fgmplus$setShape(ShapeData shape);
}
