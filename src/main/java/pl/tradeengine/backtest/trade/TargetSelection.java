package pl.tradeengine.backtest.trade;

/** Which open FVG of the current leg becomes the TP, once the timeframe is chosen. */
public enum TargetSelection {
    /** The zone reaching highest — the leg's far end. Bigger R, reached less often. */
    HIGHEST,
    /** The zone whose upper edge is closest above price — the first one price has to eat. */
    NEAREST
}
