package de.kronwerke.core.share;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/**
 * One mod's part of the shared network: Mekanism's quantum entangloporters, AE2's quantum bridges
 * and so on. A layer only ever moves things it has already taken out of its own world, through
 * {@link Share#give}, and gets back whatever the other server could not place.
 */
public interface Layer {
    /** Short and stable, it goes over the bus and into the save: "mek.qe". */
    String name();

    /** Every tick on the server thread: tell the others how things stand here, decide what to give. */
    void tick(MinecraftServer server);

    /** What another server last told about itself through {@link Share#state}. */
    void onState(String from, JsonObject state);

    /** Place what another server gave. Returns what did not fit, or null when everything did. */
    JsonObject receive(MinecraftServer server, String from, JsonObject data);

    /** Put back what came back from the other server. Returns what still does not fit (tried again later), or null. */
    JsonObject refund(MinecraftServer server, JsonObject data);

    /** A line for /kw share: what this layer is doing. */
    default String describe() {
        return "";
    }
}
