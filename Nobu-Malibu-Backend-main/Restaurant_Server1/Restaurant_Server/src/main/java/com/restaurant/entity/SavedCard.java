package com.restaurant.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Tarjeta guardada del cliente: SOLO datos no sensibles (nunca el número completo ni el CVV). */
@Entity
@Data
@Table(name = "saved_card")
public class SavedCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String brand;

    private String last4;

    private String holder;

    private Integer expMonth;

    private Integer expYear;

    private boolean defaultCard;
}
