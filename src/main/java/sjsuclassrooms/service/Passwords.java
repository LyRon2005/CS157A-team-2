package sjsuclassrooms.service;

// Creates password hashes and checks passwords during login.
// The database stores a hash, not the original password.

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    // This class has only static helper methods. No object needs to be created.
    private Passwords() {}
    // Use a new random salt for every account, even when two users choose the same password.
    // Store algorithm, iteration count, salt and hash together so verify() can read them.
    public static String hash(String password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    // Check the stored format before doing expensive hash work.
    // MessageDigest.isEqual compares the hash bytes without an early mismatch return.
    public static boolean verify(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int rounds = Integer.parseInt(parts[1]);
            if (rounds < ITERATIONS || rounds > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]), hash = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || hash.length != 32) return false;
            return MessageDigest.isEqual(hash, derive(password, salt, rounds));
        } catch (IllegalArgumentException e) { return false; }
    }
    // PBKDF2 repeats the hash work to make password guessing more expensive.
    // Clear temporary character arrays when possible. Java String values cannot be wiped here.
    private static byte[] derive(String password, byte[] salt, int rounds) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, rounds, 256); Arrays.fill(chars, '\0');
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Password hashing unavailable", e); }
        finally { spec.clearPassword(); }
    }
}
