import com.wildfire.render.WildfireModelRenderer;

public class FaceProbe {
    public static void main(String[] args) {
        WildfireModelRenderer.BreastModelBox box =
                new WildfireModelRenderer.BreastModelBox(64, 64, 16, 17, -4F, 0F, 0F, 4, 5, 4, 0.0F, false);
        System.out.println("quads.length=" + box.quads.length);
        for(int i = 0; i < box.quads.length; i++) {
            WildfireModelRenderer.TexturedQuad q = box.quads[i];
            if(q == null) { System.out.println("quad[" + i + "]=null"); continue; }
            System.out.println("quad[" + i + "] normal=" + q.normal.getX() + "," + q.normal.getY() + "," + q.normal.getZ());
        }
    }
}
