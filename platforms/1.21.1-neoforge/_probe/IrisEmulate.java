import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;

/** Emulates Iris 1.8 fillExtendedData face-normal overwrite on our mesh data. */
public class IrisEmulate {
    public static void main(String[] args) {
        WildfireModelRenderer.ModelBox box = new WildfireModelRenderer.BreastModelBox(64, 64, 16, 17, -4F, 0F, 0F, 4, 5, 4, 0.0F, false);
        float[] roundness = {0.1F, 0.3F, 0.5F, 0.7F, 0.85F, 1.0F};
        for (float r : roundness) {
            for (boolean bridged : new boolean[]{true, false}) {
                RoundBreastMesh m = RoundBreastMesh.of(box, r, bridged);
                int degenerate = 0, flipped = 0, quads = m.quadCount;
                for (int q = 0; q < quads; q++) {
                    int b = q * 32;
                    float[] v = new float[12];
                    for (int i = 0; i < 4; i++) {
                        v[i*3] = m.data[b + i*8]; v[i*3+1] = m.data[b + i*8 + 1]; v[i*3+2] = m.data[b + i*8 + 2];
                    }
                    float ax = v[2*3]-v[0], ay = v[2*3+1]-v[1], az = v[2*3+2]-v[2];
                    float bx = v[3*3]-v[1*3], by = v[3*3+1]-v[1*3+1], bz = v[3*3+2]-v[1*3+2];
                    float cx = ay*bz - az*by, cy = az*bx - ax*bz, cz = ax*by - ay*bx;
                    double len = Math.sqrt(cx*cx + cy*cy + cz*cz);
                    if (len < 1.0e-6) { degenerate++; continue; }
                    float fnx = (float)(cx/len), fny = (float)(cy/len), fnz = (float)(cz/len);
                    // our written normal of vertex 0
                    float nx = m.data[b+3], ny = m.data[b+4], nz = m.data[b+5];
                    float dot = fnx*nx + fny*ny + fnz*nz;
                    if (dot < -0.5F) flipped++;
                }
                System.out.printf("r=%.2f bridged=%b quads=%d degenerate=%d flipped=%d%n", r, bridged, quads, degenerate, flipped);
            }
        }
    }
}
