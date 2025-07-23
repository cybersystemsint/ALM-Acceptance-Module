package com.zain.almksazain;

import javax.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.zain.almksazain")
@EnableScheduling
public class AlmKSAZainApplication extends SpringBootServletInitializer {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Nairobi"));
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(AlmKSAZainApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(AlmKSAZainApplication.class, args);
    }
//  public static void main(String[] args) {
//     try {
//         SpringApplication.run(AlmKSAZainApplication.class, args);
//     } catch (Throwable t) {
//         t.printStackTrace(); // Print to console
//         throw t;
//     }
// }
}
