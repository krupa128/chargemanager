package com.zynetic.ev.chargemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChargemanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChargemanagerApplication.class, args);
		System.out.println(Thread.activeCount());
	}

}
