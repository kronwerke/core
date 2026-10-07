package de.kronwerke.core.link;

/**
 * Which server this is, as the launcher passes it: -Dlauncher.server is the server's name,
 * -Dlauncher.role what it does. "main" (or no launcher at all) owns the season, the goals,
 * the slots and the obelisk; every other role is a side world that asks main over the bus.
 */
public final class Role {
    private static final String SERVER = System.getProperty("launcher.server", "main");
    private static final String ROLE = System.getProperty("launcher.role", "main");

    private Role() {
    }

    public static String server() {
        return SERVER;
    }

    public static String role() {
        return ROLE;
    }

    /** The server that owns season, goals, slots and the obelisk. */
    public static boolean main() {
        return ROLE.equals("main");
    }
}
