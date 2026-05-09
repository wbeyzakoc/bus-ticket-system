package com.busgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BusgoApplication {
  public static void main(String[] args) {
    SpringApplication.run(BusgoApplication.class, args);
  }
}
