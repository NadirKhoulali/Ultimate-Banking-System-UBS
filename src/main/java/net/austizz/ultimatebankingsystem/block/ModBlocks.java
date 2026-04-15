package net.austizz.ultimatebankingsystem.block;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.custom.ATMBlock;
import net.austizz.ultimatebankingsystem.block.custom.BankOwnerPcBlock;
import net.austizz.ultimatebankingsystem.block.custom.ColorButtonBlock;
import net.austizz.ultimatebankingsystem.block.custom.ShopTerminalBlock;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UltimateBankingSystem.MODID);

    public static final RegistryObject<Block> ATM_MACHINE = registerBlock("atm_machine",
            () -> new ATMBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(4f)
                    .sound(SoundType.METAL)


            ));
    public static final RegistryObject<Block> BANK_OWNER_PC = registerBlock("bank_owner_pc",
            () -> new BankOwnerPcBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(3.5f)
                    .sound(SoundType.METAL)
            ));
    public static final RegistryObject<Block> COLOR_BUTTON_BLOCK = registerBlock("color_button_block",
            () -> new ColorButtonBlock(BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .sound(SoundType.STONE)


            ));
    public static final RegistryObject<Block> PAYMENT_TERMINAL = registerBlock("payment_terminal",
            () -> new ShopTerminalBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(3.0f)
                    .sound(SoundType.METAL)
            ));


    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static  <T extends Block> void registerBlockItem (String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register (IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
