import com.moulberry.flashback.exporting.*;
import com.moulberry.flashback.combo_options.*;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL33.*;

/** Production asynchronous encoding, GPU capture and native-library integration. */
public final class ClientExportSmoke {
    static final int WIDTH = 64, HEIGHT = 48, FPS = 24, FRAMES = 24;
    static String h264Encoder;
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static ExportSettings settings(Path path, boolean png) {
        return new ExportSettings("smoke", null, null, 0, 0, WIDTH, HEIGHT, 0, 20,
            ExportProjection.PERSPECTIVE, 1, FPS, true, false,
            png ? VideoContainer.PNG_SEQUENCE : VideoContainer.MP4,
            png ? VideoCodec.PNG : VideoCodec.H264, png ? "png" : h264Encoder, 1_000_000,
            png, false, true, true, png ? null : AudioCodec.AAC, path, null);
    }
    static ImageFrame capture(SaveableFramebuffer downloader, int fbo) {
        downloader.startDownload(fbo, false);
        long deadline = System.nanoTime() + 5_000_000_000L;
        ImageFrame result;
        while ((result = downloader.finishDownload()) == null) {
            if (System.nanoTime() > deadline) throw new AssertionError("GPU capture timed out");
            java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
        }
        return result;
    }
    static void finish(AsyncFFmpegVideoWriter writer) {
        long deadline = System.nanoTime() + 30_000_000_000L;
        writer.finish(stage -> { if (System.nanoTime() > deadline) throw new AssertionError("Encoder timed out: " + stage); });
        writer.close();
    }
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        NativeLibraryBootstrap.initialize(directory.resolve("native-cache"), false);
        for (String candidate : new String[] { "libx264", "libopenh264" }) {
            if (org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name(candidate) != null) {
                h264Encoder = candidate; break;
            }
        }
        require(h264Encoder != null, "Embedded FFmpeg software H.264 encoder");
        System.out.println("H.264 encoder: " + h264Encoder);
        require(GLFW.glfwInit(), "GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "Flashback export validation", 0, 0);
        require(window != 0, "Hidden GL context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            int fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Complete FBO");
            try (SaveableFramebuffer downloader = new SaveableFramebuffer(WIDTH, HEIGHT)) {
                Path video = directory.resolve("gpu-video-audio.mp4");
                AsyncFFmpegVideoWriter writer = new AsyncFFmpegVideoWriter(settings(video, false), video.toString());
                for (int frame = 0; frame < FRAMES; frame++) {
                    glClearColor(frame / (float) (FRAMES - 1), .25f, .75f, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    ImageFrame image = capture(downloader, fbo);
                    FloatBuffer audio = ByteBuffer.allocateDirect(48000 / FPS * 2 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    for (int sample = 0; sample < 48000 / FPS; sample++) {
                        double t = (frame * (48000 / FPS) + sample) / 48000.0;
                        audio.put((float) (.3 * Math.sin(2 * Math.PI * 440 * t)));
                        audio.put((float) (.2 * Math.sin(2 * Math.PI * 660 * t)));
                    }
                    audio.flip(); image.audioBuffer = audio;
                    writer.encode(image);
                }
                finish(writer);
                require(Files.size(video) > 1000, "MP4 output size");
                try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.toFile())) {
                    grabber.setSampleMode(org.bytedeco.javacv.FrameGrabber.SampleMode.FLOAT);
                    grabber.start();
                    require(grabber.getImageWidth() == WIDTH && grabber.getImageHeight() == HEIGHT, "Decoded resolution");
                    require(grabber.getAudioChannels() == 2 && grabber.getSampleRate() == 48000, "Decoded stereo 48 kHz");
                    int imageCount = 0, audioCount = 0; double amplitude = 0;
                    Frame decoded;
                    while ((decoded = grabber.grab()) != null) {
                        if (decoded.image != null) imageCount++;
                        if (decoded.samples != null) {
                            audioCount++;
                            for (Buffer buffer : decoded.samples) {
                                FloatBuffer samples = (FloatBuffer) buffer;
                                while (samples.hasRemaining()) amplitude = Math.max(amplitude, Math.abs(samples.get()));
                            }
                        }
                    }
                    require(imageCount == FRAMES, "Decoded frame count: " + imageCount);
                    require(audioCount > 0 && amplitude > .1, "Decoded audible samples");
                    require(grabber.getLengthInTime() >= 950000 && grabber.getLengthInTime() < 1200000, "One-second duration");
                }
                Path png = directory.resolve("gpu-transparent.png");
                glClearColor(1, 0, 0, .25f); glClear(GL_COLOR_BUFFER_BIT);
                writer = new AsyncFFmpegVideoWriter(settings(png, true), png.toString());
                writer.encode(capture(downloader, fbo)); finish(writer);
                var image = ImageIO.read(png.toFile());
                require(image.getWidth() == WIDTH && image.getHeight() == HEIGHT, "PNG dimensions");
                int pixel = image.getRGB(WIDTH/2, HEIGHT/2);
                require((pixel & 0x00ffffff) == 0xff0000 && Math.abs((pixel >>> 24) - 64) <= 1, "PNG RGBA/alpha preservation");
                h264Encoder = "flashback_test_missing_encoder";
                writer = new AsyncFFmpegVideoWriter(settings(directory.resolve("missing.mp4"), false), directory.resolve("missing.mp4").toString());
                boolean rejected = false;
                try { writer.encode(capture(downloader, fbo)); }
                catch (IllegalArgumentException expected) { rejected = expected.getMessage().contains(h264Encoder); }
                finally { writer.close(); }
                require(rejected, "Unavailable encoder fails clearly and closes safely");
            }
            require(glGetError() == GL_NO_ERROR, "GL error");
            glDeleteFramebuffers(fbo); glDeleteTextures(texture);
            System.out.println("PASS: production async GPU -> RGBA/YUV -> H.264/AAC MP4, 24 decoded frames, stereo 48 kHz sound, transparent PNG, missing encoder cleanup");
            String loaded = org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avutil.class);
            require(Path.of(loaded).startsWith(directory.toAbsolutePath().resolve("native-cache")), "Native load uses prepared immutable files");
            long modified = Files.getLastModifiedTime(Path.of(loaded)).toMillis();
            for (int i = 0; i < 5; i++) org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avutil.class);
            require(modified == Files.getLastModifiedTime(Path.of(loaded)).toMillis(), "Repeated native loads never rewrite the loaded DLL");
            System.out.println("PASS: immutable native bundle and repeated JavaCPP loads without DLL rewriting");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
}
