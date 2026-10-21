package pl.tradeengine.adapter.outbound.evm;

import java.util.List;

/** Minimal JSON-RPC 2.0 envelope — only what {@link EvmBalanceClient} needs. */
final class JsonRpc {

    private JsonRpc() {}

    record Request(String jsonrpc, long id, String method, List<Object> params) {}

    record Response(String jsonrpc, Long id, String result, Error error) {}

    record Error(Integer code, String message) {}
}
