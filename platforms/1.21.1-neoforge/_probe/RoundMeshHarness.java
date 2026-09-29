import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;

/**
 * Offline harness for RoundBreastMesh.of against a REAL FGM 3.2.2 BreastModelBox —
 * the exact box GenderLayer's constructor builds for the left breast
 * (4x5x4 at (-4,0,0), texture (16,17), no inflate). No Minecraft needed beyond
 * the few referenced vanilla classes (Direction/Vec3i) on the classpath.
 *
 * <p>Adapted from the 1.21.11-fabric harness: FGM 3.2.2 has no UVLayout — the
 * box builds its five faces fully mapped, so the degenerate-face case does not
 * exist and the expected quad count is computed from the actual quads instead
 * of hardcoded.</p>
 */
public class RoundMeshHarness {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  <- " + detail));
        if(!ok) failures++;
    }

    static int expectedQuads(WildfireModelRenderer.ModelBox box) {
        int total = 0;
        for(WildfireModelRenderer.TexturedQuad src : box.quads) {
            if(src == null) continue;
            WildfireModelRenderer.PositionTextureVertex[] v = src.vertexPositions;
            float es = Math.abs(v[1].x() - v[0].x()) + Math.abs(v[1].y() - v[0].y()) + Math.abs(v[1].z() - v[0].z());
            float et = Math.abs(v[3].x() - v[0].x()) + Math.abs(v[3].y() - v[0].y()) + Math.abs(v[3].z() - v[0].z());
            total += Math.max(1, Math.min(8, Math.round(es))) * Math.max(1, Math.min(8, Math.round(et)));
        }
        return total;
    }

    public static void main(String[] args) {
        //exactly the box from GenderLayer's constructor: left breast, 4x5x4 at (-4,0,0)
        WildfireModelRenderer.ModelBox box = new WildfireModelRenderer.BreastModelBox(64, 64, 16, 17, -4F, 0F, 0F, 4, 5, 4, 0.0F, false);
        System.out.println("source quads=" + box.quads.length);

        RoundBreastMesh mesh = RoundBreastMesh.of(box, 0.85F);
        int expected = expectedQuads(box);
        System.out.println("quadCount=" + mesh.quadCount + " expected=" + expected + " floats=" + mesh.data.length);
        check("nonempty-mesh", mesh.quadCount > 0 && mesh.data.length == mesh.quadCount * 32,
                "quads=" + mesh.quadCount + " floats=" + mesh.data.length);
        check("quad-count-matches-faces", mesh.quadCount == expected, "quads=" + mesh.quadCount + " expected " + expected);
        check("cache-stable", RoundBreastMesh.of(box, 0.85F) == mesh, "cache returned a fresh mesh");
        check("cache-invalidates", RoundBreastMesh.of(box, 0.5F) != mesh, "roundness change kept stale mesh");

        //every vertex inside the source box envelope (anti-clip), every normal unit,
        //every UV inside the mapped atlas region of the source faces
        float uMin = Float.POSITIVE_INFINITY, vMin = Float.POSITIVE_INFINITY, uMax = Float.NEGATIVE_INFINITY, vMax = Float.NEGATIVE_INFINITY;
        for(WildfireModelRenderer.TexturedQuad q : box.quads) {
            if(q == null) continue;
            for(WildfireModelRenderer.PositionTextureVertex v : q.vertexPositions) {
                uMin = Math.min(uMin, v.texturePositionX()); uMax = Math.max(uMax, v.texturePositionX());
                vMin = Math.min(vMin, v.texturePositionY()); vMax = Math.max(vMax, v.texturePositionY());
            }
        }
        boolean inside = true, unit = true;
        for(int o = 0; o < mesh.data.length; o += 8) {
            float x = mesh.data[o], y = mesh.data[o + 1], z = mesh.data[o + 2];
            //box: x -4..0, y 0..5, z 0..4 px -> /16 world units
            if(x < -4f / 16 - 1e-4 || x > 1e-4 || y < -1e-4 || y > 5f / 16 + 1e-4 || z < -1e-4 || z > 4f / 16 + 1e-4) {
                inside = false;
                System.out.println("  outside vertex at " + o + ": " + x + "," + y + "," + z);
            }
            float nx = mesh.data[o + 3], ny = mesh.data[o + 4], nz = mesh.data[o + 5];
            if(Math.abs(nx * nx + ny * ny + nz * nz - 1f) > 1e-3) unit = false;
            float u = mesh.data[o + 6], v = mesh.data[o + 7];
            if(u < uMin - 1e-4 || u > uMax + 1e-4 || v < vMin - 1e-4 || v > vMax + 1e-4) {
                unit = false;
                System.out.println("  uv outside source region: " + u + "," + v);
            }
        }
        check("envelope", inside, "vertex escaped the source box");
        check("normals-and-uvs", unit, "non-unit normal or UV outside the source UV region");

        //curvature actually applied: the max normalized EUCLIDEAN radius of a super-
        //ellipsoid with n>2 sits at the diagonal: sqrt(3^(1-2/n)); for r=0.85,
        //n=2/0.85 -> sqrt(3^0.15) = 1.0860. A flat box would give sqrt(3)=1.732,
        //an inscribed sphere 1.0 — matching theory to 4 decimals proves the morph
        float maxDist = 0f;
        for(int o = 0; o < mesh.data.length; o += 8) {
            float x = mesh.data[o] * 16 + 2, y = mesh.data[o + 1] * 16 - 2.5f, z = mesh.data[o + 2] * 16 - 2f;
            maxDist = Math.max(maxDist, (float) Math.sqrt(x * x / 4f + y * y / 6.25f + z * z / 4f));
        }
        float theory = (float) Math.sqrt(Math.pow(3, 1 - 2 * 0.85 / 2));
        check("curvature-applied", Math.abs(maxDist - theory) < 1e-3, "max=" + maxDist + " theory=" + theory);

        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }
}
