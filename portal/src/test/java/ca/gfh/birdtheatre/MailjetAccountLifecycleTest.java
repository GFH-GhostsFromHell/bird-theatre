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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end registration and reset over the mock HTTPS Mailjet provider. */
@SpringBootTest(properties={
    "bird.mail.from=sender@example.test",
    "bird.mail.mailjet.api-key=local-test-api-key",
    "bird.mail.mailjet.secret-key=local-test-secret",
    "bird.mail.smtp-enabled=false"
})
@AutoConfigureMockMvc
class MailjetAccountLifecycleTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean MailjetSendClient mailjet;

    @Test void registrationAndRecoveryUseTheHttpsProvider() throws Exception {
        String handle="mailjet"+UUID.randomUUID().toString().replace("-","").substring(0,9);
        String email=handle+"@example.test";
        mvc.perform(get("/api/v1/auth/options"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailConfigured").value(true))
            .andExpect(jsonPath("$.passwordRecoveryAvailable").value(true));
        mvc.perform(post("/api/v1/auth/register/start")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("handle",handle,
                "displayName","Mailjet Test", "email",email,
                "password","example-long-password"))))
            .andExpect(status().isOk());
        String verificationCode=codeSent(1,email);
        mvc.perform(post("/api/v1/auth/register/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("handle",handle,"code",verificationCode))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/api/v1/auth/recovery/start")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("account",email))))
            .andExpect(status().isOk());
        String recoveryCode=codeSent(2,email);
        mvc.perform(post("/api/v1/auth/recovery/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("handle",handle,"code",recoveryCode,
                "newPassword","another-long-password"))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("handle",handle,
                "password","another-long-password"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString());
    }

    private String codeSent(int count,String to) {
        ArgumentCaptor<String> text=ArgumentCaptor.forClass(String.class);
        verify(mailjet,times(count)).send(
            eq("local-test-api-key"),eq("local-test-secret"),eq("sender@example.test"),
            eq(to),anyString(),text.capture());
        Matcher found=Pattern.compile("code is: ([0-9]{6})")
            .matcher(text.getAllValues().get(count-1));
        assertTrue(found.find(),"An email code should be sent through Mailjet API");
        return found.group(1);
    }
}
