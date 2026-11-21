import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Local-only publisher key generation; never commits or writes the secret to disk. */
class GenerateUpdateKeys {
    public static void main(String[] args) throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Base64.Encoder encoder = Base64.getEncoder();
        System.out.println("Bird Theatre Ed25519 update publisher keys (keep the private value secret):");
        System.out.println("GitHub Actions SECRET  UPDATE_SIGNING_PRIVATE_KEY_BASE64:");
        System.out.println(encoder.encodeToString(pair.getPrivate().getEncoded()));
        System.out.println("GitHub Actions VARIABLE UPDATE_SIGNING_PUBLIC_KEY_BASE64:");
        System.out.println(encoder.encodeToString(pair.getPublic().getEncoded()));
        System.out.println("Store the private key in a password manager. Never commit, email, or share it.");
    }
}
