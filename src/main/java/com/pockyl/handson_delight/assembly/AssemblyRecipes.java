package com.pockyl.handson_delight.assembly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import vectorwing.farmersdelight.common.block.FeastBlock;
import vectorwing.farmersdelight.common.block.PieBlock;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.KitchenTags;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Food that Farmer's Delight and its addons make in the crafting table and that is put together on the cutting board instead. The
 * recipes are found when the server loads them and kept here as they were, with the same ingredients and result, so that an addon's
 * dish needs nothing from us. They stay among the server's recipes for automation (Create's crafters, AE2, Refined Storage): only
 * a player's own crafting grid does not make them, and no player learns them for the recipe book.
 *
 * <p>The rule: a crafting recipe of Farmer's Delight or of a mod that needs it, made of more than one kind of thing, whose result is
 * food, a drink made of food, a feast, a pie or a cake. Slices back into a whole pie, crates opened and leaves pressed back into a
 * cabbage are one kind of thing and stay in the crafting table. Item tags {@link #ASSEMBLED} and {@link #NOT_ASSEMBLED} on the
 * result correct the rule. {@link #ASSEMBLED} also brings the crafts of any other mod, datapack or script to the board: a modpack
 * tags the result and needs nothing else.
 */
public final class AssemblyRecipes {
    /**
     * Results whose recipes are put together on the board although the rule would leave them in the crafting table: also the plain
     * shaped and shapeless crafts of other mods, datapacks and scripts, whatever their namespace.
     */
    public static final TagKey<Item> ASSEMBLED = TagKey.create(Registries.ITEM, HandsOnDelight.id("assembled"));
    /** Results whose recipes stay in the crafting table although the rule would take them. */
    public static final TagKey<Item> NOT_ASSEMBLED = TagKey.create(Registries.ITEM, HandsOnDelight.id("not_assembled"));
    /**
     * Materials of a workshop, not of a kitchen: a recipe with one of them stays in the crafting table, as a golden carrot does (an
     * addon's golden egg, a copper carrot, a dragon egg of obsidian).
     */
    public static final TagKey<Item> WORKSHOP = TagKey.create(Registries.ITEM, HandsOnDelight.id("workshop"));

    /** Farmer's Delight's dough from water is a recipe type of its own, with nothing to read: the board knows what it takes. */
    private static final String DOUGH = FarmersDelightContent.id("dough").toString();
    private static final String SHAPELESS = "minecraft:crafting_shapeless";
    private static final String SHAPED = "minecraft:crafting_shaped";
    private static final int GRID = 3;

    /** Every recipe that may be put together, as the recipe manager loaded it; the tags choose among them once they are bound. */
    /**
     * Every recipe that may be put together, as the pack wrote it: read when the tags are bound (while the recipes load they are
     * not, and a recipe's ingredients are tags).
     */
    private static volatile Map<Identifier, JsonObject> candidates = Map.of();
    /**
     * The shaped and shapeless crafts the rule does not look at, also those of a type built on them (KubeJS writes its own), as the
     * recipe manager loaded them: those whose result is tagged {@link #ASSEMBLED} are put together too, once the tags are bound.
     */
    private static volatile Map<Identifier, JsonObject> others = Map.of();
    /** The registries the recipes were loaded with, to read {@link #others}. */
    private static volatile HolderLookup.Provider othersRegistries;
    private static volatile Chosen chosen;

    private AssemblyRecipes() {
    }

    /** How a dish is finished once its layers make it: what the cook's hands would do. */
    public enum Finish {
        /** Pressed together from the top: a pie into its crust, a juice, a cake. */
        PRESS,
        /** Tossed in the bowl: a salad. */
        TOSS,
        /** Rolled up: a kelp roll, a wrap round its filling. */
        ROLL,
        /** Pushed along the stick, from its tip down. */
        SKEWER,
        /** Kneaded. */
        KNEAD,
        /** Laid in the bowl, each piece in its place: a plated meal, a feast. */
        ARRANGE,
        /** The bread is split and its top half closes the filling: a sandwich, a burger. */
        SANDWICH,
        /** Rice pressed into a block, the slices laid along its top: nigiri. */
        SHAPE,
        /** Fruit frozen together round the top of the stick: a popsicle. */
        FREEZE
    }

    /**
     * What goes down first, found by name among the ingredients in this order: it says what kind of dish it is. A recipe with none
     * of them starts with its first ingredient. Wheat, its dough or flour is kneaded (a cake an addon makes from dough).
     */
    public enum Base {
        BOWL("bowl", Finish.TOSS),
        BOTTLE("glass_bottle", Finish.PRESS),
        /** Bread, or a burger already made that gets more on top: its bread opens and closes again. */
        BREAD(Finish.PRESS, "bread", "burger"),
        /** A tortilla or a flatbread: what lies on it is rolled up in it, before rice or anything else in the recipe counts as the base. */
        TORTILLA(Finish.ROLL, "tortilla", "flatbread"),
        /** Toast, with what is spread on it. */
        TOAST("toast", Finish.PRESS),
        CRUST("pie_crust", Finish.PRESS),
        /** A stick, or a blaze rod for a Nether skewer; packs often name sticks by their tag, wooden rods. */
        STICK(Finish.SKEWER, "stick", "rods/wooden", "blaze_rod"),
        KELP("kelp", Finish.ROLL),
        RICE("rice", Finish.ROLL),
        WHEAT(Finish.KNEAD, "wheat", "dough", "flour"),
        FIRST(null, Finish.PRESS);

        private final String[] words;
        private final Finish finish;

        Base(String word, Finish finish) {
            this(finish, word == null ? new String[0] : new String[] {word});
        }

        Base(Finish finish, String... words) {
            this.words = words;
            this.finish = finish;
        }

        public Finish finish() {
            return finish;
        }
    }

    /**
     * A dish put together on the board: everything lies on ingredient {@code base}, which goes down first. {@code ingredients} are the
     * recipe's own, without the empty places of a shaped one; {@code slots}: where each lies in a 3 x 3 crafting grid, so the recipe
     * itself can make the dish and say what is left (a bucket, a bottle). {@code json}: the recipe as the clients read it.
     */
    public record Assembly(Identifier id, CraftingRecipe recipe, List<Ingredient> ingredients, int[] slots, int base, Base kind,
            ItemStackTemplate result, JsonObject json) {
        /**
         * The dish itself. Recipes are read while the game's items are not yet their own (26.1 keeps a template until the item
         * components are bound), so the stack is made where the board asks for it.
         */
        public ItemStack stack() {
            return result.create();
        }

        /** The item the dish is made of. */
        public Item item() {
            return result.item().value();
        }

        /**
         * What the cook's hands do: a bowl of plants is tossed and anything else in a bowl arranged; a pie or a cake is pressed
         * whatever goes down first; a stick with ice freezes, bread closes over the filling or is rolled round it, rice is shaped, or
         * topped where its dish is tagged arranged (bibimbap).
         */
        public Finish finish() {
            if (kind == Base.BOWL) {
                return isTossed() ? Finish.TOSS : Finish.ARRANGE;
            }
            if (isBlock()) {
                return Finish.PRESS;
            }
            if (BuiltInRegistries.ITEM.getKey(item()).getPath().endsWith("dough")) {
                return Finish.KNEAD;
            }
            return switch (kind) {
                case STICK -> stack().is(KitchenTags.FROZEN) || ingredients.stream().anyMatch(in -> in.test(new ItemStack(Items.ICE)))
                        ? Finish.FREEZE : Finish.SKEWER;
                case BREAD -> isWrap() ? Finish.ROLL : isClosed() ? Finish.PRESS : Finish.SANDWICH;
                case RICE -> stack().is(KitchenTags.ARRANGED) ? Finish.ARRANGE : Finish.SHAPE;
                default -> kind.finish();
            };
        }

        /** Bread laid above the filling too, as Modest Meals' sandwiches have it: there is no loaf to split, the top is pressed on. */
        private boolean isClosed() {
            for (int i = 0; i < ingredients.size(); i++) {
                if (i != base && ingredients.get(i).equals(ingredients.get(base))) {
                    return true;
                }
            }
            return false;
        }

        /** Whether the dish has a page in the book and in recipe viewers: Modest Meals' meals of free fillings have none. */
        public boolean inBook() {
            return !json.has(ModestMeals.HIDDEN);
        }

        /** Bread rolled round its filling: tagged so, or named a wrap or a burrito, as addons name theirs. */
        private boolean isWrap() {
            String name = BuiltInRegistries.ITEM.getKey(item()).getPath();
            return stack().is(KitchenTags.WRAPPED) || name.contains("wrap") || name.contains("burrito");
        }

        /** A salad: tagged so, or, untagged, a bowl of nothing but plants. Meat, fish and eggs are laid in their places. */
        private boolean isTossed() {
            if (stack().is(KitchenTags.TOSSED)) {
                return true;
            }
            if (stack().is(KitchenTags.ARRANGED) || isBlock()) {
                return false;
            }
            for (int i = 0; i < ingredients.size(); i++) {
                List<Holder<Item>> choices = ingredients.get(i).items().toList();
                if (i == base || choices.isEmpty()) {
                    continue;
                }
                ItemStack food = choices.get(0).value().getDefaultInstance();
                boolean meat = food.is(ItemTags.MEAT);
                String name = BuiltInRegistries.ITEM.getKey(food.getItem()).getPath();
                if (meat || food.is(ItemTags.FISHES) || food.is(KitchenTags.CRACKED) || name.contains("egg") || name.contains("bacon")) {
                    return false;
                }
            }
            return true;
        }

        /** Whether the dish is a block put down whole: a feast, a pie, a cake (not a plantable food, which is a BlockItem too). */
        public boolean isBlock() {
            return item() instanceof BlockItem block && isWholeFood(block.getBlock());
        }
    }

    /** The recipes chosen among the candidates, and the list the clients get. */
    private record Chosen(List<Assembly> all, Set<Identifier> ids, ListTag book) {
    }

    /** Every recipe that may be put together, before the tags choose: for the recipe audit. */
    public static List<Assembly> candidates() {
        return read(candidates);
    }

    public static List<Assembly> all() {
        return chosen().all();
    }

    /** Every recipe as its id and JSON, for {@link #read}. */
    public static ListTag book() {
        return chosen().book();
    }

    /**
     * Whether the recipe {@code id} is made on the board: a player's crafting grid does not make it, shaped, shapeless or of a type
     * of its own ({@code CraftingMenuMixin}).
     */
    public static boolean isMadeOnTheBoard(Identifier id) {
        return chosen().ids().contains(id);
    }

    /**
     * Chosen when first asked after the recipes or the tags changed: while the recipes load, the item tags are not bound yet. The
     * server asks first when a player joins, or when it sends the reloaded recipes, after the tags.
     */
    private static Chosen chosen() {
        Chosen now = chosen;
        if (now == null) {
            synchronized (AssemblyRecipes.class) {
                now = chosen;
                if (now == null) {
                    List<Assembly> from = read(candidates);
                    from.addAll(tagged());
                    now = choose(from);
                    chosen = now;
                }
            }
        }
        return now;
    }

    /** The tags may have changed: the recipes are chosen again when next asked. */
    public static void tagsChanged() {
        chosen = null;
    }

    private static Chosen choose(List<Assembly> from) {
        List<Assembly> all = new ArrayList<>();
        ListTag book = new ListTag();
        Set<Identifier> ids = new HashSet<>();
        // Modest Meals makes its meals of whatever it is given: the board writes them itself, and the crafting grid no longer makes them.
        List<Identifier> mealIngredients = ModestMeals.ingredients();
        List<Assembly> meals = ModestMeals.dishes(mealIngredients);
        if (!meals.isEmpty()) {
            book.add(ModestMeals.entry(mealIngredients));
            ModestMeals.recipeId().ifPresent(ids::add);
        }
        for (Assembly meal : meals) {
            if (!meal.stack().is(NOT_ASSEMBLED)) {
                keep(meal, all, book);
            }
        }
        for (Assembly assembly : from) {
            ItemStack result = assembly.stack();
            boolean taken = result.is(ASSEMBLED) || !result.is(NOT_ASSEMBLED) && !usesWorkshop(assembly.ingredients())
                    && (isDishResult(result) || isCutIntoFood(result) || isDrinkOfFood(assembly));
            if (taken) {
                keep(assembly, all, book);
            }
        }
        all.forEach(assembly -> ids.add(assembly.id()));
        return new Chosen(List.copyOf(all), Set.copyOf(ids), book);
    }

    private static void keep(Assembly assembly, List<Assembly> all, ListTag book) {
        all.add(assembly);
        CompoundTag entry = new CompoundTag();
        entry.putString("id", assembly.id().toString());
        entry.putString("json", assembly.json().toString());
        book.add(entry);
    }

    /**
     * Food, or a block of food put down whole. A pie may be no block item and no food of its own: Delightful makes the vanilla pumpkin
     * pie a pie block that is put down and eaten in slices.
     */
    public static boolean isDishResult(ItemStack result) {
        if (result.has(DataComponents.FOOD)) {
            return true;
        }
        if (result.getItem() instanceof BlockItem item && isWholeFood(item.getBlock())) {
            return true;
        }
        Set<Item> placed = wholeFoodItems;
        if (placed == null) {
            placed = BuiltInRegistries.BLOCK.stream().filter(AssemblyRecipes::isWholeFood).map(Block::asItem).collect(Collectors.toSet());
            wholeFoodItems = placed;
        }
        return placed.contains(result.getItem());
    }

    /** The items that put down a block of whole food, once the blocks are all registered. */
    private static volatile Set<Item> wholeFoodItems;
    /** The cutting board's recipes the server loaded with the dishes. */
    private static volatile List<CuttingBoardRecipe> cutting = List.of();

    /**
     * A whole dish the board cuts into food: a pie no one eats whole. Farmer's Delight puts the vanilla pumpkin pie down through an item of
     * its own, and Delightful leaves the vanilla one no food: only its slices are.
     */
    public static boolean isCutIntoFood(ItemStack result) {
        for (CuttingBoardRecipe recipe : cutting) {
            if (!recipe.getInput().isEmpty() && recipe.getInput().test(result)
                    && recipe.getResults().stream().anyMatch(item -> item.get(DataComponents.FOOD) != null)) {
                return true;
            }
        }
        return false;
    }

    /** Whether some ingredient can only be a workshop's material. */
    public static boolean usesWorkshop(List<Ingredient> ingredients) {
        return ingredients.stream().anyMatch(ingredient -> {
            List<Holder<Item>> choices = ingredient.items().toList();
            return !choices.isEmpty() && choices.stream().allMatch(item -> item.is(WORKSHOP));
        });
    }

    /** A drink made of food (melon juice); milk poured from a bucket into bottles is no dish. */
    private static boolean isDrinkOfFood(Assembly assembly) {
        return assembly.stack().getUseAnimation() == ItemUseAnimation.DRINK && assembly.ingredients().stream()
                .anyMatch(ingredient -> ingredient.items().anyMatch(item -> item.value().components().has(DataComponents.FOOD)));
    }

    private static boolean isWholeFood(Block block) {
        return block instanceof FeastBlock || block instanceof PieBlock || block instanceof CakeBlock;
    }

    /**
     * The recipes {@link #book} lists, read where they are needed: on a client its tags are bound and its items known. Their
     * ingredients and results name items and tags only, which the built-in registries know.
     */
    public static List<Assembly> read(ListTag list) {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        List<Assembly> read = new ArrayList<>();
        JsonArray any = null;
        for (int i = 0; i < list.size() && any == null; i++) {
            List<Identifier> items = ModestMeals.read(list.getCompoundOrEmpty(i));
            any = items == null ? null : ModestMeals.choices(items);
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            if (ModestMeals.read(entry) != null) {
                continue;
            }
            Identifier id = Identifier.tryParse(entry.getStringOr("id", ""));
            try {
                JsonObject json = JsonParser.parseString(entry.getStringOr("json", "")).getAsJsonObject();
                if (id != null) {
                    parse(id, json, any, registries).ifPresent(read::add);
                }
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.warn("Could not read the board's recipe {}", id, broken);
            }
        }
        return read;
    }

    /**
     * The same from the pack's files: 26.1's recipe manager reads its recipes through a codec and keeps no JSON, so the board's
     * recipes are read again from the reload's resources, and only those of Farmer's Delight and of the mods that need it. The other
     * mods' plain shaped and shapeless crafts go to {@code crafts}: their results tagged for the board are put together there too.
     */
    public static void collectFrom(ResourceManager resources, Map<Identifier, JsonObject> found, Map<Identifier, JsonObject> crafts) {
        found.clear();
        crafts.clear();
        Map<Identifier, net.minecraft.server.packs.resources.Resource> files =
                resources.listResources("recipe", path -> path.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, net.minecraft.server.packs.resources.Resource> file : files.entrySet()) {
            Identifier id = recipeId(file.getKey());
            boolean ours = id.getNamespace().equals(FarmersDelightContent.MOD_ID)
                    || Platform.INSTANCE.isFarmersDelightAddon(id.getNamespace());
            try (java.io.Reader reader = file.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject json = element.getAsJsonObject();
                if (ours) {
                    Optional<JsonObject> recipe = candidate(id, json);
                    if (recipe.isPresent()) {
                        found.put(id, recipe.get());
                        continue;
                    }
                }
                String type = string(json, "type");
                if (SHAPED.equals(type) || SHAPELESS.equals(type)) {
                    crafts.put(id, json);
                }
            } catch (RuntimeException | java.io.IOException broken) {
                // One broken recipe of an addon or datapack must not stop the reload: the game itself only logs it.
                HandsOnDelight.LOGGER.warn("Could not read the recipe {} for the cutting board", id, broken);
            }
        }
    }

    /** The recipe id of a file of the recipe folder ({@code minecraft:recipe/x/y.json} is {@code minecraft:x/y}). */
    private static Identifier recipeId(Identifier file) {
        String path = file.getPath();
        if (path.startsWith("recipe/")) {
            path = path.substring("recipe/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return Identifier.fromNamespaceAndPath(file.getNamespace(), path);
    }

    /** Reads the recipes {@link #collectFrom} found, after the recipe manager has read its own: only those it {@code loaded}. */
    public static void load(Map<Identifier, JsonObject> taken, Map<Identifier, JsonObject> crafts,
            Map<Identifier, RecipeHolder<?>> loaded, HolderLookup.Provider registries, ResourceManager resources) {
        cutting = loaded.values().stream().map(RecipeHolder::value).filter(CuttingBoardRecipe.class::isInstance).map(CuttingBoardRecipe.class::cast).toList();
        // The recipes are kept as JSON: while they load, item tags are not bound, and a recipe's ingredients are tags.
        Map<Identifier, JsonObject> mayMake = new HashMap<>();
        taken.forEach((id, json) -> {
            if (loaded.containsKey(id)) {
                mayMake.put(id, json);
            }
        });
        candidates = Map.copyOf(mayMake);
        Map<Identifier, JsonObject> kept = new HashMap<>();
        crafts.forEach((id, json) -> {
            if (!taken.containsKey(id) && loaded.containsKey(id)) {
                kept.put(id, json);
            }
        });
        others = Map.copyOf(kept);
        othersRegistries = registries;
        ModestMeals.load(resources, loaded);
        chosen = null;
        HandsOnDelight.LOGGER.info("{} crafting recipes of Farmer's Delight may be put together on the cutting board", mayMake.size());
    }

    /** The recipes {@code json} reads, for when the tags are bound: the ones the board can lay. */
    private static List<Assembly> read(Map<Identifier, JsonObject> json) {
        List<Assembly> read = new ArrayList<>();
        HolderLookup.Provider registries = othersRegistries;
        if (registries == null) {
            return read;
        }
        json.forEach((id, recipe) -> {
            try {
                parse(id, recipe, null, registries).ifPresent(read::add);
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.error("Could not read the recipe {} for the cutting board", id, broken);
            }
        });
        return read;
    }

    /** The crafts of {@link #others} whose result is tagged {@link #ASSEMBLED}, read as the board reads a recipe of its own. */
    private static List<Assembly> tagged() {
        List<Assembly> found = new ArrayList<>();
        HolderLookup.Provider registries = othersRegistries;
        for (Map.Entry<Identifier, JsonObject> craft : others.entrySet()) {
            try {
                Optional<Assembly> recipe = parse(craft.getKey(), craft.getValue(), null, registries);
                if (recipe.isEmpty() || !recipe.get().stack().is(ASSEMBLED)) {
                    continue;
                }
                found.add(recipe.get());
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.warn("Could not read the recipe {} for the cutting board", craft.getKey(), broken);
            }
        }
        return found;
    }

    /** The recipe as the board reads it, when it is one the board could make: else empty. */
    private static Optional<JsonObject> candidate(Identifier id, JsonObject json) {
        if (!id.getNamespace().equals(FarmersDelightContent.MOD_ID) && !Platform.INSTANCE.isFarmersDelightAddon(id.getNamespace())) {
            return Optional.empty();
        }
        return plain(json);
    }

    /**
     * A recipe of the crafting table or Farmer's Delight's dough, as the board reads it, when the board could make it. A crafting type
     * of an addon's own is read like the crafting table's when it is written like one: whether it lays its items as the crafting table
     * does is known once it is read ({@link #parse}).
     */
    private static Optional<JsonObject> plain(JsonObject json) {
        String type = string(json, "type");
        JsonObject recipe = json;
        if (DOUGH.equals(type)) {
            // One wheat and a water bucket, which stays: the recipe's own matches and remainders say so, the board only lays them.
            recipe = json.deepCopy();
            JsonArray ingredients = new JsonArray();
            ingredients.add(item("minecraft:wheat"));
            ingredients.add(item("minecraft:water_bucket"));
            recipe.add("ingredients", ingredients);
            JsonObject result = new JsonObject();
            result.addProperty("id", FarmersDelightContent.id("wheat_dough").toString());
            recipe.add("result", result);
        }
        List<JsonElement> named = named(recipe);
        if (named == null || named.size() < 2 || named.size() > GRID * GRID || named.stream().allMatch(named.get(0)::equals)) {
            return Optional.empty();
        }
        JsonElement result = recipe.get("result");
        String name = result == null ? null : result.isJsonObject() ? resultName(result.getAsJsonObject())
                : result.isJsonPrimitive() ? result.getAsString() : null;
        Identifier item = name == null ? null : Identifier.tryParse(name);
        return item != null && BuiltInRegistries.ITEM.containsKey(item) ? Optional.of(recipe) : Optional.empty();
    }

    /** A result names its item as {@code id}, as 1.21 does, or as {@code item}, as some addons still write it. */
    private static String resultName(JsonObject result) {
        String id = string(result, "id");
        return id != null ? id : string(result, "item");
    }

    /** The value of {@code result}, or the JSON error thrown: what {@code getOrThrow} did with a factory. */
    private static <T> T value(DataResult<T> result) {
        return result.result().orElseThrow(() -> new JsonParseException(
                result.error().map(err -> err.message()).orElse("broken recipe JSON")));
    }

    /**
     * The dish a recipe's JSON names, as a template: 26.1's recipe no longer tells its result without an input, and its items are not
     * their own while the recipes load. Empty when it names none the game knows.
     */
    private static Optional<ItemStackTemplate> result(JsonObject json, DynamicOps<JsonElement> ops) {
        JsonElement result = json.get("result");
        if (result == null) {
            return Optional.empty();
        }
        Optional<ItemStackTemplate> read = ItemStackTemplate.CODEC.parse(ops, result).result();
        if (read.isPresent() && read.get().count() > 0) {
            return read;
        }
        String name = result.isJsonObject() ? resultName(result.getAsJsonObject())
                : result.isJsonPrimitive() ? result.getAsString() : null;
        Identifier id = name == null ? null : Identifier.tryParse(name);
        Optional<net.minecraft.core.Holder.Reference<Item>> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.get(id);
        return item.map(holder -> new ItemStackTemplate(holder, 1, net.minecraft.core.component.DataComponentPatch.EMPTY));
    }

    /**
     * What {@code RecipeManager.fromJson} does, which is not public, and what the board needs of the recipe. Where {@code json} marks a
     * place for any of Modest Meals' ingredients ({@link ModestMeals#ANY}), {@code any} lists them; such a place is never the base.
     */
    static Optional<Assembly> parse(Identifier id, JsonObject json, JsonArray any, HolderLookup.Provider registries) {
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonObject full = any == null ? json : ModestMeals.expand(json, any).getAsJsonObject();
        Recipe<?> recipe = value(Recipe.CODEC.parse(ops, full));
        List<JsonElement> named = named(full);
        List<JsonElement> written = named(json);
        if (!(recipe instanceof CraftingRecipe crafting) || named == null || written == null || named.isEmpty()
                || !laysLikeACraftingTable(full, crafting)) {
            return Optional.empty();
        }
        List<Ingredient> ingredients = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        if (crafting instanceof ShapedRecipe shaped) {
            // The shaped recipe's places, its empty ones left out: where each one lies in the grid is the place's own number.
            List<Optional<Ingredient>> all = shaped.getIngredients();
            for (int i = 0; i < all.size(); i++) {
                Ingredient each = all.get(i).orElse(null);
                if (each != null && !each.isEmpty()) {
                    ingredients.add(each);
                    slots.add(i / shaped.getWidth() * GRID + i % shaped.getWidth());
                }
            }
        } else {
            // A shapeless recipe keeps its ingredients to itself, as a recipe of its own type does: the JSON names them all.
            named.forEach(ingredient -> ingredients.add(value(Ingredient.CODEC.parse(ops, ingredient))));
        }
        while (slots.size() < ingredients.size()) {
            slots.add(slots.size());
        }
        if (ingredients.size() != named.size() || ingredients.size() > GRID * GRID) {
            return Optional.empty();
        }
        ItemStackTemplate result = result(full, ops).orElse(null);
        if (result == null) {
            return Optional.empty();
        }
        int base = 0;
        Base kind = Base.FIRST;
        search:
        for (Base candidate : Base.values()) {
            for (String word : candidate.words) {
                for (int i = 0; i < written.size(); i++) {
                    if (names(written.get(i), word)) {
                        base = i;
                        kind = candidate;
                        break search;
                    }
                }
            }
        }
        return Optional.of(new Assembly(id, crafting, List.copyOf(ingredients), slots.stream().mapToInt(Integer::intValue).toArray(),
                base, kind, result, json));
    }

    /**
     * Whether the board can make {@code crafting} by laying its ingredients in a grid: the crafting table's own types and Farmer's
     * Delight's dough, or a type of an addon built on the crafting table's shaped or shapeless craft. Such a type only adds to what it
     * makes, and its own code still does that on the board, which makes the dish through the recipe: Barbeque's Delight's kebabs and
     * bibimbap carry the seasoning of their skewers. A type that matches in a way of its own stays in the crafting table.
     */
    private static boolean laysLikeACraftingTable(JsonObject json, CraftingRecipe crafting) {
        String type = string(json, "type");
        return SHAPELESS.equals(type) || SHAPED.equals(type) || DOUGH.equals(type) || crafting instanceof ShapedRecipe
                || crafting instanceof ShapelessRecipe;
    }

    /** The ingredients of a shapeless or shaped recipe's JSON, a shaped one's row by row as the recipe lists them; null if broken. */
    private static List<JsonElement> named(JsonObject json) {
        List<JsonElement> named = new ArrayList<>();
        if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            json.getAsJsonArray("ingredients").forEach(named::add);
            return named;
        }
        if (!json.has("pattern") || !json.get("pattern").isJsonArray() || !json.has("key") || !json.get("key").isJsonObject()) {
            return null;
        }
        JsonObject key = json.getAsJsonObject("key");
        for (JsonElement row : json.getAsJsonArray("pattern")) {
            for (char symbol : row.getAsString().toCharArray()) {
                if (symbol != ' ') {
                    JsonElement ingredient = key.get(String.valueOf(symbol));
                    if (ingredient == null) {
                        return null;
                    }
                    named.add(ingredient);
                }
            }
        }
        return named;
    }

    private static JsonElement item(String id) {
        return new com.google.gson.JsonPrimitive(id);
    }

    /** Whether an ingredient, or one of its choices, is an item or a tag with {@code word} in its path. */
    private static boolean names(JsonElement ingredient, String word) {
        if (ingredient.isJsonArray()) {
            for (JsonElement choice : ingredient.getAsJsonArray()) {
                if (names(choice, word)) {
                    return true;
                }
            }
            return false;
        }
        if (!ingredient.isJsonObject()) {
            return false;
        }
        JsonObject object = ingredient.getAsJsonObject();
        String name = object.has("item") ? string(object, "item") : string(object, "tag");
        Identifier id = name == null ? null : Identifier.tryParse(name);
        return id != null && id.getPath().contains(word);
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** Whether {@code item} is what a dish of the board makes: lying there, it is a dish made, not food to be cut. */
    public static boolean isDish(Item item) {
        return all().stream().anyMatch(dish -> dish.stack().is(item));
    }

    /** Whether {@code item} starts a dish: it is what goes down first in one. */
    public static boolean isBase(ItemStack item) {
        for (Assembly assembly : all()) {
            if (assembly.ingredients().get(assembly.base()).test(item)) {
                return true;
            }
        }
        return false;
    }

    /** Whether what lies on the board, the base first, is all part of some dish. */
    public static boolean fits(List<ItemStack> layers) {
        for (Assembly assembly : all()) {
            if (assign(assembly, layers, false) != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether more could still go on the layers for a bigger dish: then a finished one waits for the cook's word. */
    public static boolean canGrow(List<ItemStack> layers) {
        for (Assembly assembly : all()) {
            if (assembly.ingredients().size() > layers.size() && assign(assembly, layers, false) != null) {
                return true;
            }
        }
        return false;
    }

    /** The dish that what lies on the board makes, all of it and nothing more. */
    public static Optional<Assembly> complete(List<ItemStack> layers) {
        for (Assembly assembly : all()) {
            if (assign(assembly, layers, true) != null) {
                return Optional.of(assembly);
            }
        }
        return Optional.empty();
    }

    /**
     * Which ingredient each layer takes, the first one the base; {@code all}: every ingredient is taken. Null when the layers do not
     * go into the dish.
     */
    public static int[] assign(Assembly assembly, List<ItemStack> layers, boolean all) {
        List<Ingredient> ingredients = assembly.ingredients();
        if (layers.isEmpty() || layers.size() > ingredients.size() || all && layers.size() != ingredients.size()) {
            return null;
        }
        if (!ingredients.get(assembly.base()).test(layers.get(0))) {
            return null;
        }
        int[] taken = new int[layers.size()];
        taken[0] = assembly.base();
        boolean[] used = new boolean[ingredients.size()];
        used[assembly.base()] = true;
        return assign(ingredients, layers, 1, used, taken) ? taken : null;
    }

    private static boolean assign(List<Ingredient> ingredients, List<ItemStack> layers, int next, boolean[] used, int[] taken) {
        if (next == layers.size()) {
            return true;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!used[i] && ingredients.get(i).test(layers.get(next))) {
                used[i] = true;
                taken[next] = i;
                if (assign(ingredients, layers, next + 1, used, taken)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }
}
