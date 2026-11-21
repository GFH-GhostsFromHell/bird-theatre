package ca.gfh.birdtheatre;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

@Service
class Auth {
    private final AccountRepo accounts;
    private final SessionRepo sessions;
    private final BCryptPasswordEncoder password = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final boolean legacySignupEnabled;
    Auth(AccountRepo accounts, SessionRepo sessions,
         @Value("${bird.auth.legacy-signup-enabled:false}") boolean legacySignupEnabled) {
        this.accounts=accounts;this.sessions=sessions;
        this.legacySignupEnabled=legacySignupEnabled;
    }
    String secret() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    String sha(String input) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    Account require(String authHeader) {
        if (authHeader==null || !authHeader.startsWith("Bearer ") || authHeader.length()>300)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        return sessions.findByTokenHashAndExpiresAtAfter(sha(authHeader.substring(7)),Instant.now())
            .map(session->session.owner)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired"));
    }
    Account maybe(String authHeader) {
        try { return require(authHeader); } catch (ResponseStatusException e) { return null; }
    }
    Object signup(String handle, String name, String pass) {
        if (!legacySignupEnabled) throw new ResponseStatusException(HttpStatus.GONE,
            "Use verified account registration instead");
        handle=handle.toLowerCase(java.util.Locale.ROOT).trim();
        if (accounts.findByHandle(handle).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Handle already taken");
        Account a=accounts.save(new Account(handle,name.trim(),password.encode(pass)));
        return session(a);
    }
    Object login(String handle,String pass) {
        Account a=accounts.findByHandle(handle.toLowerCase(java.util.Locale.ROOT).trim())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials"));
        if (!password.matches(pass,a.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
        if (a.email!=null && !a.email.isBlank() && !Boolean.TRUE.equals(a.emailVerified))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Verify your email to sign in");
        return session(a);
    }
    Object session(Account a) {
        String token=secret();
        sessions.save(new AccountSession(a,sha(token),Instant.now().plus(14,ChronoUnit.DAYS)));
        return java.util.Map.of("token",token,"handle",a.handle,"displayName",a.displayName);
    }
    void logout(String authorization) {
        if (authorization!=null && authorization.startsWith("Bearer ")) {
            sessions.findByTokenHashAndExpiresAtAfter(sha(authorization.substring(7)),Instant.now())
                .ifPresent(sessions::delete);
        }
    }
    void checkDevice(Camera camera,String token) {
        if (token==null || token.length()>200 ||
            !MessageDigest.isEqual(camera.ingestTokenHash.getBytes(StandardCharsets.UTF_8),sha(token).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid camera device token");
        }
    }
    void own(Camera camera,Account user) {
        if (!camera.owner.id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Camera belongs to another member");
    }
}
