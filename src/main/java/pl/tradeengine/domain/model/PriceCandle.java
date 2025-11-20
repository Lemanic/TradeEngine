package pl.tradeengine.domain.model;

import java.time.ZonedDateTime;

//public record PriceCandle(
//        Symbol symbol,
//        Timeframe timeframe,
//        ZonedDateTime openTime,
//        ZonedDateTime closeTime,
//        double open,
//        double high,
//        double low,
//        double close
//) {}

import java.math.BigDecimal; // <-- DODAJ IMPORT

public record PriceCandle(
        Symbol symbol,
        Timeframe timeframe,
        ZonedDateTime openTime,
        ZonedDateTime closeTime,
        BigDecimal open,  // <-- ZMIEŃ TYP
        BigDecimal high,  // <-- ZMIEŃ TYP
        BigDecimal low,   // <-- ZMIEŃ TYP
        BigDecimal close  // <-- ZMIEŃ TYP
) {}
