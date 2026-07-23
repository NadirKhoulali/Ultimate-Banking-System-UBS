package net.austizz.ultimatebankingsystem.item;

import net.minecraft.world.item.Item;

/** A handheld two-person-tool-sized saw. It is deliberately not a BlockItem. */
public final class Ove9000SawItem extends Item {
    public Ove9000SawItem() {
        super(new Item.Properties().stacksTo(1).durability(100));
    }
}
