package de.kronwerke.core.camera;

/**
 * The camera: a Minecraft client without a screen that takes the pictures for the quest book
 * on the real server. Started by the camera bootstrap with -Dkronwerke.camera=true.
 */
public final class CameraClient {
    public static final boolean ON = Boolean.getBoolean("kronwerke.camera");

    private CameraClient() {
    }
}
