package de.kronwerke.core.obelisk;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The blocks and items Core adds: the obelisk and its parts, and the intake. */
public final class ObeliskBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(KronwerkeCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KronwerkeCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, KronwerkeCore.MOD_ID);

    private static BlockBehaviour.Properties stone(int light) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .sound(SoundType.DEEPSLATE)
                .strength(-1.0f, 3600000.0f)
                .lightLevel(s -> light)
                .noOcclusion()
                .noLootTable()
                .isValidSpawn((s, l, p, t) -> false)
                .isViewBlocking((s, l, p) -> false);
    }

    public static final DeferredBlock<ObeliskBlock> OBELISK = BLOCKS.register("obelisk", () -> new ObeliskBlock(stone(11)));

    public static final DeferredBlock<ObeliskPartBlock> OBELISK_SHAFT = BLOCKS.register("obelisk_shaft",
            () -> new ObeliskPartBlock(stone(9), Block.box(2, 0, 2, 14, 16, 14)));

    public static final DeferredBlock<ObeliskPartBlock> OBELISK_PLINTH = BLOCKS.register("obelisk_plinth",
            () -> new ObeliskPartBlock(stone(0).sound(SoundType.DEEPSLATE_BRICKS), Shapes.block()));

    public static final DeferredBlock<ObeliskTopBlock> OBELISK_TOP = BLOCKS.register("obelisk_top",
            () -> new ObeliskTopBlock(stone(15).sound(SoundType.AMETHYST)));

    public static final DeferredBlock<ObeliskIntakeBlock> OBELISK_INTAKE = BLOCKS.register("obelisk_intake",
            () -> new ObeliskIntakeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).sound(SoundType.DEEPSLATE).strength(2.0f, 10.0f).noOcclusion()));

    public static final DeferredItem<Item> OBELISK_ITEM = ITEMS.register("obelisk",
            () -> new BlockItem(OBELISK.get(), new Item.Properties().fireResistant()));
    public static final DeferredItem<Item> OBELISK_PLINTH_ITEM = ITEMS.register("obelisk_plinth",
            () -> new BlockItem(OBELISK_PLINTH.get(), new Item.Properties().fireResistant()));
    public static final DeferredItem<Item> OBELISK_INTAKE_ITEM = ITEMS.register("obelisk_intake",
            () -> new BlockItem(OBELISK_INTAKE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ObeliskTopBlockEntity>> OBELISK_TOP_ENTITY = ENTITIES.register("obelisk_top",
            () -> BlockEntityType.Builder.of(ObeliskTopBlockEntity::new, OBELISK_TOP.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ObeliskIntakeBlockEntity>> OBELISK_INTAKE_ENTITY = ENTITIES.register("obelisk_intake",
            () -> BlockEntityType.Builder.of(ObeliskIntakeBlockEntity::new, OBELISK_INTAKE.get()).build(null));

    private ObeliskBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(ObeliskBlocks::onCreativeTabs);
        modBus.addListener(ObeliskBlocks::onCapabilities);
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(OBELISK_ITEM.get());
            event.accept(OBELISK_PLINTH_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(OBELISK_INTAKE_ITEM.get());
        }
    }

    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, OBELISK_INTAKE_ENTITY.get(), (be, side) -> be.handler);
    }
}
