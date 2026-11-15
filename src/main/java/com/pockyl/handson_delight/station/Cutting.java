package com.pockyl.handson_delight.station;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CakeBlock;
import vectorwing.farmersdelight.common.block.PieBlock;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.KitchenTags;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Farmer's Delight's cutting recipes, read for the board's station: what an item is cut with and into how many strokes. */
public final class Cutting {
    /**
     * The most marks an item gets, one for each piece it falls into up to a limit: a cake into seven is
     * cut three times, never more.
     */
    public static final int MOST_MARKS = 3;
    /** Strips of pasta: this many cuts side by side, whatever comes out. */
    private static final int STRIPS = 3;
    /**
     * Per recipe manager: the client's and the server's of a single-player game are two. Read again when the manager's map of
     * recipes is a new one (a reload, a server's recipes): looked up every frame at the board, so never built anew in between.
     */
    private static final Map<Object, Cached> CACHE = new WeakHashMap<>();

    private Cutting() {
    }

    /** Every cutting recipe, Farmer's Delight's and its addons', in the order the recipe manager gives them. */
    public static List<CuttingBoardRecipe> everything(Level level) {
        return cached(level).everything();
    }

    /**
     * Every cutting recipe, the board's station: cooking first (see {@link #isCooking}), then the rest (a log stripped with an axe, a
     * flower cut into dye, leather armour cut into leather, an addon's moulds). A board that took only food left players with a leather
     * armour lying on it and no station, and every cut goes through it now; the book keeps the kitchen in front.
     */
    public static List<CuttingBoardRecipe> all(Level level) {
        return cached(level).ordered();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Cached cached(Level level) {
        // On a client the recipe access is a ClientRecipeContainer, not a RecipeManager: ask Fabric for the synced list.
        net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes synced =
                ((net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess) level.recipeAccess()).getSynchronizedRecipes();
        Map<?, ?> recipes = new java.util.HashMap<>();
        synchronized (CACHE) {
            Cached cached = CACHE.get(level.recipeAccess());
            if (cached == null || cached.from() != recipes) {
                List<CuttingBoardRecipe> everything = new ArrayList<>();
                Map<CuttingBoardRecipe, Identifier> ids = new IdentityHashMap<>();
                List<RecipeHolder<?>> found = new ArrayList<>(synced.recipes());
                for (RecipeHolder<?> holder : found) {
                    if (holder.value() instanceof CuttingBoardRecipe cutting && !cutting.getInput().isEmpty()) {
                        everything.add(cutting);
                        ids.put(cutting, holder.id().identifier());
                    }
                }
                List<CookingPotRecipe> pot = Platform.INSTANCE.cookingRecipes(level).stream().map(RecipeHolder::value).toList();
                List<CuttingBoardRecipe> ordered = new ArrayList<>(everything.stream().filter(recipe -> isCooking(recipe, pot)).toList());
                everything.stream().filter(recipe -> !ordered.contains(recipe)).forEach(ordered::add);
                cached = new Cached(recipes, List.copyOf(everything), List.copyOf(ordered), ids);
                CACHE.put(level.recipeAccess(), cached);
            }
            return cached;
        }
    }

    /**
     * Whether {@code recipe} is cooking, with {@code pot} the cooking pot's recipes: food goes in or comes out, what comes out goes
     * into the pot (rice, mushrooms, pasta), or a knife cuts what the pot serves its meals on (Fright's Delight's bone into the skewers
     * of its kebabs). Cooking comes first in the board's book.
     */
    public static boolean isCooking(CuttingBoardRecipe recipe, List<CookingPotRecipe> pot) {
        for (net.minecraft.core.Holder<net.minecraft.world.item.Item> choice : recipe.getInput().items().toList()) {
            ItemStack input = new ItemStack(choice);
            if (input.has(DataComponents.FOOD)) {
                return true;
            }
        }
        List<ItemStack> results = recipe.getResults().stream()
                .map(net.minecraft.world.item.ItemStackTemplate::create).toList();
        for (ItemStack result : results) {
            if (result.has(DataComponents.FOOD) || pot.stream().anyMatch(cooking -> cooking.input().stream().anyMatch(in -> in.test(result)))) {
                return true;
            }
        }
        // A knife that cuts something into nothing but what the pot serves on makes skewers. A bush cut into sticks with an axe, a
        // basket taken apart into canvas and a stick, a plant cut into a stick and gunpowder are no cooking, though a stick holds a
        // marshmallow from the pot.
        ItemStack knife = BuiltInRegistries.ITEM.getOptional(FarmersDelightContent.id("iron_knife"))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        return !results.isEmpty() && tool(recipe).test(knife) && results.stream().allMatch(result -> pot.stream()
                .map(Cutting::container).anyMatch(container -> !container.isEmpty() && container.is(result.getItem())));
    }

    /** The dish's container, or empty when the recipe has none. */
    private static ItemStack container(CookingPotRecipe recipe) {
        net.minecraft.world.item.ItemStackTemplate template = recipe.container();
        return template == null ? ItemStack.EMPTY : template.create();
    }

    /** The cutting recipes read from a recipe manager's map of recipes, as long as the map is the same one. */
    private record Cached(Map<?, ?> from, List<CuttingBoardRecipe> everything, List<CuttingBoardRecipe> ordered,
                          Map<CuttingBoardRecipe, Identifier> ids) {
    }

    /** The id {@code recipe} is known by, now that a recipe holds none of its own. */
    public static Identifier id(Level level, CuttingBoardRecipe recipe) {
        return cached(level).ids().get(recipe);
    }

    public static List<CuttingBoardRecipe> recipesFor(Level level, ItemStack item) {
        List<CuttingBoardRecipe> found = new ArrayList<>();
        if (!item.isEmpty()) {
            all(level).stream().filter(recipe -> recipe.getInput().test(item)).forEach(found::add);
        }
        return found;
    }

    /** How the marks of an item lie on it. */
    public enum Style {
        /** Across its length, a little askew each time: meat, fish, a cabbage. */
        ACROSS,
        /** Square across its length, in even slices: a roll. */
        STRAIGHT,
        /** Through its middle, crossing each other: a pie, a cake. */
        WEDGES,
        /** Square across, close side by side: pasta from dough. */
        STRIPS
    }

    /** How {@code item} is cut: in how many marks, laid how. Both sides read the same recipes and tags, so they agree. */
    public record Plan(int marks, Style style) {
    }

    public static Plan plan(Level level, ItemStack item) {
        Style style = item.is(KitchenTags.CUT_IN_WEDGES) || isRound(item) ? Style.WEDGES
                : item.is(KitchenTags.CUT_IN_STRIPS) ? Style.STRIPS
                : item.is(KitchenTags.SLICED_STRAIGHT) ? Style.STRAIGHT : Style.ACROSS;
        if (style == Style.STRIPS) {
            return new Plan(STRIPS, style);
        }
        int pieces = recipesFor(level, item).stream().findFirst().map(Cutting::pieces).orElse(2);
        // n cuts across make n + 1 pieces; n cuts through the middle make 2n wedges.
        int marks = style == Style.WEDGES ? (pieces + 1) / 2 : pieces - 1;
        return new Plan(Mth.clamp(marks, 1, MOST_MARKS), style);
    }

    /** A pie or a cake put down whole, an addon's too: cut in wedges like Farmer's Delight's. */
    private static boolean isRound(ItemStack item) {
        return item.getItem() instanceof BlockItem block && (block.getBlock() instanceof PieBlock || block.getBlock() instanceof CakeBlock);
    }

    /** What an item falls into: the main result as many as it gives, and one piece for each thing besides (the bone of a ham). */
    static int pieces(CuttingBoardRecipe recipe) {
        List<ItemStack> results = recipe.getResults().stream()
                .map(net.minecraft.world.item.ItemStackTemplate::create).toList();
        if (results.isEmpty()) {
            return 2;
        }
        return results.get(0).getCount() + results.size() - 1;
    }

    /**
     * The inventory slot whose tool cuts {@code item}: the one in hand first, then the off hand, the hotbar and the rest; -1 without
     * one.
     */
    public static int toolSlot(Level level, ItemStack item, Inventory inventory) {
        if (findWith(level, item, inventory.getSelectedItem()).isPresent()) {
            return inventory.getSelectedSlot();
        }
        if (findWith(level, item, inventory.getItem(Inventory.SLOT_OFFHAND)).isPresent()) {
            return Inventory.SLOT_OFFHAND;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (findWith(level, item, inventory.getItem(slot)).isPresent()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Whether {@code item} can be stuck into the board, as Farmer's Delight's own sneaking use does it: a tool with a tier (a knife,
     * a sword, an axe), a trident or shears.
     */
    public static boolean canStick(ItemStack item) {
        return item.has(net.minecraft.core.component.DataComponents.TOOL) || item.is(net.minecraft.tags.ItemTags.SWORDS)
                || item.getItem() instanceof net.minecraft.world.item.TridentItem || item.getItem() instanceof net.minecraft.world.item.ShearsItem;
    }

    /** Whether {@code item} is what some cutting recipe cuts with: a knife, an axe, an addon's rolling pin. */
    public static boolean isTool(Level level, ItemStack item) {
        return !item.isEmpty() && everything(level).stream().anyMatch(recipe -> tool(recipe).test(item));
    }

    /**
     * What {@code recipe} cuts with, to test a tool by and to show: through {@link Platform#unwrap}, as an addon's tool action on
     * Refabricated throws when it is tested as Fabric wraps it.
     */
    public static Ingredient tool(CuttingBoardRecipe recipe) {
        return Platform.INSTANCE.unwrap(recipe.getTool());
    }

    public static Optional<CuttingBoardRecipe> findWith(Level level, ItemStack item, ItemStack tool) {
        return tool.isEmpty() ? Optional.empty() : recipesFor(level, item).stream().filter(recipe -> tool(recipe).test(tool)).findFirst();
    }
}
