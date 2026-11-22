package pl.tradeengine.backtest.trade;

public enum ExitReason {
    TP,
    SL,
    /** SL moved to entry after the break-even trigger, then hit — about 0R, less on a gap. */
    BE,
    /** Still open on the last bar of data; marked at the last close and left out of the statistics. */
    OPEN_AT_END
}
