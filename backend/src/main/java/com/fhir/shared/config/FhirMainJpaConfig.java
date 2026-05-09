package com.fhir.shared.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableJpaRepositories(basePackages = {
        "com.fhir.auth.repository",
        "com.fhir.consent.repository",
        "com.fhir.identity.repository",
        "com.fhir.shared.audit",
        "com.fhir.shared.hospital",
        "com.fhir.admin",
        "com.fhir.notification"
}, entityManagerFactoryRef = "fhirMainEntityManagerFactory", transactionManagerRef = "fhirMainTransactionManager")
public class FhirMainJpaConfig {

    @org.springframework.beans.factory.annotation.Value("${spring.jpa.hibernate.ddl-auto:update}")
    private String ddlAuto;

    @org.springframework.beans.factory.annotation.Value("${spring.jpa.properties.hibernate.dialect:org.hibernate.dialect.PostgreSQLDialect}")
    private String dialect;

    @Primary
    @Bean(name = "fhirMainEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean fhirMainEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("fhirMainDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages(
                        "com.fhir.auth.model",
                        "com.fhir.consent.model",
                        "com.fhir.identity.model",
                        "com.fhir.shared.audit",
                        "com.fhir.shared.hospital",
                        "com.fhir.notification")
                .persistenceUnit("fhirMain")
                .properties(Map.of(
                        "hibernate.hbm2ddl.auto", ddlAuto,
                        "hibernate.dialect", dialect))
                .build();
    }

    @Primary
    @Bean(name = "fhirMainTransactionManager")
    public PlatformTransactionManager fhirMainTransactionManager(
            @Qualifier("fhirMainEntityManagerFactory") LocalContainerEntityManagerFactoryBean factory) {
        return new JpaTransactionManager(factory.getObject());
    }
}
