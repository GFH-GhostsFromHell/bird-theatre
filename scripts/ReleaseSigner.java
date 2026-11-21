import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

/** Standalone Java 21 manifest signer; keys are read ONLY from CI environment variables. */
class ReleaseSigner {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java ReleaseSigner.java manifest.json manifest.sig");
        String secret = System.getenv("UPDATE_SIGNING_PRIVATE_KEY_BASE64");
        String publicText = System.getenv("UPDATE_SIGNING_PUBLIC_KEY_BASE64");
        if (secret == null || secret.isBlank() || publicText == null || publicText.isBlank())
            throw new SecurityException("Release signing keys not configured");
        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = keyFactory.generatePrivate(
            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(secret.trim())));
        PublicKey publicKey = keyFactory.generatePublic(
            new X509EncodedKeySpec(Base64.getDecoder().decode(publicText.trim())));
        byte[] manifest = Files.readAllBytes(Path.of(args[0]));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(manifest);
        byte[] signed = signer.sign();
        Signature checker = Signature.getInstance("Ed25519");
        checker.initVerify(publicKey);
        checker.update(manifest);
        if (!checker.verify(signed)) throw new SecurityException("Public/private update keys do not match");
        Files.writeString(Path.of(args[1]), Base64.getEncoder().encodeToString(signed) + "\n", StandardCharsets.US_ASCII);
        System.out.println("Signed release manifest and verified publisher key (Ed25519)");
    }
}
