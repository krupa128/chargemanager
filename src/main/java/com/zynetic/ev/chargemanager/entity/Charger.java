package com.zynetic.ev.chargemanager.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "charger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Charger {

    @Id
    @Column(length = 36) // UUID format
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerStatus status;

    @Column(nullable = false)
    private LocalDateTime lastHeartbeat;

    @Column(nullable = false)
    private String vendor;

    @NotBlank
    @Column(nullable = false)
    private String model;

    private String firmwareVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
