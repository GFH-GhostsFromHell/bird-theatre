package ca.gfh.birdtheatre;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration @EnableWebSecurity
class WebSecurity {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable()) // stateless, explicit bearer token; no cookie authentication
            .authorizeHttpRequests(req -> req.anyRequest().permitAll()) // API enforces ownership in every write handler
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; base-uri 'none'; object-src 'none'; form-action 'self'; " +
                "script-src 'self' https://cdn.jsdelivr.net; style-src 'self'; " +
                "img-src 'self' data: https:; media-src 'self' https: blob:; " +
                "connect-src 'self' https:; worker-src 'self' blob:; frame-ancestors 'none'")))
            .build();
    }
}
