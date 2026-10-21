package pl.tradeengine.adapter.outbound.hyperliquid;

/** Hyperliquid's info API could not be reached, or answered with something unusable. */
public class HyperliquidException extends RuntimeException {

    public HyperliquidException(String message) {
        super(message);
    }

    public HyperliquidException(String message, Throwable cause) {
        super(message, cause);
    }
}
