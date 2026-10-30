package pl.tradeengine.adapter.outbound.near;

/**
 * A NEAR endpoint could not be reached, or answered with an error.
 *
 * <p>A {@code RuntimeException} on purpose: {@code PortfolioSnapshotService} catches exactly
 * that around every source, and catching it is what turns a NEAR outage into carried-forward,
 * stale positions rather than a hole in the portfolio.
 *
 * <p>Messages never quote the account id. It is not a secret the way an xpub is, but it ties
 * the dashboard's owner to an on-chain identity, and the whole module keeps addresses in
 * environment variables and out of the log.
 */
public class NearRpcException extends RuntimeException {

    public NearRpcException(String message) {
        super(message);
    }

    public NearRpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
