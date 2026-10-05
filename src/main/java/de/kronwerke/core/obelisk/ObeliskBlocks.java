package de.kronwerke.core.obelisk;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The blocks and items Core adds. Only the obelisk so far. */
public final class ObeliskBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(KronwerkeCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KronwerkeCore.MOD_ID);

    public static final DeferredBlock<ObeliskBlock> OBELISK = BLOCKS.register("obelisk", () -> new ObeliskBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .sound(SoundType.DEEPSLATE)
                    .strength(-1.0f, 3600000.0f)
                    .lightLevel(s -> 11)
                    .noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final DeferredBlock<ObeliskTopBlock> OBELISK_TOP = BLOCKS.register("obelisk_top", () -> new ObeliskTopBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .sound(SoundType.AMETHYST)
                    .strength(-1.0f, 3600000.0f)
                    .lightLevel(s -> 13)
                    .noOcclusion()
                    .noLootTable()
                    .isValidSpawn((s, l, p, t) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final DeferredItem<Item> OBELISK_ITEM = ITEMS.register("obelisk",
            () -> new BlockItem(OBELISK.get(), new Item.Properties().fireResistant()));

    private ObeliskBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ObeliskBlocks::onCreativeTabs);
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(OBELISK_ITEM.get());
        }
    }
}
