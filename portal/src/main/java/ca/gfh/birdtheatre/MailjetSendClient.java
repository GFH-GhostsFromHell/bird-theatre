package ca.gfh.birdtheatre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Mailjet Send API v3.1 over HTTPS port 443, compatible with Render Free. */
@Component
class MailjetSendClient {
    static final URI MAILJET_ENDPOINT = URI.create("https://api.mailjet.com/v3.1/send");
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI endpoint;

    @Autowired MailjetSendClient(ObjectMapper json) {
        this(json, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build(), MAILJET_ENDPOINT);
    }

    // Unit-test seam; deployed code always uses the pinned HTTPS endpoint.
    MailjetSendClient(ObjectMapper json, HttpClient http, URI endpoint) {
        this.json = json;
        this.http = http;
        this.endpoint = endpoint;
    }

    void send(String apiKey, String secret, String from, String to,
              String subject, String text) {
        try {
            String payload=json.writeValueAsString(Map.of(
                "Messages", List.of(Map.of(
                    "From", Map.of("Email",from,"Name","Bird Theatre"),
                    "To", List.of(Map.of("Email",to)),
                    "Subject",subject,
                    "TextPart",text
                ))));
            String authorization=Base64.getEncoder().encodeToString(
                (apiKey+":"+secret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request=HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization","Basic "+authorization)
                .header("Content-Type","application/json")
                .header("Accept","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response=http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200 || response.statusCode()>=300) throw failed();
            JsonNode messages=json.readTree(response.body()).path("Messages");
            if(!messages.isArray() || messages.size()!=1 ||
                !"success".equalsIgnoreCase(messages.get(0).path("Status").asText()))
                throw failed();
        } catch (ResponseStatusException expected) {
            throw expected;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failed();
        } catch (Exception failure) {
            // Never include private keys, API response bodies, or recipient details in errors.
            throw failed();
        }
    }

    private ResponseStatusException failed() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Mailjet could not accept the email. Please try again later.");
    }
}
