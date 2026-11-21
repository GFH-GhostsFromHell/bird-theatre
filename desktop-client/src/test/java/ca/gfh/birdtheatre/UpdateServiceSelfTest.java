package ca.gfh.birdtheatre;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

/** Offline regression tests for version ordering, signed manifests and release URL restrictions. */
public class UpdateServiceSelfTest {
    public static void main(String[] args) throws Exception {
        assertTrue(UpdateService.compareVersions("0.1.10", "0.1.9") > 0, "version update");
        assertTrue(UpdateService.compareVersions("0.2.0", "0.1.99") > 0, "major/minor");
        assertTrue(UpdateService.compareVersions("0.1.4", "0.1.4") == 0, "same version");
        assertTrue(UpdateService.compareVersions("0.1.2", "0.1.10") < 0, "older version");

        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String key = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String json = manifest("0.1.10", UpdateService.RELEASE_PREFIX + "0.1.10/" + UpdateService.ASSET_NAME);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(pair, payload);
        byte[] signatureBase64 = Base64.getEncoder().encode(signature);
        UpdateService.Manifest verified = UpdateService.verifyManifest(payload, signatureBase64, UpdateService.readPublicKey(key));
        assertTrue(verified.version().equals("0.1.10"), "valid signed update");

        payload[0] ^= 1;
        expectFailure(() -> UpdateService.verifyManifest(payload, signatureBase64, pair.getPublic()), "tampered manifest rejected");
        String hostile = manifest("0.1.10", "https://example.com/fake.msi");
        expectFailure(() -> UpdateService.verifyManifest(hostile.getBytes(StandardCharsets.UTF_8),
            Base64.getEncoder().encode(sign(pair, hostile.getBytes(StandardCharsets.UTF_8))), pair.getPublic()),
            "unexpected download host rejected despite valid signature");
        byte[] bogusSig = Base64.getEncoder().encode(new byte[64]);
        expectFailure(() -> UpdateService.verifyManifest(json.getBytes(StandardCharsets.UTF_8), bogusSig, pair.getPublic()),
            "invalid signature rejected");
        System.out.println("Bird Theatre updater self-tests passed (7 checks)");
    }

    private static String manifest(String version, String url) {
        return "{\"version\":\"" + version + "\",\"url\":\"" + url +
            "\",\"sha256\":\"" + "a".repeat(64) + "\"}";
    }
    private static byte[] sign(KeyPair pair, byte[] payload) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        return signer.sign();
    }
    private static void expectFailure(Checked action, String message) throws Exception {
        try { action.run(); } catch (SecurityException | java.io.IOException ok) { return; }
        throw new AssertionError(message);
    }
    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
