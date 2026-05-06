package calebxzhou.rdi.mc.common2.rcmd.client;

import java.util.List;
import java.util.Map;

public record RcmdRecipeView(
        String id,
        String type,
        String group,
        boolean special,
        RcmdItemStackView result,
        String category,
        List<RcmdIngredientView> ingredients,
        Map<String, RcmdIngredientView> key,
        List<String> pattern,
        Float experience,
        Integer cookingTime
) {
}
