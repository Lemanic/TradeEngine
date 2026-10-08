package pl.tradeengine.domain.port.portfolio;

/** Thrown when prices could not be fetched at all. No snapshot is written in that case. */
public class PriceUnavailableException extends RuntimeException {

    public PriceUnavailableException(String message) {
        super(message);
    }

    public PriceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
