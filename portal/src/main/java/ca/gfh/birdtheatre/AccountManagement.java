package ca.gfh.birdtheatre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Shared account lifecycle for Windows, web, Android and iOS clients. */
@RestController
@RequestMapping("/api/v1")
class AccountManagement {
    private static final int MAX_CODE_ATTEMPTS=8;
    private static final Duration CODE_LIFETIME=Duration.ofMinutes(15);
    private static final Duration CODE_COOLDOWN=Duration.ofSeconds(60);
    private final AccountRepo accounts;
    private final AccountActionRepo codes;
    private final SessionRepo sessions;
    private final Auth auth;
    private final AccountEmail email;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
    private final SecureRandom random=new SecureRandom();

    AccountManagement(AccountRepo accounts,AccountActionRepo codes,
            SessionRepo sessions,Auth auth,AccountEmail email) {
        this.accounts=accounts; this.codes=codes; this.sessions=sessions;
        this.auth=auth; this.email=email;
    }

    record Registration(@NotBlank @Pattern(regexp="[a-zA-Z0-9_]{3,32}") String handle,
        @NotBlank @Size(min=2,max=80) String displayName,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=128) String password) {}
    record CodeInput(@NotBlank String handle,@NotBlank String code) {}
    record RecoveryStart(@NotBlank String account) {}
    record UsernameReminder(@NotBlank @Email String email) {}
    record RecoveryComplete(@NotBlank String handle,@NotBlank String code,
                            @NotBlank @Size(min=12,max=128) String newPassword) {}
    record ProfileInput(@NotBlank @Size(min=2,max=80) String displayName,
        @Size(max=80) String region,@Size(max=420) String bio,boolean publicProfile) {}
    record PasswordChange(@NotBlank String currentPassword,
        @NotBlank @Size(min=12,max=128) String newPassword) {}

    @GetMapping("/auth/options")
    Map<String,Object> options() {
        return Map.of("emailConfigured",email.configured(),
            "emailVerificationRequired",true,
            "passwordRecoveryAvailable",email.configured());
    }

    @PostMapping("/auth/register/start")
    @Transactional
    Map<String,Object> register(@Valid @RequestBody Registration input) {
        ensureMail();
        String handle=normalizedHandle(input.handle());
        String address=input.email().trim().toLowerCase(Locale.ROOT);
        if(accounts.findByHandle(handle).isPresent() || accounts.existsByEmailIgnoreCase(address))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This username or email is already registered.");
        AccountActionCode action=prepare(handle,"REGISTER",address);
        action.displayName=input.displayName().trim();
        action.passwordHash=encoder.encode(input.password());
        String code=code();
        action.codeHash=encoder.encode(code);
        codes.save(action);
        email.sendCode(address,"REGISTER",code);
        return Map.of("message","Verification code sent. Enter it within 15 minutes.",
            "handle",handle,"verificationRequired",true);
    }

    @PostMapping("/auth/register/verify")
    @Transactional(noRollbackFor=ResponseStatusException.class)
    Object verifyRegistration(@Valid @RequestBody CodeInput input) {
        String handle=normalizedHandle(input.handle());
        AccountActionCode action=verifyCode(handle,"REGISTER",input.code());
        if(accounts.findByHandle(handle).isPresent() || accounts.existsByEmailIgnoreCase(action.email))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Account already registered");
        Account account=new Account(handle,action.displayName,action.passwordHash);
        account.email=action.email; account.emailVerified=true;
        account.region=""; account.bio=""; account.publicProfile=false;
        accounts.save(account);
        codes.delete(action);
        return auth.session(account);
    }

    @PostMapping("/auth/recovery/start")
    @Transactional
    Map<String,Object> startRecovery(@Valid @RequestBody RecoveryStart input) {
        ensureMail();
        String key=input.account().trim().toLowerCase(Locale.ROOT);
        Account account=key.contains("@") ? accounts.findByEmailIgnoreCase(key).orElse(null)
            : accounts.findByHandle(key).orElse(null);
        if(account!=null && account.email!=null && Boolean.TRUE.equals(account.emailVerified)) {
            AccountActionCode prior=codes.findByHandleAndPurpose(account.handle,"RESET").orElse(null);
            if(prior==null || prior.createdAt.isBefore(Instant.now().minus(CODE_COOLDOWN))) {
                AccountActionCode action=prepare(account.handle,"RESET",account.email);
                String code=code(); action.codeHash=encoder.encode(code); codes.save(action);
                email.sendCode(account.email,"RESET",code);
            }
        }
        return Map.of("message","If this account has a verified email, a reset code will be sent.");
    }

    @PostMapping("/auth/recovery/username")
    Map<String,Object> remindUsername(@Valid @RequestBody UsernameReminder input) {
        ensureMail();
        Account account=accounts.findByEmailIgnoreCase(input.email().trim()).orElse(null);
        if(account!=null && Boolean.TRUE.equals(account.emailVerified))
            email.sendUsername(account.email,account.handle);
        return Map.of("message","If this email has an account, a username reminder will be sent.");
    }

    @PostMapping("/auth/recovery/complete")
    @Transactional(noRollbackFor=ResponseStatusException.class)
    Map<String,Object> completeRecovery(@Valid @RequestBody RecoveryComplete input) {
        String handle=normalizedHandle(input.handle());
        AccountActionCode action=verifyCode(handle,"RESET",input.code());
        Account account=accounts.findByHandle(handle).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired code"));
        account.passwordHash=encoder.encode(input.newPassword());
        accounts.save(account);
        sessions.deleteByOwnerId(account.id);
        codes.delete(action);
        return Map.of("changed",true,"message","Password changed. Sign in again.");
    }

    @GetMapping("/me/profile")
    Map<String,Object> profile(@RequestHeader(value="Authorization",required=false) String authorization) {
        return profileData(auth.require(authorization));
    }

    @PutMapping("/me/profile")
    @Transactional
    Map<String,Object> updateProfile(
            @RequestHeader(value="Authorization",required=false) String authorization,
            @Valid @RequestBody ProfileInput input) {
        Account a=auth.require(authorization);
        a.displayName=input.displayName().trim();
        a.region=input.region()==null ? "" : input.region().trim();
        a.bio=input.bio()==null ? "" : input.bio().trim();
        a.publicProfile=input.publicProfile();
        return profileData(accounts.save(a));
    }

    @PostMapping("/me/password")
    @Transactional
    Object changePassword(
            @RequestHeader(value="Authorization",required=false) String authorization,
            @Valid @RequestBody PasswordChange input) {
        Account a=auth.require(authorization);
        if(!encoder.matches(input.currentPassword(),a.passwordHash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Current password is incorrect");
        if(encoder.matches(input.newPassword(),a.passwordHash))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a different password");
        a.passwordHash=encoder.encode(input.newPassword());
        accounts.save(a);
        sessions.deleteByOwnerId(a.id);
        return auth.session(a);
    }

    @GetMapping("/members/{handle}")
    Map<String,Object> publicProfile(@PathVariable String handle) {
        Account a=accounts.findByHandle(normalizedHandle(handle)).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Member not found"));
        if(!Boolean.TRUE.equals(a.publicProfile))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Member not found");
        return Map.of("handle",a.handle,"displayName",a.displayName,
            "region",a.region==null?"":a.region,"bio",a.bio==null?"":a.bio,
            "joinedAt",a.joinedAt);
    }

    private Map<String,Object> profileData(Account a) {
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("id",a.id);value.put("handle",a.handle);
        value.put("displayName",a.displayName);
        value.put("email",a.email==null?"":a.email);
        value.put("emailVerified",Boolean.TRUE.equals(a.emailVerified));
        value.put("region",a.region==null?"":a.region);
        value.put("bio",a.bio==null?"":a.bio);
        value.put("publicProfile",Boolean.TRUE.equals(a.publicProfile));
        value.put("joinedAt",a.joinedAt);
        return value;
    }

    private String normalizedHandle(String handle) {
        if(handle==null || !handle.trim().matches("[A-Za-z0-9_]{3,32}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid username");
        return handle.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureMail() {
        if(!email.configured())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Email verification is not yet configured for Bird Theatre.");
    }

    private String code() { return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000)); }

    private AccountActionCode prepare(String handle,String purpose,String address) {
        AccountActionCode old=codes.findByHandleAndPurpose(handle,purpose).orElse(null);
        if(old!=null && old.createdAt.isAfter(Instant.now().minus(CODE_COOLDOWN)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Please wait a minute before requesting another code.");
        if(old!=null)codes.delete(old);
        AccountActionCode next=new AccountActionCode();
        next.handle=handle;next.purpose=purpose;next.email=address;
        next.createdAt=Instant.now();next.expiresAt=next.createdAt.plus(CODE_LIFETIME);
        return next;
    }

    private AccountActionCode verifyCode(String handle,String purpose,String candidate) {
        AccountActionCode action=codes.findByHandleAndPurpose(handle,purpose).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired code"));
        if(action.expiresAt.isBefore(Instant.now()) || action.attempts>=MAX_CODE_ATTEMPTS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired code");
        if(candidate==null || !candidate.matches("[0-9]{6}") || !encoder.matches(candidate,action.codeHash)) {
            action.attempts++;
            codes.save(action);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired code");
        }
        return action;
    }
}
