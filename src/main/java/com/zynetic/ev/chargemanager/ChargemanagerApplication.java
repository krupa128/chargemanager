package com.zynetic.ev.chargemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // required for OcppService's stale-charger heartbeat check
public class ChargemanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChargemanagerApplication.class, args);
	}

}