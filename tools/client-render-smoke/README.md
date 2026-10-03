# Client rendering smoke test

Run from the repository root after Forge dependencies have been downloaded:

```powershell
py -X utf8 tools/client-render-smoke/run.py --java-home "C:/path/to/jdk-17"
```

The test opens an invisible GLFW OpenGL 3.3 context, compiles the **production**
`ShaderManager`, and checks actual pixels for RGBA copy, vertical flip,
transparent-alpha preservation, alpha normalization and perspective depth.
It also checks viewport, framebuffer, depth/scissor state restoration and GL errors.
No Minecraft account, window interaction, save or replay is required.
The Gradle cache location respects `GRADLE_USER_HOME`.

Validated with JDK 17.0.19 and NVIDIA RTX 3050 / OpenGL 3.3 on 2026-09-27.
After `gradlew compileJava`, append `--capture` to test the production
`SaveableFramebuffer` and `ImageFrame` classes: exact RGBA pixels over 20 PBO
reuses, floating-point depth, rejection of overlapping captures, and restoration
of the framebuffer, pixel-pack buffer and pack layout. This check also passed
on the same Java 17 / OpenGL 3.3 setup.

`--capture` also compiles and tests the production `PanoramaProjector` for cube
cross/equirectangular layouts, six cardinal directions, alpha, float depth and
audio retention. It passed on the same setup.

After resolving the client runtime, append `--export` for actual production
asynchronous GPU capture -> RGBA/YUV conversion -> H.264/AAC MP4 and transparent
PNG encoding. This uses the embedded FFmpeg native libraries. JavaCV decodes
the MP4 and verifies 24 frames at 64?48, one-second duration and audible stereo
48 kHz audio; ImageIO verifies PNG RGBA pixels. Outputs are in
`build/client-render-smoke/exports/`. Hardware encoder probes may log unavailable
devices; the test uses the embedded software H.264 encoder (`libopenh264` here).
Run after Gradle finishes, since it rebuilds the classes and embedded runtime.

The export check also exercises background encoder detection with the embedded
native libraries. It blocks probing with a container monitor to verify that the
caller returns without waiting, checks that repeated opens share pending and
completed detection, and validates video, audio and transparency capabilities.

ImGui interaction, OpenAL sound capture, camera orientation and replay playback
still require full client integration checks.
