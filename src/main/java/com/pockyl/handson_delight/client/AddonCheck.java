package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.Cutting;
import com.pockyl.handson_delight.station.StoveStation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Farmer's Delight's addons are the mod's own stations, and nothing of theirs is written here: their stoves are in the mod's tag of
 * stoves, their cutting and cooking recipes go through the same recipe manager, their dishes are read from their own files. Whether
 * that holds for a given addon is a thing to see, not to assume: with {@code -Dhandson.addontest} the client says once, in the log,
 * what of an addon the board would cut, what of it the board would put together, what of it the pot would cook, and whether the
 * addon's stove opens as the stove station.
 */
public final class AddonCheck {
    /** The addons this check is about: the two that build on Farmer's Delight's stove. */
    private static final String[][] STOVES = {{"farmersdelight", "stove"}, {"mynethersdelight", "nether_stove"},
            {"ends_delight", "end_stove"}};

    private static boolean done;

    private AddonCheck() {
    }

    static void install() {
        if (Boolean.getBoolean("handson.addontest")) {
            ClientTickEvents.END_CLIENT_TICK.register(AddonCheck::tick);
        }
    }

    private static void tick(Minecraft minecraft) {
        if (done || minecraft.level == null || minecraft.player == null || minecraft.gui.getGuiTicks() < 140) {
            return;
        }
        done = true;
        try {
            report(minecraft.level);
        } catch (RuntimeException broken) {
            HandsOnDelight.LOGGER.warn("addon check could not finish", broken);
        }
    }

    private static void report(Level level) {
        for (String[] entry : STOVES) {
            Identifier id = Identifier.fromNamespaceAndPath(entry[0], entry[1]);
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null || !id.equals(BuiltInRegistries.BLOCK.getKey(block))) {
                HandsOnDelight.LOGGER.info("addon check: {} is not here", id);
                continue;
            }
            HandsOnDelight.LOGGER.info("addon check: {} opens as the stove station: {}", id,
                    StoveStation.TYPE.matches(block.defaultBlockState()));
        }
        HandsOnDelight.LOGGER.info("addon check: the board cuts {} recipes in all, of them {}", Cutting.everything(level).size(),
                counts(level, false));
        HandsOnDelight.LOGGER.info("addon check: the board puts together {} recipes in all, of them {}", AssemblyRecipes.all().size(),
                counts(level, true));
        HandsOnDelight.LOGGER.info("addon check: the pot cooks {} recipes, of them {}", Platform.INSTANCE.cookingRecipes(level).size(),
                cooked(level));
        // The stove's and the skillet's books are built from the campfire recipes the client got: what the fire cooks is what the
        // grill takes, minus what the mod keeps off it (eggs). Nothing else feeds them, so these two numbers are the books.
        var fireRecipes = ((net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess) level.recipeAccess()).getSynchronizedRecipes()
                .getAllOfType(net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING);
        int grill = 0;
        for (RecipeHolder<net.minecraft.world.item.crafting.CampfireCookingRecipe> recipe : fireRecipes) {
            if (!com.pockyl.handson_delight.station.StoveCooking.grillable(recipe.value().input()).isEmpty()) {
                grill++;
            }
        }
        HandsOnDelight.LOGGER.info("addon check: the fire cooks {} recipes, {} of them on the stove's grill", fireRecipes.size(),
                grill);
    }

    /** How many items of each addon the board would cut, in one line: {@code ends_delight 12, mynethersdelight 21}. */
    private static String counts(Level level, boolean assembled) {
        Map<String, Integer> per = new LinkedHashMap<>();
        if (!assembled) {
            for (Item item : BuiltInRegistries.ITEM) {
                String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                if (isAddon(namespace) && !Cutting.recipesFor(level, new ItemStack(item)).isEmpty()) {
                    per.merge(namespace, 1, Integer::sum);
                }
            }
        } else {
            for (AssemblyRecipes.Assembly assembly : AssemblyRecipes.all()) {
                String namespace = assembly.id().getNamespace();
                if (isAddon(namespace)) {
                    per.merge(namespace, 1, Integer::sum);
                }
            }
        }
        return line(per);
    }

    /** How many of the pot's recipes came with the addons. */
    private static String cooked(Level level) {
        Map<String, Integer> per = new LinkedHashMap<>();
        for (RecipeHolder<CookingPotRecipe> recipe : Platform.INSTANCE.cookingRecipes(level)) {
            String namespace = recipe.id().identifier().getNamespace();
            if (isAddon(namespace)) {
                per.merge(namespace, 1, Integer::sum);
            }
        }
        return line(per);
    }

    private static String line(Map<String, Integer> per) {
        if (per.isEmpty()) {
            return "nothing";
        }
        StringBuilder text = new StringBuilder();
        per.forEach((namespace, count) -> text.append(text.isEmpty() ? "" : ", ").append(namespace).append(' ').append(count));
        return text.toString();
    }

    /** Whether {@code namespace} is a mod that builds on Farmer's Delight (its own stoves, its own food). */
    private static boolean isAddon(String namespace) {
        return !namespace.equals("minecraft") && Platform.INSTANCE.isFarmersDelightAddon(namespace);
    }
}
