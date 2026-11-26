package com.restaurant.enums;

public enum PaymentStatus {
    /** Registrado pero aún no cobrado (efectivo al llegar / transferencia por verificar). */
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}
