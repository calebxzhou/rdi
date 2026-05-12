package calebxzhou.rdi.mc.common2.rcmd.client;

import calebxzhou.rdi.mc.rcmd.RcmdArgumentTypes;
import calebxzhou.rdi.mc.rcmd.RcmdCommandSpec;
import calebxzhou.rdi.mc.rcmd.RcmdDispatchResult;
import calebxzhou.rdi.mc.rcmd.RcmdDispatcher;
import calebxzhou.rdi.mc.rcmd.RcmdResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RcmdClientCommands {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RcmdDispatcher DISPATCHER = new RcmdDispatcher();
    private static final ExecutorService EXPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "rdi-export");
        thread.setDaemon(true);
        return thread;
    });

    static {
        DISPATCHER.register(
                RcmdCommandSpec.builder("firmchunk")
                        .description("Toggle firm chunk border")
                        .argument("state", RcmdArgumentTypes.enumOf("show", "hide"))
                        .command(context -> {
                            var state = context.getString("state");
                            ((RcmdClientBridge) context.source()).setFirmChunkVisible("show".equals(state));
                            return RcmdResult.ok("永久区块边框：" + ("show".equals(state) ? "显示" : "隐藏"));
                        })
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("export", "recipe")
                        .description("Export synced recipes")
                        .command(context -> startExportRecipes((RcmdClientBridge) context.source()))
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("export", "langkey")
                        .description("Export English and Chinese language keys")
                        .command(context -> startExportLangKeys((RcmdClientBridge) context.source()))
                        .build()
        );
    }

    private RcmdClientCommands() {
    }

    public static RcmdDispatchResult dispatch(RcmdClientBridge bridge, String message) {
        return DISPATCHER.dispatch(bridge, message);
    }

    public static void reply(RcmdClientBridge bridge, RcmdResult result) {
        if (result == null || result.message().isEmpty()) {
            return;
        }
        for (var message : result.message().split("\\R")) {
            if (message.isEmpty()) {
                continue;
            }
            if (result.success()) {
                bridge.sendFeedback(message);
            } else {
                bridge.sendError(message);
            }
        }
    }

    private static RcmdResult startExportRecipes(RcmdClientBridge bridge) {
        var snapshot = bridge.recipeExportSnapshot();
        if (snapshot == null) {
            return RcmdResult.error("请先进入地图再导出配方");
        }
        EXPORT_EXECUTOR.execute(() -> {
            var result = exportRecipes(snapshot, bridge);
            bridge.executeOnMainThread(() -> reply(bridge, result));
        });
        return RcmdResult.ok("已开始后台导出JSON配方" + snapshot.recipes().size() + "个，完成后会提示");
    }

    private static RcmdResult startExportLangKeys(RcmdClientBridge bridge) {
        var snapshot = bridge.langExportSnapshot();
        EXPORT_EXECUTOR.execute(() -> {
            var result = exportLangKeys(snapshot, bridge);
            bridge.executeOnMainThread(() -> reply(bridge, result));
        });
        return RcmdResult.ok("已开始后台导出语言key，完成后会提示");
    }

    private static RcmdResult exportRecipes(RcmdRecipeSnapshot snapshot, RcmdClientBridge bridge) {
        try {
            var context = new RecipeExportContext();
            var recipesByType = new JsonObject();

            for (var recipe : snapshot.recipes()) {
                var recipeJson = recipeToJson(recipe, context);
                var typeRecipes = recipesByType.has(recipe.type()) ? recipesByType.getAsJsonArray(recipe.type()) : new JsonArray();
                typeRecipes.add(recipeJson);
                recipesByType.add(recipe.type(), typeRecipes);
            }

            var exportJson = new JsonObject();
            exportJson.add("items", context.items());
            exportJson.add("recipes", recipesByType);
            var exportDir = bridge.gameDirectory().resolve("rdi");
            Files.createDirectories(exportDir);
            var exportPath = exportDir.resolve("export_recipes.json");
            Files.writeString(exportPath, GSON.toJson(exportJson), StandardCharsets.UTF_8);
            return RcmdResult.ok("已导出JSON配方" + snapshot.recipes().size() + "个：" + exportPath.toAbsolutePath());
        } catch (Exception e) {
            return RcmdResult.error("配方导出失败：" + e.getMessage());
        }
    }

    private static RcmdResult exportLangKeys(RcmdLangSnapshot snapshot, RcmdClientBridge bridge) {
        try {
            var keys = new TreeSet<String>();
            keys.addAll(snapshot.english().keySet());
            keys.addAll(snapshot.chinese().keySet());

            var csv = new StringBuilder("langkey,english-name,chinese-name\r\n");
            for (var key : keys) {
                csv.append(csvValue(key))
                        .append(',')
                        .append(csvValue(snapshot.english().getOrDefault(key, "")))
                        .append(',')
                        .append(csvValue(snapshot.chinese().getOrDefault(key, "")))
                        .append("\r\n");
            }

            var exportDir = bridge.gameDirectory().resolve("rdi");
            Files.createDirectories(exportDir);
            var exportPath = exportDir.resolve("langkey.csv");
            Files.writeString(exportPath, csv, StandardCharsets.UTF_8);
            return RcmdResult.ok("已导出语言key" + keys.size() + "个：" + exportPath.toAbsolutePath());
        } catch (Exception e) {
            return RcmdResult.error("语言key导出失败：" + e.getMessage());
        }
    }

    private static String csvValue(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static JsonObject recipeToJson(RcmdRecipeView recipe, RecipeExportContext context) {
        var json = new JsonObject();
        json.addProperty("id", recipe.id());
        json.addProperty("group", recipe.group());
        json.addProperty("special", recipe.special());
        json.addProperty("rk", context.itemRef(recipe.result()));
        json.add("result", itemStackToJson(recipe.result(), context));
        if (recipe.category() != null) {
            json.addProperty("category", recipe.category());
        }
        if (recipe.ingredients() != null && !recipe.ingredients().isEmpty()) {
            json.add("ingredients", ingredientsToJson(recipe.ingredients(), context));
        }
        if (recipe.key() != null && !recipe.key().isEmpty()) {
            var key = new JsonObject();
            for (var entry : recipe.key().entrySet()) {
                key.add(entry.getKey(), ingredientKeyJson(entry.getValue(), context));
            }
            json.add("key", key);
        }
        if (recipe.pattern() != null && !recipe.pattern().isEmpty()) {
            var pattern = new JsonArray();
            for (var row : recipe.pattern()) {
                pattern.add(row);
            }
            json.add("pattern", pattern);
        }
        if (recipe.experience() != null) {
            json.addProperty("experience", recipe.experience());
        }
        if (recipe.cookingTime() != null) {
            json.addProperty("cooking_time", recipe.cookingTime());
        }
        return json;
    }

    private static JsonElement ingredientKeyJson(RcmdIngredientView ingredient, RecipeExportContext context) {
        if (ingredient.isEmpty()) {
            return new JsonArray();
        }
        if (ingredient.tags() != null && !ingredient.tags().isEmpty()) {
            var json = new JsonObject();
            var tags = new JsonArray();
            for (var tag : ingredient.tags()) {
                var tagJson = new JsonObject();
                tagJson.addProperty("id", tag.id());
                tagJson.addProperty("c", tag.count());
                tagJson.addProperty("n", tag.candidateCount());
                if (tag.examples() != null && !tag.examples().isEmpty()) {
                    var examples = new JsonArray();
                    for (var example : tag.examples()) {
                        examples.add(context.itemRef(example));
                    }
                    tagJson.add("examples", examples);
                }
                tags.add(tagJson);
            }
            json.add("tags", tags);
            if (ingredient.items() != null && !ingredient.items().isEmpty()) {
                var items = new JsonArray();
                for (var itemStack : ingredient.items()) {
                    items.add(context.itemRef(itemStack));
                }
                json.add("items", items);
            }
            return json;
        }
        if (ingredient.items().size() == 1) {
            return new JsonPrimitive(context.itemRef(ingredient.items().get(0)));
        }
        var json = new JsonArray();
        for (var itemStack : ingredient.items()) {
            json.add(context.itemRef(itemStack));
        }
        return json;
    }

    private static JsonArray ingredientsToJson(Iterable<RcmdIngredientView> ingredients, RecipeExportContext context) {
        var json = new JsonArray();
        for (var ingredient : ingredients) {
            json.add(ingredientKeyJson(ingredient, context));
        }
        return json;
    }

    private static JsonObject itemStackToJson(RcmdItemStackView itemStack, RecipeExportContext context) {
        var json = new JsonObject();
        json.addProperty("i", context.itemRef(itemStack));
        json.addProperty("c", itemStack.count());
        return json;
    }

    private static final class RecipeExportContext {
        private final Map<String, Integer> itemIdToIndex = new LinkedHashMap<>();
        private final JsonArray items = new JsonArray();

        int itemRef(RcmdItemStackView itemStack) {
            var existingIndex = itemIdToIndex.get(itemStack.id());
            if (existingIndex != null) {
                return existingIndex;
            }

            var index = itemIdToIndex.size();
            itemIdToIndex.put(itemStack.id(), index);
            var itemJson = new JsonObject();
            itemJson.addProperty("id", itemStack.id());
            itemJson.addProperty("lk", itemStack.langKey());
            items.add(itemJson);
            return index;
        }

        JsonArray items() {
            return items;
        }
    }
}
