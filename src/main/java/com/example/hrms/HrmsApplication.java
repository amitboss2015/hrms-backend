package com.example.hrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HrmsApplication {
  public static void main(String[] args) {
    SpringApplication.run(HrmsApplication.class, args);
  }
}
