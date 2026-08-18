package com.zain.almksazain;

import java.util.TimeZone;

import javax.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

// WAR deployment bootstraps through ServletInitializer only - this class must NOT also extend
// SpringBootServletInitializer. Tomcat's ServletContainerInitializer SPI calls onStartup() on every
// WebApplicationInitializer found on the classpath, so having both meant two full application
// contexts were created per deployment, each with its own copy of every bean (including
// AgingEmailSchedulerService, which duplicated the aging-email cron jobs).
@SpringBootApplication
@ComponentScan(basePackages = "com.zain.almksazain")
@EnableAsync
@EnableScheduling
public class AlmKSAZainApplication {

    @PostConstruct
    public void init() {
          TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"));
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
