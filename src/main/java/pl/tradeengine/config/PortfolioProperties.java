package pl.tradeengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration of the portfolio module.
 *
 * <p>The repository is public, so every address and key comes from an environment variable with
 * an empty default: a missing variable makes the app log a warning and skip that wallet, never
 * fail to start.
 *
 * <p>The YAML key for the native coin is {@code native-asset} rather than {@code native},
 * because {@code native} is a Java keyword and cannot name a property.
 */
@ConfigurationProperties(prefix = "portfolio")
@Getter
@Setter
public class PortfolioProperties {

    /** Turns the hourly scheduler on. Manual snapshots work regardless. */
    private boolean enabled = false;

    /** Cron for the hourly snapshot, evaluated in UTC. */
    private String cron = "0 0 * * * *";

    /**
     * Jak stare ilości wolno jeszcze przenieść, gdy źródło padnie. Wartość starsza niż to
     * okno nie jest używana — mogła zostać w tym czasie sprzedana, a zła liczba jest gorsza
     * niż uczciwa dziura.
     */
    private Duration carryForwardMaxAge = Duration.ofDays(7);

    private Cmc cmc = new Cmc();

    private Evm evm = new Evm();

    private Blockbook blockbook = new Blockbook();

    private Hyperliquid hyperliquid = new Hyperliquid();

    private Manual manual = new Manual();

    private Coinbase coinbase = new Coinbase();

    private Near near = new Near();

