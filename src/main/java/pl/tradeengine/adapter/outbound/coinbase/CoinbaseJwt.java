package pl.tradeengine.adapter.outbound.coinbase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the short-lived bearer token Coinbase's CDP keys require.
 *
 * <p>Three details that are easy to get wrong and fail as a bare {@code 401}:
 * <ul>
 *   <li>the algorithm must be <b>ES256</b> (ECDSA over P-256); Ed25519 keys are not accepted;</li>
 *   <li>the {@code uri} claim is {@code "{METHOD} {HOST}{PATH}"} — host without scheme, path
 *       without query string — and a token is valid for exactly one such request;</li>
 *   <li>JCA emits ECDSA signatures in DER, while JWS wants the raw {@code R||S} pair padded to
 *       32 bytes each. Skipping that conversion produces a well-formed token that never verifies.</li>
 * </ul>
 *
 * <p>Coinbase hands out the key as a SEC1 PEM ({@code BEGIN EC PRIVATE KEY}), which
 * {@code KeyFactory} cannot read directly — it expects PKCS#8. Rather than depend on another
 * library, the 32-byte scalar is lifted out of the DER structure here.
 */
public class CoinbaseJwt {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Coinbase rejects anything older; the token covers a single request. */
    private static final int LIFETIME_SECONDS = 120;

    private final String keyName;
    private final PrivateKey privateKey;
    private final Clock clock;

    public CoinbaseJwt(String keyName, String privateKeyPem, Clock clock) {
        this.keyName = keyName;
        this.privateKey = readSec1PrivateKey(privateKeyPem);
        this.clock = clock;
    }

    /**
     * @param method HTTP method, upper case
     * @param host   host only, e.g. {@code api.coinbase.com}
     * @param path   path only, without the query string
     */
    public String tokenFor(String method, String host, String path) {
        long now = clock.instant().getEpochSecond();
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "ES256");
        header.put("typ", "JWT");
        header.put("kid", keyName);
        header.put("nonce", HexFormat.of().formatHex(nonce));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "cdp");
        payload.put("sub", keyName);
        payload.put("nbf", now);
        payload.put("exp", now + LIFETIME_SECONDS);
        payload.put("uri", method + " " + host + path);

        String signingInput = encode(header) + "." + encode(payload);
        return signingInput + "." + B64URL.encodeToString(sign(signingInput));
    }

    private static String encode(Map<String, Object> claims) {
        try {
            return B64URL.encodeToString(JSON.writeValueAsBytes(claims));
        } catch (JsonProcessingException e) {
            throw new CoinbaseException("Could not serialise the JWT", e);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return derToJose(signature.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new CoinbaseException("Could not sign the JWT: " + e.getClass().getSimpleName(), e);
        }
    }

    /** DER {@code SEQUENCE { INTEGER r, INTEGER s }} to the fixed-width {@code R||S} JWS wants. */
    static byte[] derToJose(byte[] der) {
        int index = der[1] < 0 ? 3 : 2;   // long-form length byte when the high bit is set
        byte[] out = new byte[64];
        for (int part = 0; part < 2; part++) {
            if (der[index] != 0x02) {
                throw new CoinbaseException("Signature is not a DER sequence of two integers");
            }
            int length = der[index + 1];
            BigInteger value = new BigInteger(1, der, index + 2, length);
            byte[] fixed = toFixedWidth(value);
            System.arraycopy(fixed, 0, out, part * 32, 32);
            index += 2 + length;
        }
        return out;
    }

    private static byte[] toFixedWidth(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] fixed = new byte[32];
        if (bytes.length > 32) {
            // BigInteger prepends a zero byte to keep the value positive
            System.arraycopy(bytes, bytes.length - 32, fixed, 0, 32);
        } else {
            System.arraycopy(bytes, 0, fixed, 32 - bytes.length, bytes.length);
        }
        return fixed;
    }

    /**
     * Lifts the private scalar out of a SEC1 {@code ECPrivateKey} DER:
     * {@code SEQUENCE { INTEGER 1, OCTET STRING privateKey, ... }}.
     */
    static PrivateKey readSec1PrivateKey(String pem) {
        String body = pem.replace("\\n", "\n")
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        if (body.isEmpty()) {
            throw new CoinbaseException("The Coinbase private key is empty");
        }

        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new CoinbaseException("The Coinbase private key is not valid base64");
        }

        try {
            int index = der[1] < 0 ? 3 : 2;
            if (der[index] != 0x02) {
                throw new CoinbaseException("Unexpected SEC1 structure: no version integer");
            }
            index += 2 + der[index + 1];
            if (der[index] != 0x04) {
                throw new CoinbaseException("Unexpected SEC1 structure: no private key octets");
            }
            int length = der[index + 1];
            BigInteger scalar = new BigInteger(1, der, index + 2, length);

            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec p256 = parameters.getParameterSpec(ECParameterSpec.class);

            return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(scalar, p256));
        } catch (CoinbaseException e) {
            throw e;
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            throw new CoinbaseException(
                    "Could not read the Coinbase private key (expected a SEC1 EC PEM)");
        }
    }
}
