package com.restaurant.controller;

import com.restaurant.dto.PaymentDtos.*;
import com.restaurant.services.finance.FinanceService;
import com.restaurant.services.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** NOBU FINANCE (solo ADMIN: lo exige WebSecurityConfiguration para /api/admin/**). */
@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final PaymentService paymentService;

    @GetMapping("/summary")
    public FinanceSummary summary(@RequestParam(defaultValue = "7d") String period) {
        return financeService.summary(period);
    }

    @PostMapping("/payments/{id}/status")
    public PaymentDto setStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        return financeService.setStatus(id, body.getStatus());
    }

    @GetMapping("/bank-account")
    public BankAccountDto bankAccount() {
        return paymentService.bankAccount();
    }

    @PutMapping("/bank-account")
    public BankAccountDto updateBankAccount(@RequestBody BankAccountDto body) {
        return paymentService.updateBankAccount(body);
    }

    @PostMapping("/demo/seed")
    public Map<String, Object> seed() {
        return Map.of("created", financeService.seedDemo());
    }

    @PostMapping("/demo/tick")
    public PaymentDto tick() {
        return financeService.demoTick();
    }

    @DeleteMapping("/demo")
    public Map<String, Object> clearDemo() {
        return Map.of("deleted", financeService.clearDemo());
    }
}
