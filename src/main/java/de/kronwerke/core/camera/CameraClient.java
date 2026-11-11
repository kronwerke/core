package de.kronwerke.core.camera;

/**
 * The camera: a Minecraft client without a screen that takes the pictures for the quest book
 * on the real server. Started by the camera bootstrap with -Dkronwerke.camera=true.
 */
public final class CameraClient {
    /** Without a screen: GLFW's null platform and an OSMesa context. */
    public static final boolean HEADLESS = Boolean.getBoolean("kronwerke.camera");
    /** The test player (see the probe package): headless too, but otherwise a player like any other. */
    public static final boolean PROBE = Boolean.getBoolean("kronwerke.probe");
    /** The camera itself: takes pictures, sees no signs and labels, closes every screen. */
    public static final boolean ON = HEADLESS && !PROBE;

    private CameraClient() {
    }
}
