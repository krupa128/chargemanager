package com.zynetic.ev.chargemanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "charging_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChargingTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int transactionId;

    @ManyToOne
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)  // 🔹 Linking transactions to users
    private User user;

    @Column(nullable = false)
    private String idTag;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Column(nullable = false)
    private int meterStart;

    private Integer meterEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String stopReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
