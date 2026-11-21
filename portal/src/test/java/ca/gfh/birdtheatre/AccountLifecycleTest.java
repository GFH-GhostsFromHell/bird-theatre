package ca.gfh.birdtheatre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real auth services and database while replacing SMTP with a mock sender. */
@SpringBootTest(properties={"bird.mail.from=test-sender@example.test", "bird.mail.smtp-enabled=true"})
@AutoConfigureMockMvc
class AccountLifecycleTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender sender;

    private JsonNode postJson(String path, Object payload, int expectedStatus) throws Exception {
        String body=json.writeValueAsString(payload);
        String response=mvc.perform(post(path)
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String captureCode(int count) {
        var capture=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender,times(count)).send(capture.capture());
        String message=capture.getAllValues().get(count-1).getText();
        Matcher match=Pattern.compile("code is: ([0-9]{6})").matcher(message);
        assertTrue(match.find(),"Expected a six-digit email code");
        return match.group(1);
    }

    @Test void verifiedRegistrationProfilesAndPasswordRecovery() throws Exception {
        String handle="birder"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String address=handle+"@example.test";
        mvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of(
                "handle",handle,"displayName","Unsafe", "password","bad-password-1234"))))
            .andExpect(status().isGone());
        JsonNode registration=postJson("/api/v1/auth/register/start",
            Map.of("handle",handle,"email",address,"displayName","Bird Keeper",
                "password","original-password-123"),200);
        assertTrue(registration.get("verificationRequired").asBoolean());
        String code=captureCode(1);
        postJson("/api/v1/auth/register/verify",Map.of("handle",handle,"code","000000".equals(code)?"111111":"000000"),400);
        JsonNode account=postJson("/api/v1/auth/register/verify",Map.of("handle",handle,"code",code),200);
        String bearer="Bearer "+account.get("token").asText();
        mvc.perform(get("/api/v1/me/profile").header("Authorization",bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(address))
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.publicProfile").value(false));
        mvc.perform(get("/api/v1/members/"+handle)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/me/profile").header("Authorization",bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of(
                "displayName","Bird Keeper Updated","region","Ontario",
                "bio","Birdhouses and nesting cameras","publicProfile",true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Bird Keeper Updated"));
        mvc.perform(get("/api/v1/members/"+handle)).andExpect(status().isOk())
            .andExpect(jsonPath("$.region").value("Ontario"))
            .andExpect(jsonPath("$.email").doesNotExist());

        JsonNode changed=json.readTree(mvc.perform(post("/api/v1/me/password")
            .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of(
                "currentPassword","original-password-123",
                "newPassword","changed-password-123"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        String updatedBearer="Bearer "+changed.get("token").asText();
        mvc.perform(get("/api/v1/me/profile").header("Authorization",bearer))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/profile").header("Authorization",updatedBearer))
            .andExpect(status().isOk());

        postJson("/api/v1/auth/recovery/start",Map.of("account",address),200);
        String resetCode=captureCode(2);
        postJson("/api/v1/auth/recovery/complete",Map.of(
            "handle",handle,"code",resetCode,"newPassword","reset-password-123"),200);
        mvc.perform(get("/api/v1/me/profile").header("Authorization",updatedBearer))
            .andExpect(status().isUnauthorized());
        JsonNode signedIn=postJson("/api/v1/auth/login",Map.of(
            "handle",handle,"password","reset-password-123"),200);
        assertNotNull(signedIn.get("token").asText());
        mvc.perform(get("/api/v1/me/profile")
            .header("Authorization","Bearer "+signedIn.get("token").asText()))
            .andExpect(status().isOk());
    }
}
