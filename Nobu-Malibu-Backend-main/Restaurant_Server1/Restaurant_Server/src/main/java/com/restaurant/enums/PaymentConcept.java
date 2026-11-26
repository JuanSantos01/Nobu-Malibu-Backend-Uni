package com.restaurant.enums;

public enum PaymentConcept {
    /** Solo la garantía de la reserva. */
    RESERVATION,
    /** Solo productos del menú (pre-orden). */
    ORDER,
    /** Garantía de reserva + productos. */
    MIXED
}
