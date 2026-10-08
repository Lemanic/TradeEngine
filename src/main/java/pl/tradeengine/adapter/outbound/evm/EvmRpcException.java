package pl.tradeengine.adapter.outbound.evm;

/** A JSON-RPC node could not be reached, or answered with an error. */
public class EvmRpcException extends RuntimeException {

    public EvmRpcException(String message) {
        super(message);
    }

    public EvmRpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
