package com.restaurant.entity;

import com.restaurant.enums.PaymentConcept;
import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Un cobro del restaurante. Los importes se guardan en CENTAVOS (long) para no
 * arrastrar errores de redondeo de double.
 * No guarda números de tarjeta: solo marca, últimos 4 dígitos y titular.
 */
@Entity
@Data
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String customerName;

    /** Reserva a la que pertenece (puede ser null en un pedido suelto). */
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    private PaymentMethodType method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentConcept concept;

    private long subtotalCents;
    private long depositCents;
    private long tipCents;
    private long totalCents;

    private Integer tipPercent;
    private Integer guests;

    private String cardBrand;
    private String cardLast4;
    private String cardHolder;

    /** Código corto que el cliente usa como concepto de la transferencia (NM-000123). */
    private String reference;

    /** Número de comprobante que el cliente informa tras transferir. */
    private String transferReference;

    @Column(length = 1000)
    private String items;

    private String failureReason;

    /** Marcado para poder borrar los datos de demostración sin tocar los reales. */
    private boolean demo;

    private Instant createdAt;

    private Instant paidAt;
}
