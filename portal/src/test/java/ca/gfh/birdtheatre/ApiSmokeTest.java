package ca.gfh.birdtheatre;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="bird.auth.legacy-signup-enabled=true") @AutoConfigureMockMvc
class ApiSmokeTest {
    @Autowired MockMvc mvc;
    @Test void camerasArePublicAndMyCamerasRequireLogin() throws Exception {
        mvc.perform(get("/api/v1/cameras")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/cameras")).andExpect(status().isUnauthorized());
    }
    @Test void rejectsInsecureCameraStream() throws Exception {
        String signup="""
            {"handle":"testbirder","displayName":"Test Birder","password":"a-good-example-password"}
            """;
        String response=mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
        mvc.perform(post("/api/v1/cameras").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Test feeder","kind":"FEEDER","region":"Ontario","description":"Test","streamUrl":"http://localhost/live.m3u8","publicListing":true}
                """))
            .andExpect(status().isBadRequest());
    }
}
