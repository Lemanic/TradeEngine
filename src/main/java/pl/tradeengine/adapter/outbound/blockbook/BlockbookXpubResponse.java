package pl.tradeengine.adapter.outbound.blockbook;

/**
 * Response of Blockbook's {@code /api/v2/xpub/{xpub}?details=basic}.
 *
 * @param balance confirmed balance in the chain's smallest unit (satoshi for BTC), as a string
 * @param error   set instead of the totals when the server rejects the request
 */
public record BlockbookXpubResponse(String balance, String error) {}
