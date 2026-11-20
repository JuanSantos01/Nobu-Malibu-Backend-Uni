package com.restaurant.services.customer;

/** No hay mesas libres de ese tipo en el horario pedido (o la solicitud es inválida). */
public class TableConflictException extends RuntimeException {
    public TableConflictException(String message) {
        super(message);
    }
}
