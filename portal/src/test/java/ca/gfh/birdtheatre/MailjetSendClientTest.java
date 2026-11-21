package ca.gfh.birdtheatre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a local HTTP stand-in; never sends real email or reads private API keys. */
class MailjetSendClientTest {
    @Test void constructsMailjet31PayloadAndRejectsProviderErrors() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>();
        AtomicReference<String> body=new AtomicReference<>();
        AtomicBoolean reject=new AtomicBoolean(false);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v3.1/send", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            boolean bad=reject.get();
            byte[] reply=(bad ? "{\"ErrorMessage\":\"provider rejected secret-key\"}"
                : "{\"Messages\":[{\"Status\":\"success\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(bad?401:200,reply.length);
            try(var stream=exchange.getResponseBody()) { stream.write(reply); }
        });
        server.start();
        try {
            MailjetSendClient client=new MailjetSendClient(new ObjectMapper(),
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v3.1/send"));
            client.send("test-public-key","test-private-key",
                "sender@example.test","recipient@example.test",
                "Bird Theatre code","Your code is 123456");
            assertEquals("Basic "+java.util.Base64.getEncoder().encodeToString(
                "test-public-key:test-private-key".getBytes(StandardCharsets.UTF_8)),auth.get());
            JsonNode data=new ObjectMapper().readTree(body.get());
            JsonNode message=data.get("Messages").get(0);
            assertEquals("sender@example.test",message.get("From").get("Email").asText());
            assertEquals("recipient@example.test",message.get("To").get(0).get("Email").asText());
            assertEquals("Bird Theatre code",message.get("Subject").asText());
            assertTrue(message.get("TextPart").asText().contains("123456"));
            assertFalse(body.get().contains("test-private-key"));
            reject.set(true);
            ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->client.send("test-public-key","test-private-key",
                    "sender@example.test","recipient@example.test","Subject","Body"));
            assertEquals(502,error.getStatusCode().value());
            assertFalse(error.getMessage().contains("test-private-key"));
            assertFalse(error.getMessage().contains("provider rejected"));
        } finally { server.stop(0); }
    }
}
