package pl.tradeengine.application.dto.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ilości wychodzą w pełnej precyzji, ale bez zer dopełniających i bez notacji wykładniczej. */
final class PortfolioAmounts {

    private PortfolioAmounts() {}

    static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }
}
