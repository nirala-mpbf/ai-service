package com.example.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataSourceConfigurationTest {

    @Test
    void bindsTwoDatabasesOnTheSamePostgresServer() {
        String promptUrl = "jdbc:postgresql://127.0.0.1:5544/test_prompts";
        String memoryUrl = "jdbc:postgresql://127.0.0.1:5544/test_memory";

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(DataSourceConfiguration.class)
                .withPropertyValues(
                        "APP_DB_HOST=127.0.0.1",
                        "APP_DB_PORT=5544",
                        "PROMPT_DB_NAME=test_prompts",
                        "MEMORY_DB_NAME=test_memory")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    HikariDataSource prompts = context.getBean("promptMetadataDataSource", HikariDataSource.class);
                    HikariDataSource memory = context.getBean("agentMemoryDataSource", HikariDataSource.class);
                    assertThat(prompts.getJdbcUrl()).isEqualTo(promptUrl);
                    assertThat(memory.getJdbcUrl()).isEqualTo(memoryUrl);
                    assertThat(context.getBean(DataSource.class)).isSameAs(prompts);
                    assertThat(context.getEnvironment().getProperty("react-agent.prompts.datasource-bean"))
                            .isEqualTo("promptMetadataDataSource");
                    assertThat(context.getEnvironment().getProperty("react-agent.memory.datasource-bean"))
                            .isEqualTo("agentMemoryDataSource");
                });
    }
}
