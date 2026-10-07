package de.kronwerke.core.portal;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The way into the mining world: the Grubenrahmen and the portal it holds. */
public final class PortalBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(KronwerkeCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KronwerkeCore.MOD_ID);

    /** The frame: dark stone bound in iron, blue veins that glow a little. */
    public static final DeferredBlock<Block> FRAME = BLOCKS.register("grubenrahmen", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.DEEPSLATE).sound(SoundType.DEEPSLATE_BRICKS).strength(5.0f, 1200.0f)
            .requiresCorrectToolForDrops().lightLevel(s -> 4)));

    public static final DeferredBlock<MinePortalBlock> PORTAL = BLOCKS.register("minenportal", () -> new MinePortalBlock(BlockBehaviour.Properties.of()
            .noCollission().strength(-1.0f).sound(SoundType.GLASS).lightLevel(s -> 11).noLootTable()
            .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false)));

    public static final DeferredItem<Item> FRAME_ITEM = ITEMS.register("grubenrahmen", () -> new BlockItem(FRAME.get(), new Item.Properties()));

    private PortalBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) e.accept(FRAME_ITEM.get());
        });
    }
}
