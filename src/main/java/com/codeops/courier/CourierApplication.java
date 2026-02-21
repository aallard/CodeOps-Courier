package com.codeops.courier;

import com.codeops.courier.config.JwtProperties;
import com.codeops.courier.config.ServiceUrlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CodeOps-Courier application entry point. Full-featured API testing and development platform.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, ServiceUrlProperties.class})
public class CourierApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourierApplication.class, args);
    }
}
