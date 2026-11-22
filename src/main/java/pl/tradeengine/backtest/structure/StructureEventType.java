package pl.tradeengine.backtest.structure;

public enum StructureEventType {
    /** A pullback traded into an FVG of the leg — the leg is armed, reaching its extreme now shifts structure. */
    FVG_TOUCH,
    /** An armed leg's extreme taken by a wick only: the protected level moves, continuation is not yet confirmed. */
    SWEEP,
    /** A close beyond the leg's extreme (at once, or after a sweep): the trend continues. */
    BOS,
    /** A close beyond the protected level: the trend flips. */
    MSS,
    /** A wick beyond the protected level that closed back inside: no MSS, the protected level moves to the wick. */
    PROTECTED_SWEEP
}
