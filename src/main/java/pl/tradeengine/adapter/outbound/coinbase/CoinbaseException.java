package pl.tradeengine.adapter.outbound.coinbase;

/**
 * Coinbase could not be reached or rejected the request.
 *
 * <p>Messages never carry the private key or the signed token — an exposed JWT is valid for two
 * minutes and an exposed key forever.
 */
public class CoinbaseException extends RuntimeException {

    public CoinbaseException(String message) {
        super(message);
    }

    public CoinbaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
