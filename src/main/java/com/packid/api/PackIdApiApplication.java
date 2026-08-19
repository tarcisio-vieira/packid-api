package com.packid.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import java.util.TimeZone;

@SpringBootApplication
public class PackIdApiApplication extends SpringBootServletInitializer {
    private static final String BRAZIL_TIME_ZONE = "America/Sao_Paulo";

    static {
        TimeZone.setDefault(TimeZone.getTimeZone(BRAZIL_TIME_ZONE));
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(BRAZIL_TIME_ZONE));
        SpringApplication.run(PackIdApiApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        TimeZone.setDefault(TimeZone.getTimeZone(BRAZIL_TIME_ZONE));
        return builder.sources(PackIdApiApplication.class);
    }
}
