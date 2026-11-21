package ca.gfh.birdtheatre;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
class DatabaseConfig {
    /** Render supplies postgres:// credentials; Spring requires a JDBC URL. */
    @Bean @Primary
    @ConditionalOnProperty(name = "database.url")
    DataSource renderPostgres(org.springframework.core.env.Environment env) {
        URI uri = URI.create(env.getRequiredProperty("database.url"));
        if (!java.util.Set.of("postgres", "postgresql").contains(uri.getScheme())
            || uri.getHost() == null || uri.getRawUserInfo() == null || !uri.getRawUserInfo().contains(":")) {
            throw new IllegalArgumentException("Expected PostgreSQL DATABASE_URL with user/password");
        }
        String[] parts = uri.getRawUserInfo().split(":", 2);
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + (uri.getPort() < 0 ? 5432 : uri.getPort()) + uri.getRawPath());
        ds.setUsername(URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
        ds.setPassword(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(0);
        return ds;
    }
}
