package pl.tradeengine.adapter.outbound.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Klucz uzyty tutaj jest jednorazowy, wygenerowany na potrzeby tego testu — nigdy nie wstawiaj
 * tu klucza z konfiguracji, nawet uniewaznionego.
 */
class CoinbaseJwtTest {

    private static final String PRIVATE_PEM = CoinbaseTestKeys.PRIVATE_PEM;
    private static final String PUBLIC_PEM = CoinbaseTestKeys.PUBLIC_PEM;

    private static final String KEY_NAME = "organizations/org-1/apiKeys/key-1";
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private final CoinbaseJwt jwt =
            new CoinbaseJwt(KEY_NAME, PRIVATE_PEM, Clock.fixed(NOW, ZoneOffset.UTC));

    private static JsonNode decode(String part) throws Exception {
        return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(part));
    }

    @Test
    void buildsTheHeaderCoinbaseExpects() throws Exception {
        JsonNode header = decode(jwt.tokenFor("GET", "api.coinbase.com", "/x").split("\\.")[0]);

        assertThat(header.get("alg").asText()).isEqualTo("ES256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");
        assertThat(header.get("kid").asText()).isEqualTo(KEY_NAME);
        // nonce chroni przed powtorzeniem zapytania, wiec musi byc za kazdym razem inny
        assertThat(header.get("nonce").asText()).hasSize(32);
    }

    @Test
    void buildsThePayloadWithTheUriClaimBoundToOneRequest() throws Exception {
        JsonNode payload = decode(
                jwt.tokenFor("GET", "api.coinbase.com", "/api/v3/brokerage/accounts").split("\\.")[1]);

        assertThat(payload.get("iss").asText()).isEqualTo("cdp");
        assertThat(payload.get("sub").asText()).isEqualTo(KEY_NAME);
        assertThat(payload.get("nbf").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(payload.get("exp").asLong()).isEqualTo(NOW.getEpochSecond() + 120);
        // host bez schematu, sciezka bez query — inaczej Coinbase oddaje gole 401
        assertThat(payload.get("uri").asText())
                .isEqualTo("GET api.coinbase.com/api/v3/brokerage/accounts");
    }

    @Test
    void signatureIsRawSixtyFourBytesAndVerifiesAgainstThePublicKey() throws Exception {
        String token = jwt.tokenFor("GET", "api.coinbase.com", "/api/v3/brokerage/accounts");
        String[] parts = token.split("\\.");
        byte[] raw = Base64.getUrlDecoder().decode(parts[2]);

        // JWS wymaga surowego R||S; podpis w DER przeszedlby walidacje ksztaltu i nie zweryfikowal sie
        assertThat(raw).hasSize(64);

        byte[] der = rawToDer(raw);
        PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(PUBLIC_PEM.replace("\\n", "")
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", ""))));
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertThat(verifier.verify(der)).isTrue();
    }

    @Test
    void producesADifferentTokenEveryTime() {
        assertThat(jwt.tokenFor("GET", "api.coinbase.com", "/x"))
                .isNotEqualTo(jwt.tokenFor("GET", "api.coinbase.com", "/x"));
    }

    @Test
    void acceptsAPemWrittenWithEscapedNewlines() {
        String escaped = PRIVATE_PEM.replace("\n", "\\n");

        assertThat(new CoinbaseJwt(KEY_NAME, escaped, Clock.fixed(NOW, ZoneOffset.UTC))
                .tokenFor("GET", "api.coinbase.com", "/x")).contains(".");
    }

    @Test
    void rejectsAKeyThatIsNotAnEcPem() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThatThrownBy(() -> new CoinbaseJwt(KEY_NAME, "", clock))
                .isInstanceOf(CoinbaseException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> new CoinbaseJwt(KEY_NAME, "nie-base64!!!", clock))
                .isInstanceOf(CoinbaseException.class);
        assertThatThrownBy(() -> new CoinbaseJwt(KEY_NAME,
                "-----BEGIN EC PRIVATE KEY-----\nQUJD\n-----END EC PRIVATE KEY-----", clock))
                .isInstanceOf(CoinbaseException.class);
    }

    @Test
    void convertsDerSignaturesWithTheHighBitSetWithoutLosingBytes() {
        // BigInteger dokleja zerowy bajt, zeby liczba byla dodatnia, wiec polowka w DER ma wtedy
        // 33 bajty. Bez obslugi tego podpis wychodzilby przesuniety i Coinbase odrzucalby go
        // tylko czasem — wtedy, gdy wylosowane r albo s zaczyna sie od bajtu >= 0x80.
        byte[] raw = new byte[64];
        raw[0] = (byte) 0xFF;
        raw[32] = (byte) 0xFE;
        for (int i = 1; i < 32; i++) {
            raw[i] = (byte) i;
            raw[32 + i] = (byte) (0x40 + i);
        }

        byte[] der = rawToDer(raw);
        assertThat(der[3]).as("polowka r ma w DER 33 bajty").isEqualTo((byte) 33);

        assertThat(CoinbaseJwt.derToJose(der)).isEqualTo(raw);
    }

    @Test
    void convertsDerSignaturesWithLeadingZeroesBackToFixedWidth() {
        // odwrotny skraj: male r/s zajmuja w DER mniej niz 32 bajty i trzeba je dopelnic z przodu
        byte[] raw = new byte[64];
        raw[31] = 0x07;
        raw[63] = 0x09;

        assertThat(CoinbaseJwt.derToJose(rawToDer(raw))).isEqualTo(raw);
    }

    /** Odwrotnosc derToJose, tylko po to, zeby JCA mogla zweryfikowac podpis w tescie. */
    private static byte[] rawToDer(byte[] raw) {
        java.math.BigInteger r = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(raw, 0, 32));
        java.math.BigInteger s = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(raw, 32, 64));
        byte[] rb = r.toByteArray(), sb = s.toByteArray();
        byte[] der = new byte[6 + rb.length + sb.length];
        der[0] = 0x30; der[1] = (byte) (4 + rb.length + sb.length);
        der[2] = 0x02; der[3] = (byte) rb.length;
        System.arraycopy(rb, 0, der, 4, rb.length);
        der[4 + rb.length] = 0x02; der[5 + rb.length] = (byte) sb.length;
        System.arraycopy(sb, 0, der, 6 + rb.length, sb.length);
        return der;
    }
}
