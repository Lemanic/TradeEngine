package pl.tradeengine.domain.util;

import java.math.BigDecimal;

public class PriceFormatter {

    private PriceFormatter() {}

    public static String format(BigDecimal price) {
        if (price == null) {
            return "N/A";
        }

        return price.stripTrailingZeros().toPlainString();
    }
}
