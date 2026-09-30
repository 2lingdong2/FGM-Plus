import com.wildfire.render.WildfireModelRenderer;
import io.github.e33epus.fgmplus.render.RoundBreastMesh;

public class NaNsweep {
    public static void main(String[] args) {
        WildfireModelRenderer.ModelBox box = new WildfireModelRenderer.BreastModelBox(64, 64, 16, 17, -4F, 0F, 0F, 4, 5, 4, 0.0F, false);
        int bad = 0;
        for (float r = 0.01F; r <= 1.0001F; r += 0.01F) {
            for (boolean bridged : new boolean[]{true, false}) {
                RoundBreastMesh m = RoundBreastMesh.of(box, Math.min(r, 1.0F), bridged);
                for (int o = 0; o < m.data.length; o += 8) {
                    for (int k = 0; k < 6; k++) {
                        float v = m.data[o + k];
                        if (!Float.isFinite(v)) { bad++; System.out.println("NON-FINITE r=" + r + " bridged=" + bridged + " comp=" + k + " val=" + v); }
                    }
                }
            }
        }
        System.out.println(bad == 0 ? "SWEEP CLEAN: no NaN/Inf in 100 roundness values x both bridge states" : bad + " non-finite values");
    }
}
