package ca.gfh.birdtheatre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="bird.auth.legacy-signup-enabled=true") @AutoConfigureMockMvc
class DetectionIngestionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void sameSpeciesTwoTracksCountAsTwoVisitsAndNetworkRetryIsIdempotent() throws Exception {
        String user = "bird" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String signup = json.writeValueAsString(java.util.Map.of(
            "handle", user, "displayName", "Integration Tester", "password", "test-password-1234"));
        JsonNode session = json.readTree(mvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON).content(signup))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String auth = "Bearer " + session.get("token").asText();
        String station = """
            {"name":"CI Test Station","kind":"FEEDER","region":"Ontario","description":"Local fixture", "streamUrl":"https://example.org/test.m3u8", "publicListing":false}
            """;
        JsonNode camera = json.readTree(mvc.perform(post("/api/v1/cameras")
            .header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(station))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String cameraId = camera.get("camera").get("id").asText();
        String deviceToken = camera.get("deviceToken").asText();
        String endpoint = "/api/v1/cameras/"+cameraId+"/detections";
        String first = String.format("""
            {"eventId":"%s", "trackId":"%s", "species":"Blue Jay", "eventType":"ARRIVAL", "confidence":0.8}
            """, UUID.randomUUID(), UUID.randomUUID());
        String second = String.format("""
            {"eventId":"%s", "trackId":"%s", "species":"Blue Jay", "eventType":"ARRIVAL", "confidence":0.75}
            """, UUID.randomUUID(), UUID.randomUUID());
        mvc.perform(post(endpoint).header("X-Device-Token", deviceToken)
            .contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isOk());
        JsonNode retry = json.readTree(mvc.perform(post(endpoint).header("X-Device-Token", deviceToken)
            .contentType(MediaType.APPLICATION_JSON).content(first))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(retry.get("deduplicated").asBoolean());
        mvc.perform(post(endpoint).header("X-Device-Token", deviceToken)
            .contentType(MediaType.APPLICATION_JSON).content(second)).andExpect(status().isOk());
        JsonNode stats = json.readTree(mvc.perform(get("/api/v1/cameras/"+cameraId+"/statistics")
            .header("Authorization",auth)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertEquals(2,stats.get("totalEstimatedVisits").asInt());
        assertEquals(2,stats.get("bySpecies").get("Blue Jay").asInt());
        mvc.perform(post(endpoint).header("X-Device-Token", "invalid")
            .contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isUnauthorized());
    }
}
