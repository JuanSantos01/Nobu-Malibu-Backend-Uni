package com.restaurant.services.customer;

import java.util.Date;

/** No se puede reservar (mesas llenas, restaurante cerrado o solicitud inválida). */
public class TableConflictException extends RuntimeException {

    /** Si hay mesas llenas: primer instante en que volvería a haber una libre para la duración pedida (puede ser null). */
    private final Date freeAt;

    /** Motivo para que la app distinga: FULL, CLOSED, INVALID. */
    private final String code;

    public TableConflictException(String message) {
        this(message, "INVALID", null);
    }

    public TableConflictException(String message, String code, Date freeAt) {
        super(message);
        this.code = code;
        this.freeAt = freeAt;
    }

    public Date getFreeAt() {
        return freeAt;
    }

    public String getCode() {
        return code;
    }
}
