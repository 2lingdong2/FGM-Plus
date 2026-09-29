import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;

/**
 * Offline harness for RoundBreastMesh.of against a REAL FGM 3.1 BreastModelBox —
 * the exact 4x5x4 box GenderLayer's constructor builds (bytecode-verified args).
 * FGM 3.1 has no UVLayout and no degenerate-UV concept: its ModelBox.initQuads
 * fills all six faces. Runs on the classpath: build/classes + FGM deobf jar +
 * forge-1.20.1-47.3.0_mapped_official_1.20.1.jar (Direction/Vec3i only) + slf4j.
 * Exit code 0 = all assertions passed.
 */
public class RoundMeshHarness {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  <- " + detail));
        if(!ok) failures++;
    }

    static int segments(float pixelLength) {
        return Math.max(1, Math.min(8, Math.round(pixelLength)));
    }

    public static void main(String[] args) throws Exception {
        //exactly the construction GenderLayer's <init> runs (javap-verified FGM 3.1):
        //texW=64 texH=64 u=16 v=17 at (-4,0,0), 4x5x4 px, delta 0, no mirror
        WildfireModelRenderer.BreastModelBox box =
                new WildfireModelRenderer.BreastModelBox(64, 64, 16, 17, -4F, 0F, 0F, 4, 5, 4, 0.0F, false);

        //3.1 fills FIVE faces (quads.length == 5: E, W, D, U, N) — the breast box has
        //NO back face (+z / SOUTH), it sits open against the torso. No null slots, no
        //degenerate-UV concept (the mesh keeps only a defensive null skip)
        int faces = 0;
        int expectedQuads = 0;
        for(WildfireModelRenderer.TexturedQuad q : box.quads) {
            if(q == null) continue;
            faces++;
            WildfireModelRenderer.PositionTextureVertex[] v = q.vertexPositions;
            float eS = Math.abs(v[1].x() - v[0].x()) + Math.abs(v[1].y() - v[0].y()) + Math.abs(v[1].z() - v[0].z());
            float eT = Math.abs(v[3].x() - v[0].x()) + Math.abs(v[3].y() - v[0].y()) + Math.abs(v[3].z() - v[0].z());
            expectedQuads += segments(eS) * segments(eT);
        }
        check("five-faces-present", faces == 5 && box.quads.length == 5,
                "faces=" + faces + " quads.length=" + box.quads.length);

        RoundBreastMesh mesh = RoundBreastMesh.of(box, 0.85F, true);
        System.out.println("quadCount=" + mesh.quadCount + " floats=" + mesh.data.length);
        check("nonempty-mesh", mesh.quadCount > 0 && mesh.data.length == mesh.quadCount * 32,
                "quads=" + mesh.quadCount + " floats=" + mesh.data.length);
        check("quad-count-matches-faces", mesh.quadCount == expectedQuads,
                "quads=" + mesh.quadCount + " expected=" + expectedQuads);
        check("cache-stable", RoundBreastMesh.of(box, 0.85F, true) == mesh, "cache returned a fresh mesh");
        check("cache-invalidates", RoundBreastMesh.of(box, 0.5F, true) != mesh, "roundness change kept stale mesh");

        //every vertex inside the source box envelope (anti-clip), every normal unit,
        //every UV in [0,1] (3.1's TexturedQuad ctor divides the px rect by the tex size)
        boolean inside = true, unit = true;
        for(int o = 0; o < mesh.data.length; o += 8) {
            float x = mesh.data[o], y = mesh.data[o + 1], z = mesh.data[o + 2];
            //box: x -4..0, y 0..5, z 0..4 px -> /16 world units
            if(x < -4f / 16 - 1e-4 || x > 1.3f / 16 + 1e-4 || y < -1e-4 || y > 5f / 16 + 1e-4 || z < -1e-4 || z > 4f / 16 + 1e-4) {
                inside = false;
                System.out.println("  outside vertex at " + o + ": " + x + "," + y + "," + z);
            }
            float nx = mesh.data[o + 3], ny = mesh.data[o + 4], nz = mesh.data[o + 5];
            if(Math.abs(nx * nx + ny * ny + nz * nz - 1f) > 1e-3) unit = false;
            float u = mesh.data[o + 6], v = mesh.data[o + 7];
            if(u < 0 || v < 0 || u > 1 || v > 1) { unit = false; System.out.println("  uv out of atlas: " + u + "," + v); }
        }
        check("envelope", inside, "vertex escaped the source box");
        check("normals-and-uvs", unit, "non-unit normal or UV outside [0,1]");

        //curvature actually applied: the max normalized EUCLIDEAN radius of a super-
        //ellipsoid with n>2 sits at the diagonal: sqrt(3^(1-2/n)); for r=0.85,
        //n=2/0.85 -> sqrt(3^0.15) = 1.0860. A flat box would give sqrt(3)=1.732,
        //an inscribed sphere 1.0 — matching theory to 4 decimals proves the morph
        float maxDist = 0f;
        for(int o = 0; o < mesh.data.length; o += 8) {
            float x = mesh.data[o] * 16 + 2, y = mesh.data[o + 1] * 16 - 2.5f, z = mesh.data[o + 2] * 16 - 2f;
            if(x >= 0) continue; //bridged inner half is intentionally NOT the pure surface
            maxDist = Math.max(maxDist, (float) Math.sqrt(x * x / 4f + y * y / 6.25f + z * z / 4f));
        }
        float theory = (float) Math.sqrt(Math.pow(3, 1 - 2 * 0.85 / 2));
        check("curvature-applied", Math.abs(maxDist - theory) < 1e-3, "max=" + maxDist + " theory=" + theory);

        //cleavage bridge: the bridged mesh's inner vertices must cross the torso
        //centerline plane (world x = 0), while the plain (bridge-off) mesh must not
        RoundBreastMesh plain = RoundBreastMesh.of(box, 0.85F, false);
        float bridgedMaxX = Float.NEGATIVE_INFINITY, plainMaxX = Float.NEGATIVE_INFINITY;
        for(int o = 0; o < mesh.data.length; o += 8) bridgedMaxX = Math.max(bridgedMaxX, mesh.data[o]);
        for(int o = 0; o < plain.data.length; o += 8) plainMaxX = Math.max(plainMaxX, plain.data[o]);
        check("cleavage-bridge", bridgedMaxX > 0.5f / 16 && bridgedMaxX <= 1.3f / 16 && plainMaxX <= 1e-4,
                "bridged max x=" + (bridgedMaxX * 16) + "px plain max x=" + (plainMaxX * 16) + "px");

        //multi-slot cache: a second player at a different roundness must not evict
        //the first mesh (<= 3 slots kept)
        RoundBreastMesh again = RoundBreastMesh.of(box, 0.85F, true);
        check("multi-slot-survives", again == mesh, "0.85 slot was evicted by 0.5");

        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }
}
