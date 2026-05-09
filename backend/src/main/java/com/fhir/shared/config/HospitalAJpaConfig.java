package com.fhir.shared.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.fhir.hospitalA",
    entityManagerFactoryRef = "hospitalAEntityManagerFactory",
    transactionManagerRef = "hospitalATransactionManager"
)
public class HospitalAJpaConfig {

    @org.springframework.beans.factory.annotation.Value("${spring.jpa.hibernate.ddl-auto:update}")
    private String ddlAuto;

    @org.springframework.beans.factory.annotation.Value("${spring.jpa.properties.hibernate.dialect:org.hibernate.dialect.PostgreSQLDialect}")
    private String dialect;

    @Bean(name = "hospitalAEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean hospitalAEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("hospitalADataSource") DataSource dataSource) {
        return builder
            .dataSource(dataSource)
            .packages("com.fhir.hospitalA.model")
            .persistenceUnit("hospitalA")
            .properties(Map.of(
                "hibernate.hbm2ddl.auto", ddlAuto,
                "hibernate.dialect", dialect
            ))
            .build();
    }

    @Bean(name = "hospitalATransactionManager")
    public PlatformTransactionManager hospitalATransactionManager(
            @Qualifier("hospitalAEntityManagerFactory")
            LocalContainerEntityManagerFactoryBean factory) {
        return new JpaTransactionManager(factory.getObject());
    }
}
