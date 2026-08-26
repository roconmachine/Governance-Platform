package com.roconmachine.governance.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;

@Configurable
public class Config {

    @Bean
    public ObjectMapper getObjectMapper(){ return new ObjectMapper();}
}
