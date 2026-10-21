package pl.tradeengine.adapter.outbound.blockbook;

/**
 * A Blockbook server could not be reached, or answered with an error.
 *
 * <p>Messages deliberately carry the network, never the request URL: the xpub sits in the path,
 * and an xpub reveals a wallet's whole balance and history, so it must not reach the log.
 */
public class BlockbookException extends RuntimeException {

    public BlockbookException(String message) {
        super(message);
    }

    public BlockbookException(String message, Throwable cause) {
        super(message, cause);
    }
}
