package io.github.e33epus.fgmplus.render;

import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.FgmPlusMod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Turns FGM's flat 4x5x4 breast box into a superellipsoid mesh — the fix for the
 * "triangle" silhouette (a box tilted forward by -35deg reads as a wedge).
 *
 * <p>Deformation is a radial projection onto the superellipsoid
 * {@code |x|^n + |y|^n + |z|^n = 1} in the box's normalized local space, with
 * {@code n = 2 / roundness}: n is +infinity for a box, 2 for a sphere, so the
 * roundness slider walks the box→sphere continuum and every intermediate value
 * is a rounded box. Because the projection is continuous, adjacent faces share
 * identical boundary vertices — no seams; face centers stay on the box surface
 * (axial points project onto themselves), so a slightly rounded breast keeps
 * sitting on the torso and only its edges pull in.</p>
 *
 * <p>Vertex normals use the superellipsoid gradient
 * {@code (sign(x)|x|^(n-1), ...)} normalized — the analytic surface normal, so
 * lighting is smooth across subdivided faces and continuous across edges.</p>
 *
 * <p>UVs interpolate inside each original quad's (already normalized) corner
 * UVs, so the FGM texture mapping is preserved onto the curved surface. FGM 3.1
 * stores those UVs directly on {@link WildfireModelRenderer.PositionTextureVertex}
 * as {@code texturePositionX()/texturePositionY()} (already divided by the
 * texture size in the TexturedQuad constructor) and GenderLayer passes them
 * straight into {@code VertexConsumer#uv}, so the mesh lerps the raw values.</p>
 *
 * <p>Subdivision density follows the box dimensions (~one segment per model
 * pixel), so a 4x5x4 box becomes ~100 quads. That is only worth computing when
 * the box or the roundness changes: meshes are cached per box instance
 * (GenderLayer keeps its boxes as layer fields, so instances are stable) and
 * regenerated when the roundness differs. Rendering is single-threaded.</p>
 */
public final class RoundBreastMesh {

    /** Flat interleaved vertices: [x, y, z, nx, ny, nz, u, v] x 4 per quad, world units (px/16). */
    public final float[] data;
    public final int quadCount;

    private final float roundness;

    //box instance -> roundness -> mesh. Boxes are SHARED across players with the same
    //setup (GenderLayer keeps them as layer fields), and roundness is per player, so
    //a single slot per box made players with different roundness rebuild the mesh every
    //frame while they were both on screen. A few slots cover any realistic scene; the
    //slider drag path (a new value every frame) clears instead of growing unbounded.
    private static final Map<WildfireModelRenderer.ModelBox, Map<Float, RoundBreastMesh>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RoundBreastMesh(float[] data, int quadCount, float roundness) {
        this.data = data;
        this.quadCount = quadCount;
        this.roundness = roundness;
    }

    /** Cached mesh of {@code box} at {@code roundness}; never null for roundness > 0. */
    public static RoundBreastMesh of(WildfireModelRenderer.ModelBox box, float roundness) {
        Map<Float, RoundBreastMesh> slots = CACHE.computeIfAbsent(box, k -> new java.util.HashMap<>());
        synchronized(slots) {
            if(slots.size() > 3) {
                slots.clear();
            }
            RoundBreastMesh mesh = slots.get(roundness);
            if(mesh == null) {
                mesh = build(box, roundness);
                slots.put(roundness, mesh);
                //cache-miss only; the first-line observability for
                //"the roundness slider does nothing" reports
                FgmPlusMod.LOGGER.debug("FGM Plus built round mesh: quads={}, roundness={}", mesh.quadCount, roundness);
            }
            return mesh;
        }
    }

    private static RoundBreastMesh build(WildfireModelRenderer.ModelBox box, float roundness) {
        //exponent for the box->sphere continuum: roundness 1 -> n=2 (sphere);
        //clamped away from 0 so callers can pass small values without pow blowups
        float r = Math.max(roundness, 0.01F);
        double n = 2.0 / r;

        //bounding box in model pixels, from the actual quad vertices (the pos* fields
        //predate the inflate delta and can disagree by it). FGM 3.1's TexturedQuad has
        //no degenerate-UV concept (no uvs array, renderBox filters nothing), so only a
        //defensive null skip is applied here and in the emit loop below.
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        int usable = 0;
        for(WildfireModelRenderer.TexturedQuad quad : box.quads) {
            if(quad == null) continue;
            usable++;
            for(WildfireModelRenderer.PositionTextureVertex v : quad.vertexPositions) {
                minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
                minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
                minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
            }
        }
        if(usable == 0) {
            return new RoundBreastMesh(new float[0], 0, roundness);
        }
        float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f, cz = (minZ + maxZ) / 2f;
        float hx = Math.max((maxX - minX) / 2f, 1.0e-4f);
        float hy = Math.max((maxY - minY) / 2f, 1.0e-4f);
        float hz = Math.max((maxZ - minZ) / 2f, 1.0e-4f);

        //each subdivided face quad is [x, y, z, nx, ny, nz, u, v] x 4; one source face
        //yields segS x segT sub-quads, so the total is counted before allocating
        int totalQuads = 0;
        for(WildfireModelRenderer.TexturedQuad src : box.quads) {
            if(src == null) continue;
            WildfireModelRenderer.PositionTextureVertex[] v = src.vertexPositions;
            totalQuads += segments(edgeLength(v[0], v[1])) * segments(edgeLength(v[0], v[3]));
        }
        float[] out = new float[totalQuads * 4 * 8];
        int quad = 0;
        for(WildfireModelRenderer.TexturedQuad src : box.quads) {
            if(src == null) continue;
            WildfireModelRenderer.PositionTextureVertex[] c = src.vertexPositions;
            //segment count follows the face's pixel size along each edge, capped so a
            //pathologically large box cannot blow up the vertex count
            float ax = c[0].x(), ay = c[0].y(), az = c[0].z();
            float bx = c[1].x(), by = c[1].y(), bz = c[1].z();
            float dx = c[2].x(), dy = c[2].y(), dz = c[2].z();
            float ex = c[3].x(), ey = c[3].y(), ez = c[3].z();
            int segS = segments(Math.abs(bx - ax) + Math.abs(by - ay) + Math.abs(bz - az));
            int segT = segments(Math.abs(ex - ax) + Math.abs(ey - ay) + Math.abs(ez - az));

            for(int i = 0; i < segS; i++) {
                float s0 = (float) i / segS, s1 = (float) (i + 1) / segS;
                for(int j = 0; j < segT; j++) {
                    float t0 = (float) j / segT, t1 = (float) (j + 1) / segT;
                    //corner order matches the source quad's winding: (a, b, d/c cell corners);
                    //UVs lerp the RAW texturePosition values exactly as 3.1's renderBox
                    //passes them into VertexConsumer#uv
                    emit(out, quad * 32,
                            project(corner(ax, ay, az, bx, by, bz, ex, ey, ez, dx, dy, dz, s0, t0), cx, cy, cz, hx, hy, hz, n),
                            project(corner(ax, ay, az, bx, by, bz, ex, ey, ez, dx, dy, dz, s1, t0), cx, cy, cz, hx, hy, hz, n),
                            project(corner(ax, ay, az, bx, by, bz, ex, ey, ez, dx, dy, dz, s1, t1), cx, cy, cz, hx, hy, hz, n),
                            project(corner(ax, ay, az, bx, by, bz, ex, ey, ez, dx, dy, dz, s0, t1), cx, cy, cz, hx, hy, hz, n),
                            lerp(lerp(c[0].texturePositionX(), c[1].texturePositionX(), s0), lerp(c[3].texturePositionX(), c[2].texturePositionX(), s0), t0),
                            lerp(lerp(c[0].texturePositionY(), c[1].texturePositionY(), s0), lerp(c[3].texturePositionY(), c[2].texturePositionY(), s0), t0),
                            lerp(lerp(c[0].texturePositionX(), c[1].texturePositionX(), s1), lerp(c[3].texturePositionX(), c[2].texturePositionX(), s1), t0),
                            lerp(lerp(c[0].texturePositionY(), c[1].texturePositionY(), s1), lerp(c[3].texturePositionY(), c[2].texturePositionY(), s1), t0),
                            lerp(lerp(c[0].texturePositionX(), c[1].texturePositionX(), s1), lerp(c[3].texturePositionX(), c[2].texturePositionX(), s1), t1),
                            lerp(lerp(c[0].texturePositionY(), c[1].texturePositionY(), s1), lerp(c[3].texturePositionY(), c[2].texturePositionY(), s1), t1),
                            lerp(lerp(c[0].texturePositionX(), c[1].texturePositionX(), s0), lerp(c[3].texturePositionX(), c[2].texturePositionX(), s0), t1),
                            lerp(lerp(c[0].texturePositionY(), c[1].texturePositionY(), s0), lerp(c[3].texturePositionY(), c[2].texturePositionY(), s0), t1));
                    quad++;
                }
            }
        }
        return new RoundBreastMesh(out, quad, roundness);
    }

    private static int segments(float pixelLength) {
        return Math.max(1, Math.min(8, Math.round(pixelLength)));
    }

    private static float edgeLength(WildfireModelRenderer.PositionTextureVertex a, WildfireModelRenderer.PositionTextureVertex b) {
        return Math.abs(b.x() - a.x()) + Math.abs(b.y() - a.y()) + Math.abs(b.z() - a.z());
    }

    /** Bilinear position on the source face quad; corner winding follows the quad's own vertex order. */
    private static float[] corner(float ax, float ay, float az, float bx, float by, float bz,
            float ex, float ey, float ez, float dx, float dy, float dz, float s, float t) {
        float abx = ax + (bx - ax) * s, aby = ay + (by - ay) * s, abz = az + (bz - az) * s;
        float edx = ex + (dx - ex) * s, edy = ey + (dy - ey) * s, edz = ez + (dz - ez) * s;
        return new float[]{abx + (edx - abx) * t, aby + (edy - aby) * t, abz + (edz - abz) * t};
    }

    /**
     * Radial superellipsoid projection: normalized direction from the box center is
     * scaled down onto the {@code sum|x|^n = 1} surface, then mapped back to world
     * units. The returned array is [x, y, z, nx, ny, nz] in model pixels.
     */
    private static float[] project(float[] p, float cx, float cy, float cz,
            float hx, float hy, float hz, double n) {
        double ux = (p[0] - cx) / hx, uy = (p[1] - cy) / hy, uz = (p[2] - cz) / hz;
        double len = Math.pow(Math.pow(Math.abs(ux), n) + Math.pow(Math.abs(uy), n) + Math.pow(Math.abs(uz), n), 1.0 / n);
        //inputs sit on the box surface, so len >= 1 > 0; keep the guard anyway
        if(len < 1.0e-6) {
            return new float[]{p[0], p[1], p[2], 0f, 1f, 0f};
        }
        double sx = ux / len, sy = uy / len, sz = uz / len;

        //analytic superellipsoid normal: gradient of sum|x|^n, normalized. The surface
        //is the unit one stretched by (hx, hy, hz), so the gradient maps with the
        //inverse-transpose scale (per-axis divide) to stay perpendicular to the real,
        //non-uniform surface
        double px = Math.pow(Math.abs(sx), n - 1.0) * Math.signum(sx) / hx;
        double py = Math.pow(Math.abs(sy), n - 1.0) * Math.signum(sy) / hy;
        double pz = Math.pow(Math.abs(sz), n - 1.0) * Math.signum(sz) / hz;
        double gl = Math.sqrt(px * px + py * py + pz * pz);
        float nx, ny, nz;
        if(gl < 1.0e-6) {
            nx = 0f; ny = 1f; nz = 0f;
        } else {
            nx = (float) (px / gl); ny = (float) (py / gl); nz = (float) (pz / gl);
        }
        return new float[]{
                (float) (cx + sx * hx), (float) (cy + sy * hy), (float) (cz + sz * hz),
                nx, ny, nz
        };
    }

    private static void emit(float[] out, int base,
            float[] p0, float[] p1, float[] p2, float[] p3,
            float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        write(out, base, p0, u0, v0);
        write(out, base + 8, p1, u1, v1);
        write(out, base + 16, p2, u2, v2);
        write(out, base + 24, p3, u3, v3);
    }

    private static void write(float[] out, int o, float[] p, float u, float v) {
        out[o] = p[0] / 16f; out[o + 1] = p[1] / 16f; out[o + 2] = p[2] / 16f;
        out[o + 3] = p[3]; out[o + 4] = p[4]; out[o + 5] = p[5];
        out[o + 6] = u; out[o + 7] = v;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Validation hook for the offline math probe; not on any runtime path. Returns [x, y, z, nx, ny, nz]. */
    public static float[] probeProject(float roundness, float px, float py, float pz, float hx, float hy, float hz) {
        return project(new float[]{px, py, pz}, 0f, 0f, 0f, hx, hy, hz, 2.0 / Math.max(roundness, 0.01F));
    }
}
