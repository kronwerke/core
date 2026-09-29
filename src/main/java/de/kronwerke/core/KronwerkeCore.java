package de.kronwerke.core;

import com.mojang.logging.LogUtils;
import de.kronwerke.core.config.KronwerkeConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(KronwerkeCore.MOD_ID)
public class KronwerkeCore {
    public static final String MOD_ID = "kronwerke";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KronwerkeCore(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, KronwerkeConfig.SPEC);
    }
}
