package com.misl.leavetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * {@code @SpringBootApplication} is three annotations in one:
 *   - @Configuration        this class can define beans
 *   - @EnableAutoConfiguration  Spring inspects the classpath and configures what it finds
 *                               (Postgres driver present -> configure a DataSource, etc.)
 *   - @ComponentScan        scan this package and everything below it for @Component,
 *                           @Service, @Repository, @RestController and register them
 *                           in the application context.
 *
 * That last point is why every class in this project lives under
 * com.misl.leavetracker - if a class sits outside this package it is invisible to Spring.
 */
@SpringBootApplication
public class LeaveTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveTrackerApplication.class, args);
    }
}
