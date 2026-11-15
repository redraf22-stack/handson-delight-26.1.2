# Hands-On Delight → MC 26.1.2 (Fabric) — состояние порта

Проект: `E:\GameDev\Tools\moddev\hd26` (common+fabric объединены, один Loom-проект).
Источник: ветка `1.21.1` репозитория Pocky-l/handson-delight (клон `E:\GameDev\Tools\moddev\hd`).
Зависимости в `libs/`: `fdrf.jar` (Farmer's Delight Refabricated 26.1-3.6.30), `diegesis.jar` (портированная библиотека из dg26).

## Что уже сделано
- Проект собран под MC 26.1.2 / loader 0.19.5 / Fabric API 0.155.2+26.1.2 / Java 25.
- **Удалены** непортируемые части (нет зависимостей под 26.1): `compat/` (JEI, EMI, REI), `fabric/rei/`, все `*Tests.java`, `gametest/`.
  Осталось 95 java-файлов.
- Массовые замены: `ResourceLocation`→`Identifier`, `GuiGraphics`→`GuiGraphicsExtractor`, `FastColor`→`ARGB`,
  `LightTexture.FULL_BRIGHT`→`15728880`, `net.minecraft.Util`→`net.minecraft.util.Util`,
  `ItemInteractionResult`→`InteractionResult`, `server.serverLevel()`→`(ServerLevel) server.level()`.
- `CuttingBoardBlockEntityMixin`: сохранение списка — через `TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries)`
  + `ContainerHelper.saveAllItems(ValueOutput, list)` + `buildResult()`; загрузка — `TagValueInput.create(DISCARDING, registries, tag)`.

## Состояние компиляции
Было 1000+ → **68** (build26.log). Все 4 агента закончили; основной массив закрыт.

Осталось: PotModels 14 (модели котла — старая `BlockModelDefinition`/`UnbakedModel` вырезана, нужен новый
`client/resources/model` API), ServerRecipeBookMixin 6, CookedPieces 6, ToolStroke 6, FabricPlatform 4,
MinecraftAccessor 4 (`ItemColors` переименован/удалён), PotBody 4 (`BlockEntityRenderer<T,S>` + `render`),
ModestMeals 4 и мелочь по 2.

Подсказки по остатку:
- `SkilletBook`/`CookedPieces`: `SingleItemRecipe.result()` — protected, брать через `assemble(new SingleRecipeInput(stack))`.
- `FabricPlatform`: `getRecipeFor` → `((FabricRecipeAccess) level.recipeAccess()).getSynchronizedRecipes().getFirstMatch(type, input, level)`;
  `getAllOfType` возвращает `Collection<RecipeHolder<T>>` — оборачивать `new ArrayList<>(...)` с явным типом.
- `ToolStroke`: `PickaxeItem` удалён → `ItemTags.PICKAXES`/`HOES`; `BuiltInRegistries.ITEM.getKey(...)` → `.getKey(...)` может быть удалён,
  проверять `Item.getDescriptionId`/реестр.
- `MinecraftAccessor`: `net.minecraft.client.color.item.ItemColors` отсутствует — искать новый тип цветов предметов
  (`ItemTintSources`?) или убрать аксессор вместе с `FoodColors`-зависимостью.
- `PotBody`: рендер тела горшка шёл через `BlockEntityRenderer.render(...)` — в 26.1 только
  `extractRenderState(...)` + `submit(...)` (см. dg26/EndStoveRenderer).


## Проверено по API (дополнение)
- `Item.getCraftingRemainingItem()/hasCraftingRemainingItem()` — удалены; остаток крафта теперь компонент
  `DataComponents.CRAFTING_REMAINDER` (у `ItemStack` — `getCraftingRemainingItem`).
- `BiomeColors.getAverageWaterColor` принимает `BlockAndTintGetter`; `Level` его не реализует → каст с фолбэком.
- `ItemStack.onCraftedBy(Player,int)` и `onCraftedBySystem(Level)` (второй — без игрока).
- `ServerRecipeBook` хранит `ResourceKey<Recipe<?>>`, не `Identifier`.
- `FabricRecipeAccess.getSynchronizedRecipes()`: `recipes()`, `getAllOfType(type)`, `getFirstMatch(type, input, level)`
  (на клиенте список пуст, пока не зарегистрирована синхронизация сериализаторов).
- Официальный гайд: https://docs.fabricmc.net/develop/porting/26.1/fabric-api


Главный нерешённый вопрос — рецепты: `Level.recipeAccess()` даёт только property sets/stonecutter.
Полный доступ: `net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess` —
`getSynchronizedRecipes().getFirstMatch(type, input, level)` / `getAllOfType(type)`.
⚠️ Fabric синхронизирует клиенту только сериализаторы, зарегистрированные через
`RecipeSynchronization.synchronizeRecipeSerializer(...)` (по умолчанию — ни один) → книги/фильтры на клиенте пусты,
пока в client-init не зарегистрировать `CampfireCookingRecipe.SERIALIZER` и типы FD. Это в `HandsOnDelightClient`.


ВАЖНО: `handson_delight.mixins.json` ссылается на плагин `com.pockyl.handson_delight.compat.OptionalMixins` —
его нужно создать (в исходнике лежит в common), иначе @Pseudo-миксин KegMixin будет ругаться в лог.
KegMixin теперь работает по именам (`@Mixin(targets="umpaz.brewinandchewin...KegBlockEntity", remap=false)` + `@Pseudo`),
потому что Brewin' and Chewin под 26.1 не существует.

## Ещё API 26.1 (проверено по ходу)
- `Level.getRecipeManager()` удалён → `level.recipeAccess()` (`RecipeAccess`); полный список рецептов даёт только
  `(RecipeManager) level.recipeAccess()` → `getRecipes()` (на `ServerLevel` метод возвращает `RecipeManager` напрямую).
- `AbstractCookingRecipe.getCookingTime()` → `cookingTime()`; `SingleItemRecipe.input()` — public, `result()` — protected,
  поэтому готовый стак берут через `assemble(new SingleRecipeInput(stack))`.
- `Ingredient.getItems()` → `items(): Stream<Holder<Item>>`; `Ingredient.EMPTY` → `Ingredient.of(HolderSet.empty())`.
- `BuiltInRegistries.ITEM.get(id)` → `Optional<Holder.Reference<Item>>` (не `Item`!).
- `PickaxeItem`/`TieredItem` удалены (в 26.1 — компонент `TOOL`), проверять инструмент через компонент.
- `Screen.hasShiftDown()` — через `net.minecraft.client.gui.screens.Screen`.


## API 26.1, важное для hd26 (дополнение к dg26/PORT_NOTES.md)
| 1.21.1 | 26.1.2 |
|---|---|
| `RecipeManager.getAllRecipesFor(type)` | `getRecipes()` → `Collection<RecipeHolder<?>>`, фильтровать по `holder.value() instanceof X` |
| `RecipeHolder.id()` (Identifier) | `id()` → `ResourceKey<Recipe<?>>`, путь через `.location()` |
| `Recipe.assemble(input, registryAccess)` | `assemble(input)` (без RegistryAccess) |
| `recipe.getIngredients()` / `getResultItem` | у каждого типа своё: `CuttingBoardRecipe.getInput()/getTool()/getResults()` (`List<ItemStackTemplate>`) |
| `recipe.getOutputContainer()` | нет; контейнер брать из `ItemStackTemplate`/`ChanceResult` |
| `ContainerHelper.saveAllItems(CompoundTag, list, registries)` | `saveAllItems(ValueOutput, list)` + `TagValueOutput.buildResult()` |
| `ContainerHelper.loadAllItems(CompoundTag, list, registries)` | `loadAllItems(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag), list)` |
| `TieredItem` | удалён (в 26.1 — компонент `TOOL`/`ToolMaterial`) |
| `BuiltInRegistries.ITEM.get(id)` | возвращает `Optional<Holder.Reference<Item>>` → `ItemStack` из `Holder` |

## СЛЕДУЮЩАЯ СЕССИЯ: продолжить отсюда
**Работает:** сцена рисуется (еда, нож, перемешивание), камера подъезжает, книги рецептов (доска, котёл).

**Задача №1 — камера стоит слишком далеко/высоко.** Из-за этого:
- `isSceneDrawn()` возвращает false → штатный рендерер FDRF не отключается → **двойное мясо** (одно нормальное, одно фиолетовое);
- сцена кажется мелкой, книга «далеко».

Смотреть: `dg26 → impl/client/focus/FocusController.java`, метод `cameraPose(...)` — там считаются
позиция и дистанция обзора; плюс `CameraPose`/`Easing`. Сверить с оригиналом 1.21.1 (`E:\GameDev\Tools\moddev\hd\common`).
Затем проверить `CameraMixin` (`setRotation`/`setPosition`, в 26.1 Camera.update) и
`isDrawn`/`KEEP_RANGE`/`reach` в `SceneRenderer`/`ClientStation`.

**Задача №2 — книга рецептов сковороды пуста** (у доски/котла работает): проверить `SkilletBook`
(`recipeAccess` → `FabricRecipeAccess.getSynchronizedRecipes()`; на клиенте список синхронизируется только
для зарегистрированных сериализаторов — см. `RecipeSynchronization.synchronizeRecipeSerializer` в client-init).

**Задача №3 (косметика):** `TOTAL_TIMES` в печи не сохраняется (int[] отброшен при переходе на ValueOutput).

## ФИНАЛ (14.11.2026)
Готовые jar'ы: `E:\GameDev\mods-26.1\diegesis-1.0.0-beta.9+26.1.2.jar`, `handson_delight-1.4.1+26.1.2.jar`.

**Работает:** компиляция (0 ошибок), загрузка в 26.1.2, определение станций handson в diegesis,
подъезд камеры к блоку, GUI-примитивы (текстуры/предметы в GUI рисуются — проверено тестом).

**Не работает (главное):** визуал сцены — предметы внутри (сковорода, палка в котле, мясо на доске).
Диагностика: модели сцен ЗАГРУЖАЮТСЯ (5 и 22 квада), геометрия ОТПРАВЛЯЕТСЯ (6601 вызов putQuads,
предметы 52 квада), но на экран не попадает. Ошибок в логе нет.

**Причина и путь решения:** в 26.1 изменился конвейер отрисовки — всё идёт через
`SubmitNodeCollector.submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)` с явным
порядком (`order(int)`), а SceneRenderer рисует «вручную» в `MultiBufferSource` в конце кадра.
Нужно переписать SceneRenderer (1000+ строк) на collector-модель:
`renderStation`/`renderVisual`/`drawItem`/`drawModel`/`drawLitBlock`, плюс обёртки
`TintedConsumer`/`GlassConsumer`/`BackFaceConsumer`; точка входа — миксин в
`LevelRenderer.submitEntities(pose, LevelRenderState, SubmitNodeCollector)`.

Прочее не готово: книги рецептов на клиенте (нужна `RecipeSynchronization` сериализаторов),
панель инвентаря (условие `showsInventory()`), косметика (свой корпус котла, цвета предметов).

- **Компиляция: 0 ошибок.** Jar: `build/libs/handson_delight-1.4.1+26.1.2.jar` (719 КБ).
- **Клиент запускается без краша** (exit=0), мод и diegesis загружаются, ресурсы читаются.
- Исправлено в рантайме (миксины): CraftingMenu (ServerLevel), печь (cookingProgress/cookingTimes/ServerLevel/ValueOutput),
  cookAndOutputItems(ServerLevel), котёл (ServerLevel, ValueOutput), доска (ValueOutput/child), сковорода (ServerLevel,
  addItemToCook+ServerLevel, ValueOutput), StationRenderers (tryExtractRenderState).
- **Важно:** `libs/diegesis.jar` должен быть СВЕЖИМ (пересобран из dg26 после правок миксинов), иначе падение на ChatComponent.
- Осталось (не блокирует запуск):
  - `RecipeManagerMixin`: в 26.1 `apply(RecipeMap, ResourceManager, ProfilerFiller)` — миксин не применяется (лог ERROR),
    из-за этого не работает сборка блюд на доске (Assembly).
  - «Error executing task on Client: mouseClicked» — в GUI, нужен полный стек.
  - Книги рецептов на клиенте пусты (нет RecipeSynchronization).
  - Косметика отключена: свой корпус котла (PotModels), цвета предметов (ItemColors), дроп слоёв при clearContent.

## 15.11.2026 — сцена в кадре, рецепты доски ожили
**Главное: визуал сцены ПОЧИНЕН.** Причина ровно та, что предполагалась: `MultiBufferSource.immediate(...).endBatch()`
рисует свой пакет отдельным `RenderPass` ДО основного прохода кадра (renderLevel строит FrameGraph, а `endBatch` —
это `RenderType.draw(MeshData)` → свой CommandEncoder.createRenderPass), поэтому геометрия честно отправлялась и
бесследно перекрывалась уровнем.

Как сделано (dg26):
- Новый `impl/client/render/SceneSubmit.java` — `MultiBufferSource`, который копит вершины **в плоские массивы**
  по render type (без аллокаций на вершину) и затем отдаёт их в
  `SubmitNodeCollector.order(n).submitCustomGeometry(identityPose, type, (pose, consumer) -> ...)`.
  Вершины уже камера-относительные (уровневый poseStack в 26.1 пустой), поэтому pose при сабмите — identity.
- `SceneRenderer`: убраны `clearBuffers`/`sceneBuffers` и все ручные `endBatch`; сцена отдаётся `SCENE.submit(collector, 0)`,
  счётчики на предметах — отдельным `SceneSubmit` с `order 2`; контуры по-прежнему пишутся в `outlineBufferSource`
  (игру сбрасывает его сама внутри прохода), `endOutlineBatch` больше не зовём.
- `LevelRendererMixin` (`submitEntities` TAIL) передаёт коллектор в `SceneRenderer.render(...)`.

Рецепты доски (hd26):
- `RecipeManagerMixin` переписан под 26.1: `@Inject(method = "apply(RecipeMap, ResourceManager, ProfilerFiller)", at = TAIL)`
  (старый таргет `apply(Map,...)` из 1.21 не находился → миксин падал). Теперь применяется.
- В 26.1 менеджер парсит рецепты кодеком и JSON не хранит → `AssemblyRecipes.collectFrom(ResourceManager, taken, crafts)`
  второй раз читает `recipe/*.json` из ресурсов перезагрузки; чужие shaped/shapeless складываются в `crafts` (для тега `assembled`).
- **Рецепты читаются ЛЕНИВО**: при загрузке рецептов теги предметов НЕ привязаны (`Trying to access unbound tag` в
  `Ingredient.isEmpty()`), поэтому `candidates`/`others` хранятся как JSON, а разбор идёт в `chosen()` (после привязки тегов).
- Результат блюда — `ItemStackTemplate` (`Assembly.result()` → `Assembly.stack()`): в 26.1 на этапе загрузки
  компоненты предметов не привязаны (`Components not bound yet` при `new ItemStack(item)`).
- Ингредиенты в JSON 26.1 — строкой (`"minecraft:wheat"`) или `"#tag"`, не `{"item":...}` → поправлены `item()` и
  `ModestMeals.choices()`.
- `RecipeSynchronization.synchronizeRecipeSerializer(...)` для `CampfireCookingRecipe`, `SmeltingRecipe`,
  FD `CUTTING`/`COOKING` — в `HandsOnDelightFabric.onInitialize()` (общий init). Книга котла и доски теперь с рецептами.
- Итог в логе: `128 crafting recipes of Farmer's Delight may be put together on the cutting board`, ошибок разбора 0.

Проверено на живом клиенте: сцена котла на печке (жидкость, половник, книга рецептов с «Pumpkin Pie»), доска, котёл —
всё рисуется; контуры/счётчики собираются в отдельные order-группы.

Инструменты для проверки без рук (осторожно, забирают экран):
- `gradle runClientProbe` — клиент + `-Dhds.shot=<dir>` (скриншоты в мире каждые 10 с) + `-Ddiegesis.autotest`
  (закрывает открытые экраны, поворачивает игрока к ближайшей станции и открывает её). Для игры руками — `runClientQuick`.
- Скриншоты кладутся в `<dir>/screenshots/` (папку надо создать заранее, иначе `NoSuchFileException`).

Осталось:
1. **Масштаб/посадка сцены** — на грубом взгляде сковорода/гриль печи кажутся крупнее блока; сверить с 1.21 (Task №1 старых заметок).
2. ~~`mouseClicked`~~ — **закрыто**: в старых логах (`playtest.log`, `playtest2.log`, `runClient17.log`) под ним лежало
   `Caused by: Mixin transformation of net.minecraft.world.item.crafting.RecipeManager failed` — то есть падал тот же
   `RecipeManagerMixin`, а не GUI. Теперь миксин применяется, ошибки нет.
3. Косметика: свой корпус котла (PotModels), цвета предметов (ItemColors), дроп слоёв при clearContent.
4. `TOTAL_TIMES` в печи не сохраняется (int[] отброшен при ValueOutput).
5. Книга сковороды (SkilletBook) — проверить после RecipeSynchronization.

## Проверка камеры и сцены (продолжение 15.11.2026)
- **Камера подъезжает правильно.** Момент, который сбивал с толку, — скриншоты в состоянии «станция закрыта» (камера смотрит
  туда, куда смотрел игрок). При открытой станции `CameraMixin` (`@Inject` в `Camera.update` TAIL) ставит позу из
  `FocusController.rigPose()`; у всех станций hd26 своей `rig` нет → работает `CameraRig.DEFAULT` (position (8,36,-12) px,
  target (8,16,7) px = камера 2.25 блока над основанием, 0.75 перед станций, наклон ≈46° вниз) — как в 1.21.1.
- **Ассеты моделей сцены совпадают с оригиналом побайтно** (сверено с `handson_delight-fabric-1.21.1-1.4.1.zip`):
  `assets/handson_delight/models/diegesis/**` диффов нет. Тёмные «коробки» — это реальные текстуры FD
  (`farmersdelight:block/cooking_pot_side` — тёмно-серый), а не пропавшие текстуры.
- ⚠️ **Грабля: `private static final boolean DIAG = Boolean.getBoolean(...)` ВНУТРИ миксина не работает** — статический
  инициализатор класса миксина не сливается с целевым классом, поле остаётся `false`, лог молчит, хотя код выполняется.
  Системные свойства в миксинах читать в точке использования (или в обычном классе-хелпере), не в статике миксина.

Проверено скриншотами (пробный прогон): котёл открывается, камера садится на риг, в кадре котёл с жидкостью и половником +
книга рецептов; доска открывается, книга доски листает рецепты («Acacia Boat» — это резка всего подряд по дизайну мода).


## Ещё три находки с живого теста (15.11.2026, позже)
- **Пропал хук FOV — отсюда «камера далеко/мелко».** В 1.21.1 `GameRendererMixin` подменял `GameRenderer.getFov` на
  `FocusController.fov(...)`; в порте этого хука не было. В 26.1 FOV считается **в самой камере**
  (`Camera.calculateFov(float)`, приватный), поэтому хук перенесён туда (`CameraMixin`,
  `@Inject(method = "calculateFov", at = RETURN, cancellable)` → `focus.fov(value)`). У доски в JSON сцены `fov: 30`
  (position [7.5,35.5,-21], target [7.5,1,8]) — с ним кадр стал как в оригинале (пользователь подтвердил).
- **Маджента вместо текстур у «кусочков» на доске.** `ItemVoxels` строит объём из пикселей иконки и отдаёт её
  `Identifier`; `SceneRenderer.blockSprite` искал спрайт **только в атласе блоков**, а в 26.1 иконки предметов
  лежат в отдельном атласе `minecraft:textures/atlas/items.png`. Теперь `blockSprite` ищет в блоках, затем в
  `TextureAtlas.LOCATION_ITEMS` (проверка через `MissingTextureAtlasSprite.getLocation()`).
- **Обводка при наведении заливала предмет белым.** Силуэт рисовался в `OutlineBufferSource`, и игра превращала его в
  линию пост-обработкой (`entity_outline` + `doEntityOutline()`); в 26.1 силуэт сбрасывается в **основной** таргет
  (переопределение цвета для этого таргета ставится только в late-debug проходе), поэтому белый силуэт был виден.
  Сделано своё: «вывернутая оболочка» — та же геометрия, увеличенная на 7 % (`OUTLINE_GROW`), нарисованная через
  `BackFaceConsumer` и `TintedConsumer(цвет обводки)` в общий батч сцены. Видна только кромка за предметом, белого нет
  (для плоских предметов оболочки не видно — у них своя подсветка слота).
- Диагностика в миксинах: состояние для `SceneDebug.once(...)` (hd26) живёт в обычном классе — статические поля
  миксина не инициализируются (см. грабли выше).
- **Двойные предметы (15.11.2026): `StationRenderersMixin` не был вписан в `handson_delight.mixins.json`** — класс есть, а в
  списке `client` его не было. Миксин никогда не применялся, поэтому Farmer's Delight продолжал рисовать своё поверх сцены:
  на доске лежали «лишние» предметы, на сковороде стояла «гигантская» копия еды. Добавлен в список (проверить на доске, печи,
  сковороде, котле).
- **«Гигантская еда» на сковороде (15.11.2026): причина в `ItemVoxels.flatTexture`** — он искал иконку предмета только в
  атласе блоков. Если `pickParticleMaterial` не давал спрайт, запасной путь возвращал null → `ItemVoxels.icon` = empty →
  поведение падало в `Visual.ofItem` и рисовало предмет размером в **целый блок** (1.16 блока против 9 px у кусочка). Теперь
  иконка ищется в блоках, затем в атласе предметов. Проверено: печёная картошка в сковороде — четыре кусочка по ~0.5 блока.
- **Пара «спрайт ↔ render type» (15.11.2026):** мало найти спрайт в атласе предметов — рисовать его надо батчем
  `Sheets.cutoutItemSheet()/translucentItemSheet()`, а не блочным, иначе текстура сэмплится из чужого атласа («странная еда»).
  Сделано: `SceneRenderer.sprite(...)` возвращает `Sprite(спрайт, изАтласаПредметов)`, `renderShape` выбирает лист по нему.
- **«Поломанные иконки в серых квадратиках» книги (15.11.2026) — настоящая причина: `DiegesisClient.tinted(...)`.**
  Для полупрозрачной иконки (`faded`) он подменял лист рисования на `Sheets.translucentItemSheet()` — **лист атласа предметов**,
  в том числе для блочных моделей (они читают атлас блоков). UV от блока на чужом атласе = «обрывки текстуры». Теперь
  `seeThrough()` переводит каждый лист в его же полупрозрачный близнец: `cutoutBlockSheet → translucentBlockSheet`,
  `cutoutBlockItemSheet → translucentBlockItemSheet`, `cutoutItemSheet → translucentItemSheet`. Это же чинит иконки в сцене,
  которые рисуются через `tinted` (подсветки, «недоступные» рецепты, превью).
- **«Неправильные текстуры» у блоков/досок/грибов в книге (15.11.2026):** `RecipeBook.Canvas.item` рисовал иконку в контексте
  `ItemDisplayContext.GUI` — в 26.1 он применяет GUI-трансформ модели, и 3D-блок разворачивается тремя гранями (а гриб встаёт),
  что читается как «чужая текстура». В 1.21.1 иконка рисовалась **без трансформа** (`ItemDisplayContext.NONE`) и сплющивалась
  масштабом глубины. Возвращено: контекст `NONE`, глубина по `ITEM_STATE.usesBlockLight()`
  (`BLOCK_DEPTH`/`ITEM_DEPTH`), как было `model.isGui3d()`.
- **Партиклы белыми квадратами (15.11.2026):** `SceneParticles` брал атлас частиц через
  `getTextureManager().getTexture(LOCATION_PARTICLES)` — в 26.1 атласы переехали в `AtlasManager`, оттуда возвращался не атлас,
  спрайт не находился и каждый партикл рисовался белым квадратом («огромный пар»). Теперь
  `getAtlasManager().getAtlasOrThrow(minecraft:particles)`.
- ⚠️ **Проверять, что новый `diegesis.jar` попал в `hd26/libs`, а не только в `mods-26.1`** — dev-клиент грузится из
  `libs`, я один раз забыл скопировать и час тестировал старую сборку.
- ⚠️ **Нельзя перезаписывать `hd26/libs/diegesis.jar` при запущенном (или стартующем) клиенте.** JVM грузит классы из
  зипа лениво, и если файл подменили в этот момент — `ZipException: invalid LOC header (bad signature)`,
  `Drawing station <id> failed` каждым кадром, при этом пропадают книга рецептов и вся интеракция станции (они рисуются
  в том же методе). Симптом обманчив («сломалась книга и перетаскивание»), лечится перезапуском клиента. Порядок
  безопасный: `gradle build` в dg26 → скопировать jar в `hd26/libs` → **потом** запускать клиент.

## Варка в котле: найдено по логам игрока (15.11.2026, ночь)
- **Сервер не видел рецептов котла:** `FabricPlatform.findRecipe/cookingRecipes` всегда брали
  `FabricRecipeAccess.getSynchronizedRecipes()` — это список для **отправки клиенту**, на сервере он пуст → `batch = 0` →
  `progress` всегда 0 (полоска не растёт, бульон не красится), а «готово» приходило только когда печь доваривала своим путём.
  Теперь на сервере спрашивается его `RecipeManager` (`getRecipes()`), на клиенте — синхронизированный список.
- **Матчер рецептов переписан на свой:** FD-шный `CookingPotRecipe.matches` идёт через `RecipeWrapper.stackedContents()` +
  `StackedItemContents.canCraft`, а на клиенте это всегда пусто (за ним нет книги крафта) → из любых ингредиентов «блюда не
  получалось». Теперь `FabricPlatform.matchByHand`: подбор «один ингредиент ↔ один предмет» через `Ingredient.test`
  (работает и с тегами, и с `fabric:any`). Проверено: капуста + говядина → `cabbage_rolls` появилось в табло.
- **Табло гасло из-за `here`:** условие было `RecipeBook.isWorkedAt(view)` (локальная камера на станции); теперь ещё и
  `view.isFocused()`. Плюс `SmoothPass` отключён по умолчанию (книга и табло рисовались тускло, см. выше).
- **Дым:** серый — это `StoveBlock.animateTick` + `StoveBlockEntity.addSmokeParticles` (оба глушатся в радиусе 6 блоков при
  работе за станцией), большой белый — костёр (`campfire_cosy/signal_smoke`, глушится в радиусе 12). **Наш `PAN_SMOKE` уменьшен
  осознанно:** в 1.21.1 у него `grow(3.0)` — частица утраивается (до ~0.5 блока) и с близкой камеры станции читалась как дым
  костра. Теперь 1.2–1.7 px, `grow(1.5)`, цвет 0x66E8E8E8 + `emissive()` (в тёмной кухне пар не сереет).

## Найдено по логам игрока (15.11.2026, продолжение)
- **Варка: цвет бульона и табло.** Бульон менялся скачком, потому что `recolour` ставился только на «что-то влили»
  (`.onFlow(...)`), а цвет считается из `PROGRESS`, меняющегося всю варку — теперь `recolour` ставится и при каждом
  изменении прогресса (`lastProgress`). Табло устроено так: `preview` (ингредиенты сложились в блюдо) → плитка; полоска =
  прогресс партии (`PROGRESS`, растёт пока котёл **нагрет** и его **мешают**); звёзды = качество помешивания (`STARS`,
  открываются по одной на блюдо через `OPEN_STARS`), за звёзды дают добавочные порции. «Полоска заполняется, пока мешаю» =
  котёл стоит на огне и мешается.
- **Обводка при наведении:** оболочка теперь только у визуалов (у плоских иконок слота она давала двойной контур), рост
  уменьшен с 7 % до 4 %.
- **Частицы:** `PAN_SMOKE` стал белее (0x66F2F2F2) и `emissive()` — раньше серо-синий цвет умножался на свет сцены, и пар
  выглядел серым; `diegesis:steam` уже белый и emissive, `diegesis:smoke` остаётся серым намеренно (это дым).
- **«Гигантская сырая картошка» и «висит над сковородой» — один корень: в 26.1 картофель/морковь/свёкла стали
  `BlockItem`** (они сажаются). `ItemVoxels.read` решал «лежит как блок» через `instanceof BlockItem` → у сырого картофеля
  иконки не было → отрисовка предметом в **целый блок** (16 px вместо 6.5–9) и высота `+4` px у `height(...)` (кусок «в
  воздухе»), а печёный картофель — обычный `Item`, поэтому выглядел правильно. Теперь решение по **модели**:
  `ItemStackRenderState.usesBlockLight()` (блочный свет = модель блока). В `RecipeBook` глубина иконки тоже по модели, а не
  по классу предмета. Проверено логом: `piece 0: look 16 minecraft:potato ... icon true` и на скриншоте 4 куска лежат в
  сковороде.
- **Большой пар — это дым печи Farmer's Delight, не наши частицы.** Замер: наши частицы сцены ≤4 px, 4–13 штук; стили
  `PAN_SMOKE` 1.8–2.6 и `HEAT` 3–4 — те же числа, что в 1.21.1. `StoveSmokeMixin` глушил дым только когда нарисована сцена
  *самой печки*; при работе со сковородой на печке позиция печки «не показана» → дым шёл в кадр. Теперь глушится и когда
  игрок работает за станцией в радиусе 6 блоков (`HandsOnDelightClient.stationNear`).
- **Табло:** сверил `diegesis/scenes/cooking_pot.json` из релиза 1.20.1 — **побайтно тот же**, что у нас (узлы `badge`,
  `dial_track`, `dial`, `preview`, та же камера). Значит поведение одинаково во всех версиях: табло есть, когда в котле
  складывается блюдо (`preview` не пуст).
- ⚠️ **Диагностика: `SceneDebug.ON` временно ставить в `true`** для сеансов без `-Dhandson.diag` (иначе лог молчит), и
  не забывать возвращать на `Boolean.getBoolean(...)`.
- **Гладкий проход (`SmoothPass`) отключён по умолчанию (15.11.2026).** Через него рисуются «поверх всего» книга рецептов и
  табло котла; в порте его собственный `BufferSource` нигде не сбрасывается (`endBatch` потерян при переходе на 26.1), и то,
  что он всё-таки рисует, выходит **тусклее** оригинала (книга и табло бледные, причём сначала ярко, потом тускнеет — как
  раз момент, когда проход включается). Теперь `defer(...)` возвращает `false`, и painters рисуются вместе со сценой —
  резко и в своих цветах (теряется только сглаживание краёв). Включить обратно для проверки: `-Ddiegesis.smooth`.

## Следующие шаги (устарело, см. раздел 15.11.2026 выше)
1. Добить `RecipeManagerMixin` (RecipeMap) — вернуть сборку блюд.
2. Понять `mouseClicked` (полный стек из лога).
3. Зарегистрировать RecipeSynchronization для книг на клиенте.
4. Проверка в игре: доска/печь/котёл/сковорода — камера подъезжает, 3D-кнопки работают.

## Аддоны: энд-печь и незеритовая — станции как есть (15.11.2026, ночь)

**Итог: интегрировать почти нечего — обе печи уже были станциями с самого порта**, их не хватало только в проверке.

- `data/handson_delight/tags/block/stoves.json` **уже содержал** `ends_delight:end_stove` и `mynethersdelight:nether_stove`
  (тег приехал из 1.21.1 вместе с портом). `StationType.matches` смотрит и теги, поэтому обе печи подхватываются сами.
- `StoveStation.opens()` требует `AbstractStoveBlockEntity`: у энд-печи свой BE от этого класса, у незеритовой — FD-шная
  `StoveBlockEntity`. Готовка/повороты/сохранение идут через `StoveBlockEntityMixin` на **абстрактном** классе, то есть на обеих.
- `AddonStoveSmokeMixin` (`@Pseudo`, `require = 0`) глушит дым `EndStoveBlockEntity`; для незеритовой он же указывает на
  несуществующий `NetherStoveBlockEntity` — безвредно, FD-шный `StoveSmokeMixin` её и так накрывает.

**Чем эти печи отличаются от обычной (проверено по байткоду, чтобы не гадать):**
- **Энд-печь — копия FD-шной**: тот же родитель `AbstractStoveBlock`, те же 6 слотов и **идентичная** раскладка кусков
  (`getStoveItemOffset`, константы `0.3/0.2` один в один), дым — обычный `ParticleTypes.SMOKE`. Свой BE нужен только чтобы
  зарегистрировать свой тип и свой рендерер. Отличие — **только текстуры** (энд-камень) и `.mcmeta`-анимация фронта у подожжённой.
- **Незеритовая**: наследует FD-шный `StoveBlock`, своя фича — свойство `SOUL`: поджигается топливом душ
  (`#minecraft:soul_fire_base_blocks`), получает модель `nether_stove_soul`, частицы `SOUL_FIRE_FLAME` и звук `SOUL_ESCAPE`.
  **На готовку не влияет** — гриль тот же, скорость та же; `SOUL` больше нигде в коде не читается.
- **Котла в My Nether's Delight 3.0.0-beta.3 нет**: в jar лежат `blockstates/blackstone_cooking_pot.json`, модели, `models/item`
  и текстуры `cooking_pot_*`, но блока/предмета нет ни в `MNDBlocks`, ни в `MNDItems`, нет loot table, рецептов и названия в lang.
  Это остатки старых версий (в 1.20 котёл был). Интегрировать нечего.
- **`The Blazier`** (`blazier`) — не станция и трогать не надо: свой `BaseEntityBlock`, 4 уровня нагрева
  (`SMOKING → CAMPFIRE → BAKING → SMELTING`), тип рецепта выбирается по нагреву (`smoking`/`campfire_cooking`/`smelting`/`blasting`),
  слотов `getMaxSlots(heat)`, время `getCookTimeForRecipe(recipe, heat)`, тултипы «нужен огонь пожарче/послабее».

**⚠️ Найденная поломка (это и был «нет рецептов», случай со сковородой/печью):**
`StoveCooking.grillable()` для рецепта, у которого **все** варианты входа отсекаются тегом `handson_delight:not_on_grill`
(= `#cracked`, яйца), пытался собрать `Ingredient.of(HolderSet.empty())`, а **26.1 на пустой ингредиент кидает
`UnsupportedOperationException: Ingredients can't be empty`** → `StoveBook.pages()` падал целиком, книга печи оставалась пустой.
С аддонами такой рецепт появился (у MND есть `boiled_egg_from_campfire_cooking`), поэтому и вылезло. Теперь `grillable`
возвращает `Optional<Ingredient>`, пустое просто не попадает в книгу (`StoveBook`, `AddonCheck`).

**Цифры на этой сборке (аддоны в `run/mods`):** печей-станций 3 (FD, энд, незеритовая); рецептов костра 29, из них **27 на гриль**
(2 отсеяны — яйца); доска режет **143** (26 MND + 8 End), собирает **74** (27 + 13); котёл варит **63** (21 + 14);
на доске «может быть собрано» **232** крафта (было 128 без аддонов).

**Приёмы проверки без рук (dev):**
- `gradle runClientAddons --offline -Pplace=<block> -Pstation=<type> -Pclick=<node>` — клиент сам ставит блок рядом с игроком
  (`-Ddiegesis.autotest.place`), открывает станцию и кликает узел сцены (`-Ddiegesis.autotest.click=book_open`).
  Ставит только в одиночной игре и только после `player.tickCount >= 40` (иначе блок уезжает в 0,0,0 — проверено).
- `AddonCheck` (в hd26, включается `-Dhandson.addontest=true`; **именно `=true`**, `-Dfoo` без значения даёт пустую строку и
  `Boolean.getBoolean` = false) — раз в логе пишет: открываются ли три печи как станция, сколько режет доска (всего и по аддонам),
  сколько собирает, сколько варит котёл, сколько рецептов костра и сколько из них на гриль.
- Аддоны для dev-клиента лежат в `hd26/run/mods` (это не зависимости сборки: в jar они не попадают, ставить их игроку отдельно).
