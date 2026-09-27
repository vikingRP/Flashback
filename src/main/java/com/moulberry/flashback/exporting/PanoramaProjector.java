package com.moulberry.flashback.exporting;

/** CPU reconstruction of the six downloaded camera faces; independent of the game renderer. */
public final class PanoramaProjector {
    private PanoramaProjector() {}

    /** The caller retains ownership of the six faces and owns the returned image. */
    public static ImageFrame compose(ImageFrame[] frames, int resolutionX, int resolutionY, boolean cubeMap) {
        if (frames.length != 6) throw new IllegalArgumentException("Six camera faces required");
        if (resolutionX <= 0 || resolutionY <= 0) throw new IllegalArgumentException("Positive output dimensions required");
        for (ImageFrame frame : frames) {
            if (frame == null || frame.format != frames[0].format || frame.width <= 0 || frame.height <= 0)
                throw new IllegalArgumentException("Camera faces must have matching formats and positive dimensions");
        }
        ImageFrame target = new ImageFrame(resolutionX, resolutionY, frames[0].format, true);
        target.audioBuffer = frames[0].audioBuffer;

        if (cubeMap) {
            for (int i = 0; i < frames.length; i++) {
                int positionX;
                int positionY;

                if (i < 4) {
                    positionX = resolutionX * i / 4;
                    positionY = resolutionY / 3;
                } else if (i == 4) {
                    positionX = resolutionX / 4;
                    positionY = 0;
                } else if (i == 5) {
                    positionX = resolutionX / 4;
                    positionY = resolutionY * 2 / 3;
                } else {
                    break;
                }

                ImageFrame image = frames[i];
                int sizeX = Math.min(image.width, target.width - positionX);
                int sizeY = Math.min(image.height, target.height - positionY);
                image.copyRect(target, 0, 0, positionX, positionY, sizeX, sizeY);
            }
        } else {
            for (int y = 0; y < resolutionY; y++) {
                for (int x = 0; x < resolutionX; x++) {
                    double yaw = (double) x / resolutionX * 2.0 * Math.PI;
                    double pitch = (double) y / resolutionY * Math.PI - Math.PI/2.0;

                    // Sphere xyz
                    double sx = -Math.sin(yaw) * Math.cos(pitch);
                    double sy = Math.sin(pitch);
                    double sz = -Math.cos(yaw) * Math.cos(pitch);

                    // Cube xyz
                    double a = Math.max(Math.abs(sx), Math.max(Math.abs(sy), Math.abs(sz)));
                    double cx = sx / a;
                    double cy = sy / a;
                    double cz = sz / a;

                    if (cy == -1.0) {
                        ImageFrame image = frames[4];
                        int imageX = (int) Math.round((cx+1)/2 * (image.width-1));
                        int imageY = (int) Math.round((cz+1)/2 * (image.height-1));

                        image.copyPixel(target, imageX, imageY, x, y);
                    } else if (cy == 1.0) {
                        ImageFrame image = frames[5];
                        int imageX = (int) Math.round((cx+1)/2 * (image.width-1));
                        int imageY = image.height-1 - (int) Math.round((cz+1)/2 * (image.height-1));

                        image.copyPixel(target, imageX, imageY, x, y);
                    } else if (cz == -1.0) {
                        ImageFrame image = frames[3];
                        int imageX = image.width-1 - (int) Math.round((cx+1)/2 * (image.width-1));
                        int imageY = (int) Math.round((cy+1)/2 * (image.height-1));

                        image.copyPixel(target, imageX, imageY, x, y);
                    } else if (cz == 1.0) {
                        ImageFrame image = frames[1];
                        int imageX = (int) Math.round((cx+1)/2 * (image.width-1));
                        int imageY = (int) Math.round((cy+1)/2 * (image.height-1));

                        image.copyPixel(target, imageX, imageY, x, y);
                    } else if (cx == 1.0) {
                        ImageFrame image = frames[2];
                        int imageX = image.width-1 - (int) Math.round((cz+1)/2 * (image.width-1));
                        int imageY = (int) Math.round((cy+1)/2 * (image.height-1));

                        image.copyPixel(target, imageX, imageY, x, y);
                    } else if (cx == -1.0) {
                        ImageFrame image = frames[0];
                        int imageX = (int) Math.round((cz+1)/2 * (image.width-1));
                        int imageY = (int) Math.round((cy+1)/2 * (image.height-1));

                        image.copyPixel(target, imageX, imageY, x, y);
                    }
                }
            }
        }
        return target;
    }
}
