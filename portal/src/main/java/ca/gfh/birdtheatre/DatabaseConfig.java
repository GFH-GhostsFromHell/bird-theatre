package ca.gfh.birdtheatre;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
class DatabaseConfig {
    /*
     * Render supplies an internal postgres:// URL. For the prototype, Bird Theatre
     * reuses the existing PostgreSQL INSTANCE but stores all of its own tables in
     * an isolated "bird_theatre" schema, never the Electrical Workbench public schema.
     *
     * This is logical separation only: the apps still share database credentials,
     * capacity, backups, and instance availability. Use a dedicated role/database
     * when moving beyond a prototype.
     */
    @Bean @Primary
    @ConditionalOnProperty(name = "database.url")
    DataSource renderPostgres(org.springframework.core.env.Environment env) {
        URI uri = URI.create(env.getRequiredProperty("database.url"));
        if (!java.util.Set.of("postgres", "postgresql").contains(uri.getScheme())
            || uri.getHost() == null || uri.getRawUserInfo() == null
            || !uri.getRawUserInfo().contains(":") || uri.getRawPath() == null
            || uri.getRawPath().length() < 2) {
            throw new IllegalArgumentException("Expected PostgreSQL DATABASE_URL with user/password");
        }

        String schema = env.getProperty("bird.db.schema", "bird_theatre");
        if (!"bird_theatre".equals(schema)) {
            throw new IllegalArgumentException("This prototype must use the bird_theatre schema");
        }

        String[] parts = uri.getRawUserInfo().split(":", 2);
        HikariDataSource ds = new HikariDataSource();
        // The driver sets search_path on every connection; Hibernate DDL never targets public.
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":"
            + (uri.getPort() < 0 ? 5432 : uri.getPort()) + uri.getRawPath()
            + "?currentSchema=bird_theatre";
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
        ds.setPassword(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        ds.setPoolName("BirdTheatrePrototypePostgres");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(10000);

        // Provision only our schema. Validate the effective search_path before ORM starts.
        // If creation is not permitted, startup fails closed instead of using public.
        try (Connection conn = ds.getConnection();
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS bird_theatre");
            try (ResultSet rs = statement.executeQuery("SELECT current_schema()")) {
                if (!rs.next() || !"bird_theatre".equals(rs.getString(1))) {
                    throw new IllegalStateException(
                        "Bird Theatre refused to start outside its own PostgreSQL schema");
                }
            }
        } catch (Exception problem) {
            ds.close();
            throw new IllegalStateException(
                "Could not initialize isolated PostgreSQL schema for Bird Theatre", problem);
        }
        return ds;
    }
}