    @Getter
    @Setter
    public static class Cmc {
        private String apiKey = "";
        private String baseUrl = "https://pro-api.coinmarketcap.com";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);
    }

    @Getter
    @Setter
    public static class Evm {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);
        /** Keyed by network name as it appears in the API, e.g. {@code ethereum}. */
        private Map<String, Network> networks = new LinkedHashMap<>();
        private List<Wallet> wallets = new ArrayList<>();
    }

    /**
     * Salda na Coinbase, czytane przez Advanced Trade API kluczem CDP.
     *
     * <p>Klucz prywatny jest sekretem w pełnym tego słowa znaczeniu — daje dostęp do konta
     * w zakresie uprawnień klucza. Dlatego w śledzonym YAML-u stoi placeholder, a wartość
     * przychodzi z zewnątrz, i dlatego warto, by klucz miał uprawnienia tylko do odczytu.
     */
    @Getter
    @Setter
    public static class Coinbase {
        private String baseUrl = "https://api.coinbase.com";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(20);
        /** Nazwa klucza w formacie {@code organizations/{org}/apiKeys/{id}}. */
        private String keyName = "";
        /** Klucz prywatny EC w PEM (SEC1). Dopuszczalne \n jako separator linii. */
        private String privateKey = "";
        private String label = "Coinbase";
        private String network = "coinbase";
        /** Whitelista walut; pyłek z opłat i konwersji zostaje poza portfelem. */
        private List<CoinbaseCurrency> currencies = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class CoinbaseCurrency {
        /** Kod, jakim Coinbase nazywa walutę, np. {@code FET}. */
        private String code;
        /** Symbol pokazywany w portfelu; zwykle ten sam co kod. */
        private String asset;
        private Integer cmcId;
        /** Poniżej tej ilości pozycja jest pomijana jako pyłek. */
        private BigDecimal minAmount = BigDecimal.ZERO;
    }

    /**
     * Ilości wpisane ręcznie, dla tego, czego nie da się odczytać automatycznie.
     *
     * <p>Z konfiguracji pochodzi wyłącznie ilość — wycena idzie normalną ścieżką przez
     * CoinMarketCap. Kwoty są danymi o majątku, więc w śledzonym YAML-u stoi placeholder,
     * a wartość podaje się z zewnątrz, jak adresy.
     */
    @Getter
    @Setter
    public static class Manual {
        private List<ManualHolding> holdings = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ManualHolding {
        /** Stabilny identyfikator; pod nim adresowałby wpis przyszły endpoint PUT. */
        private String key;
        /** Etykieta źródła — ta sama, pod którą ma się pojawić w podziale portfela. */
        private String source;
        private String network;
        private String asset;
        private Integer cmcId;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    /**
     * Capital on Hyperliquid, read from its public {@code /info} API by address — no key.
     *
     * <p>Spot, perp equity and staking land on one network name and are kept apart by asset.
     * Unit's wrapped tokens are priced as what they represent, which the API states in
     * {@code fullName}: UETH is "Unit Ethereum", HPENGU is "Pudgy Penguins".
     */
    @Getter
    @Setter
    public static class Hyperliquid {
        private String baseUrl = "https://api.hyperliquid.xyz";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);
        /** Shown verbatim as {@code positions[].network}. */
        private String network = "hyperliquid";
        private List<HyperliquidWallet> wallets = new ArrayList<>();
        /** Whitelist of spot tokens; anything else in the balance list is ignored. */
        private List<SpotToken> spotTokens = new ArrayList<>();
        /** Asset used to express perp account equity, which is USD rather than a token. */
        private AssetRef perps = new AssetRef();
        /** Asset used for delegated (staked) HYPE. */
        private AssetRef staking = new AssetRef();
        /** Powiadomienia o zrealizowanych zleceniach — osobny przebieg, ten sam adres. */
        private FillAlerts fillAlerts = new FillAlerts();
    }

    /**
     * Alerty o realizacji zlecen na Hyperliquid: spot i perpy, odpytywane cyklicznie.
     *
     * <p>Domyslnie wylaczone, bo to jedyna czesc modulu, ktora sama z siebie wysyla wiadomosci.
     */
    @Getter
    @Setter
    public static class FillAlerts {
        private boolean enabled = false;
        /**
         * Odstep miedzy przebiegami. Zapytanie wazy 20 przy budzecie 1200 na minute, wiec 45 s
         * to okolo 3% limitu — gestszy polling tez jest bezpieczny.
         */
        private Duration pollInterval = Duration.ofSeconds(45);
        /**
         * Ile wiadomosci wolno wyslac w jednym przebiegu. Nadwyzka jest zapisywana jako
         * obsluzona i kwitowana jedna linia podsumowania.
         */
        private int maxMessagesPerRun = 10;
        /**
         * Jak daleko w przeszlosc zaglada pierwszy przebieg. Te fille sa zapamietywane BEZ
         * powiadamiania — inaczej wlaczenie alertow wysypaloby na Telegram cala historie.
         */
        private Duration bootstrapLookback = Duration.ofHours(24);
        /** Po tym czasie wiersz o obsluzonym fillu jest usuwany; tabela nie rosnie bez konca. */
        private Duration retention = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class HyperliquidWallet {
        private String label;
        private String address;
    }

    @Getter
    @Setter
    public static class SpotToken {
        /** Ticker as Hyperliquid reports it in spot balances, e.g. {@code UETH}. */
        private String coin;
        /** What it is worth, e.g. {@code ETH}. Shown as {@code positions[].asset}. */
        private String asset;
        private Integer cmcId;
    }

    @Getter
    @Setter
    public static class AssetRef {
        private String asset;
        private Integer cmcId;
    }

    /**
     * Staking natywny NEAR plus wolne saldo tego samego konta, czytane z lancucha.
     *
     * <p>Dwa hosty, bo to dwie rozne rzeczy: {@code api-base-url} odkrywa pule walidatorow,
     * a {@code rpc-url} czyta salda. Oba publiczne i bez klucza, wiec jedyna wartosc
     * przychodzaca z zewnatrz to {@code account-id}.
     *
     * <p>Liczby miejsc nie ma w konfiguracji: yoctoNEAR to 24 miejsca ustalone przez protokol,
     * wiec stoi w {@code NearClient} jak {@code NATIVE_DECIMALS} w kliencie EVM. Blockbook ma
     * je konfigurowalne tylko dlatego, ze obsluguje lancuchy o roznych jednostkach.
     */
    @Getter
    @Setter
    public static class Near {
        /** JSON-RPC. UWAGA: NEAR zglasza bledy pod HTTP 200, w polu error. */
        private String rpcUrl = "https://free.rpc.fastnear.com";
        /** Odkrywanie pul walidatorow — inny host niz RPC. */
        private String apiBaseUrl = "https://api.fastnear.com";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);
        /** Shown verbatim as {@code positions[].network}. */
        private String network = "near";
        private String asset = "NEAR";
        private Integer cmcId = 6535;
        private List<NearWallet> wallets = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class NearWallet {
        /** Display label; shares the namespace with every other source's label. */
        private String label;
        /** Konto NEAR — nazwane ({@code x.near}) albo niejawne, 64 znaki hex. */
        private String accountId;
    }

    /** UTXO chains read from an xpub through a Blockbook server. */
    @Getter
    @Setter
    public static class Blockbook {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(20);
        /** Trezor's servers sit behind a bot filter that rejects requests without one. */
        private String userAgent = "tradeengine/1.0";
        /** Keyed by network name as it appears in the API, e.g. {@code bitcoin}. */
        private Map<String, BlockbookNetwork> networks = new LinkedHashMap<>();
        private List<XpubWallet> wallets = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class BlockbookNetwork {
        private String baseUrl;
        private String asset;
        private Integer cmcId;
        /** Smallest-unit exponent of the chain: 8 for Bitcoin and Bitcoin Cash. */
        private Integer decimals = 8;
    }

    @Getter
    @Setter
    public static class XpubWallet {
        /** Display label; shares the namespace with EVM wallets, so "Trezor" covers both. */
        private String label;
        /** Extended public key. Reveals the whole wallet, so it only ever comes from outside the repo. */
        private String xpub;
        private String network;
    }

    @Getter
    @Setter
    public static class Network {
        private String rpcUrl;
        private NativeAsset nativeAsset;
        /** Whitelist — only these ERC-20s are read. */
        private List<Token> tokens = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class NativeAsset {
        private String asset;
        private Integer cmcId;
    }

    @Getter
    @Setter
    public static class Token {
        private String asset;
        private String contract;
        private Integer decimals;
        private Integer cmcId;
    }

    @Getter
    @Setter
    public static class Wallet {
        /** Display label; several entries may share it and their positions are summed. */
        private String label;
        private String address;
        private List<String> networks = new ArrayList<>();
    }
}
