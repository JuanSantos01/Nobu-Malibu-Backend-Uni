package com.restaurant.controller;

import com.restaurant.services.customer.TableConflictException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cuerpo JSON de un conflicto de reserva: {message, code, freeAt?}. `freeAt` va en ISO-8601 (UTC). */
final class ConflictBody {
    private ConflictBody() {}

    static Map<String, Object> of(TableConflictException e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", e.getMessage());
        m.put("code", e.getCode());
        if (e.getFreeAt() != null) m.put("freeAt", e.getFreeAt().toInstant().toString());
        return m;
    }
}
