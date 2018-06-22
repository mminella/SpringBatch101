package io.spring;

import java.util.UUID;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
public class Application {

	public static void main(String[] args) {
		args = new String[3];
		args[0] = "inputFile=/Users/mminella/Documents/SpringSourceWorkspace/StarWars/src/main/resources/data/swk_small.log";
		args[1] = "stagingDirectory=/Users/mminella/Documents/SpringSourceWorkspace/StarWars/target/work/";
		args[2] = "id=" + UUID.randomUUID().toString();
		SpringApplication.run(Application.class, args);
	}
}
