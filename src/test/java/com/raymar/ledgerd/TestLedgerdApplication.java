package com.raymar.ledgerd;

import org.springframework.boot.SpringApplication;

public class TestLedgerdApplication {

	public static void main(String[] args) {
		SpringApplication.from(LedgerdApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
