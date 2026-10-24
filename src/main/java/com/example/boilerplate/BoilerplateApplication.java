package com.example.boilerplate;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BoilerplateApplication {
  public static void main(String[] args) {
    SpringApplication.run(BoilerplateApplication.class, args);
  }

  /** The time the dashboard, the reports and the export read ("today", "overdue"); ITs pin it. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
