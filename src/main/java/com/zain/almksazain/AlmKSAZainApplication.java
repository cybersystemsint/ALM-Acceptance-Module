package com.zain.almksazain;

import java.util.TimeZone;

import javax.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@ComponentScan(basePackages = "com.zain.almksazain")
@EnableAsync
@EnableScheduling
public class AlmKSAZainApplication extends SpringBootServletInitializer {

    @PostConstruct
    public void init() {
          TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"));
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(AlmKSAZainApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(AlmKSAZainApplication.class, args);
    }
    /**
     *  Define RestTemplate Bean
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
