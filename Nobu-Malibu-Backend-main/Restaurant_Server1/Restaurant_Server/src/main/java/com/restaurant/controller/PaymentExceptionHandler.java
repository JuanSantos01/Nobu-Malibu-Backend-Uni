package com.restaurant.controller;

import com.restaurant.services.payment.PaymentException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Devuelve {"message": "..."} (la app móvil lo muestra tal cual). */
@RestControllerAdvice(assignableTypes = {PaymentController.class, FinanceController.class})
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, String>> handle(PaymentException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
