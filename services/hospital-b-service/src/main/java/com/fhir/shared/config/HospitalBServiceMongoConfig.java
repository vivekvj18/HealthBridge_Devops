package com.fhir.shared.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.fhir.hospitalB.repository")
public class HospitalBServiceMongoConfig extends AbstractMongoClientConfiguration {

    @Value("${app.mongo.hospital-b.uri:mongodb://localhost:27017}")
    private String mongoUri;

    @Value("${app.mongo.hospital-b.database:hospital_b}")
    private String databaseName;

    @Value("${app.mongo.hospital-b.auto-index-creation:true}")
    private boolean autoIndexCreation;

    @Override
    protected String getDatabaseName() {
        return databaseName;
    }

    @Override
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Override
    protected boolean autoIndexCreation() {
        return autoIndexCreation;
    }
}
