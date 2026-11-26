package com.restaurant.controller;

import com.restaurant.dto.PaymentDtos.*;
import com.restaurant.entity.User;
import com.restaurant.services.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Pagos y tarjetas del cliente. El usuario sale SIEMPRE del JWT, nunca de un id enviado por el móvil. */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments/quote")
    public QuoteResponse quote(@RequestBody PaymentRequest req) {
        return paymentService.quote(req);
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentDto> pay(@AuthenticationPrincipal User user, @RequestBody PaymentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.pay(user, req));
    }

    @GetMapping("/payments")
    public List<PaymentDto> myPayments(@AuthenticationPrincipal User user) {
        return paymentService.myPayments(user.getId());
    }

    @PostMapping("/payments/{id}/transfer-reference")
    public PaymentDto transferReference(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestBody ReferenceRequest body) {
        return paymentService.attachTransferReference(user, id, body.getReference());
    }

    @GetMapping("/cards")
    public List<SavedCardDto> cards(@AuthenticationPrincipal User user) {
        return paymentService.cards(user.getId());
    }

    @PostMapping("/cards")
    public ResponseEntity<SavedCardDto> addCard(@AuthenticationPrincipal User user, @RequestBody CardInput body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.addCard(user, body));
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deleteCard(@AuthenticationPrincipal User user, @PathVariable Long id) {
        paymentService.deleteCard(user, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cards/{id}/default")
    public ResponseEntity<Void> defaultCard(@AuthenticationPrincipal User user, @PathVariable Long id) {
        paymentService.makeDefault(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bank-account")
    public BankAccountDto bankAccount() {
        return paymentService.bankAccount();
    }
}
