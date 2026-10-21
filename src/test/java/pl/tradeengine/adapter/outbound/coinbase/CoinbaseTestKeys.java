package pl.tradeengine.adapter.outbound.coinbase;

/**
 * Jednorazowa para kluczy P-256 wygenerowana wylacznie na potrzeby testow.
 * NIE wstawiaj tu klucza z konfiguracji, nawet uniewaznionego.
 */
final class CoinbaseTestKeys {

    static final String PRIVATE_PEM = "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIMy9TxnQwxOf06jXCJPxoSgO7iq+oMzz8gCTJAq804RDoAoGCCqGSM49\nAwEHoUQDQgAE2nJLiheTPSDRfMvSnH2M6Zm3qyrec/lSlLDHGRhW4F76JacExb0C\nQncRRdhlh6ce7x8+BKKaaXO+XTzTDqiQ9Q==\n-----END EC PRIVATE KEY-----";
    static final String PUBLIC_PEM = "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2nJLiheTPSDRfMvSnH2M6Zm3qyre\nc/lSlLDHGRhW4F76JacExb0CQncRRdhlh6ce7x8+BKKaaXO+XTzTDqiQ9Q==\n-----END PUBLIC KEY-----";

    private CoinbaseTestKeys() {}
}
