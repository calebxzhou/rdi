package calebxzhou.rdi.mc.common2.rcmd.client;

import java.util.List;

public record RcmdIngredientView(List<RcmdItemStackView> items) {
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
