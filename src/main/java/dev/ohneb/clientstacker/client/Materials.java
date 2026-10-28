package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.Conversion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

public final class Materials {
    public static final Conversion EMERALDS = pair("emerald_block", "emerald");
    public static final List<Conversion> ALL = List.of(
        EMERALDS, pair("iron_block", "iron_ingot"), pair("gold_block", "gold_ingot"),
        pair("diamond_block", "diamond"), pair("copper_block", "copper_ingot"),
        pair("lapis_block", "lapis_lazuli"), pair("redstone_block", "redstone"),
        pair("coal_block", "coal"), pair("hay_block", "wheat"), pair("bone_block", "bone_meal"),
        pair("slime_block", "slime_ball"), pair("raw_iron_block", "raw_iron"),
        pair("raw_gold_block", "raw_gold"), pair("raw_copper_block", "raw_copper"),
        pair("netherite_block", "netherite_ingot"), pair("iron_ingot", "iron_nugget"),
        pair("gold_ingot", "gold_nugget")
    );

    private Materials() {}

    public static List<ItemStack> costs(MerchantOffer offer) {
        ItemStack first = offer.getCostA();
        ItemStack second = offer.getCostB();
        if (!second.isEmpty() && ItemStack.isSameItemSameComponents(first, second)) {
            return List.of(first.copyWithCount(first.getCount() + second.getCount()));
        }
        return List.of(first, second);
    }

    private static Conversion pair(String compact, String loose) {
        return new Conversion("minecraft:" + compact, "minecraft:" + loose, 9);
    }

    public static Item item(String id) {
        //? if >1.21.1 {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        //?} else {
        /*return BuiltInRegistries.ITEM.get(Identifier.parse(id));
        *///?}
    }

    public static String id(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static boolean plain(ItemStack stack) {
        return stack.isEmpty() || stack.getComponentsPatch().isEmpty();
    }

    public static boolean enabled(Conversion conversion) {
        return !conversion.compact().equals("minecraft:netherite_block") || ClientStacker.config().allowNetherite;
    }

    public static Conversion forStack(ItemStack stack, boolean compact) {
        if (!plain(stack)) return null;
        String id = id(stack);
        return ALL.stream().filter(Materials::enabled)
            .filter(pair -> (compact ? pair.loose() : pair.compact()).equals(id)).findFirst().orElse(null);
    }

    public static Conversion forTrade(ItemStack stack) {
        if (!plain(stack)) return null;
        return ALL.stream().filter(Materials::enabled)
            .filter(pair -> pair.loose().equals(id(stack)))
            .filter(pair -> pair.equals(EMERALDS) ? ClientStacker.config().emeraldTrading
                : ClientStacker.config().sellingMaterials.contains(pair.loose()))
            .findFirst().orElse(null);
    }
}
