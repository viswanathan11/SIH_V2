package com.vidyasetu.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;

/**
 * Fallback when Docker/PostgreSQL is unavailable: runs a real PostgreSQL binary in-process
 * (zonky embedded-postgres). Data is persisted under app.data-dir/pg.
 */
@Configuration
@Profile("local-embedded")
public class EmbeddedPostgresConfig {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfig.class);

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres(@Value("${app.data-dir}") String dataDir,
                                             @Value("${app.embedded-pg.port:54329}") int port) throws IOException {
        File dir = new File(dataDir, "pg");
        dir.mkdirs();
        log.info("Starting embedded PostgreSQL on port {} (data: {})", port, dir.getAbsolutePath());
        return EmbeddedPostgres.builder()
                .setPort(port)
                .setDataDirectory(dir)
                .setCleanDataDirectory(false)
                .start();
    }

    @Bean
    public DataSource dataSource(EmbeddedPostgres pg) {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(pg.getJdbcUrl("postgres", "postgres"));
        ds.setUsername("postgres");
        ds.setPassword("postgres");
        ds.setMaximumPoolSize(20);
        return ds;
    }
}
