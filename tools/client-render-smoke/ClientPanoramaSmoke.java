import com.moulberry.flashback.exporting.ImageFrame;
import com.moulberry.flashback.exporting.PanoramaProjector;
import org.lwjgl.system.MemoryUtil;

/** Cardinal directions, cross layout, alpha, depth and audio ownership. */
public final class ClientPanoramaSmoke {
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static int pixel(ImageFrame image, int x, int y) {
        return MemoryUtil.memGetInt(image.pixels + 4L * (x + y * image.width));
    }
    static void check(ImageFrame.Format format) {
        ImageFrame[] faces = new ImageFrame[6];
        int[] colors = new int[6];
        try {
            for (int face = 0; face < 6; face++) {
                faces[face] = new ImageFrame(8, 8, format, true);
                colors[face] = format == ImageFrame.Format.RGBA_U8 ? 0x40102030 + face : Float.floatToRawIntBits((face + 1) / 8f);
                for (int p = 0; p < 64; p++) MemoryUtil.memPutInt(faces[face].pixels + 4L*p, colors[face]);
            }
            faces[0].audioBuffer = java.nio.FloatBuffer.wrap(new float[] {.25f});
            try (ImageFrame cross = PanoramaProjector.compose(faces, 32, 24, true)) {
                for (int face = 0; face < 4; face++) require(pixel(cross, face*8+4, 12) == colors[face], "Cross lateral face " + face);
                require(pixel(cross, 12, 4) == colors[4], "Cross top face");
                require(pixel(cross, 12, 20) == colors[5], "Cross bottom face");
                require(pixel(cross, 0, 0) == 0 && pixel(cross, 31, 23) == 0, "Cross unused pixels stay transparent/zero");
                require(cross.audioBuffer == faces[0].audioBuffer, "Audio buffer preserved");
            }
            try (ImageFrame sphere = PanoramaProjector.compose(faces, 64, 32, false)) {
                require(pixel(sphere, 0, 16) == colors[3], "Longitude zero");
                require(pixel(sphere, 16, 16) == colors[0], "Longitude quarter");
                require(pixel(sphere, 32, 16) == colors[1], "Longitude half");
                require(pixel(sphere, 48, 16) == colors[2], "Longitude three quarters");
                for (int x = 0; x < 64; x++) {
                    require(pixel(sphere, x, 0) == colors[4], "North pole");
                    require(pixel(sphere, x, 31) == colors[5], "South pole");
                }
                for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
                    int value = pixel(sphere, x, y); boolean found = false;
                    for (int color : colors) found |= color == value;
                    require(found, "Complete sphere coverage / bit-exact color or depth");
                }
            }
        } finally { for (ImageFrame face : faces) if (face != null) face.close(); }
    }
    public static void main(String[] args) {
        check(ImageFrame.Format.RGBA_U8); check(ImageFrame.Format.GRAY_F32);
        System.out.println("PASS: production cube cross and equirectangular reconstruction, six cardinal directions, RGBA alpha, float depth and audio retention");
    }
}
