package pl.tradeengine.domain.model;

class KeyLevel {
    Long id;
    Symbol symbol;
    Timeframe timeframe;
    double price;
    double weight;
    KeyLevelType type; // np. SWING_HIGH, ORDER_BLOCK, WEEKLY_OPEN
}