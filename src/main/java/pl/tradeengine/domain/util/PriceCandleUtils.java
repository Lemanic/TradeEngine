package pl.tradeengine.domain.util;

import pl.tradeengine.domain.model.PriceCandle;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceCandleUtils {

    public static BigDecimal hlc3(PriceCandle candle) {
        return candle.high()
                .add(candle.low())
                .add(candle.close())
                .divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
    }
}
