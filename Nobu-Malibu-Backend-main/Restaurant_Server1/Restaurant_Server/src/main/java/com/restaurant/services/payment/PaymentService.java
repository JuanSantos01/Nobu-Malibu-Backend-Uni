package com.restaurant.services.payment;

import com.restaurant.dto.PaymentDtos.*;
import com.restaurant.entity.*;
import com.restaurant.enums.PaymentConcept;
import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;
import com.restaurant.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lógica de cobro de Nobu Malibu.
 *
 * IMPORTANTE (seguridad): el total SIEMPRE se calcula aquí con los precios de la base de
 * datos; el cliente solo envía qué productos/cuántos. La pasarela de tarjeta es SIMULADA
 * (no mueve dinero real): para producción se reemplaza {@link #simulateGateway} por el SDK
 * de Wompi / Stripe / MercadoPago, que entrega un token en vez de datos de tarjeta.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Set<Integer> TIPS = Set.of(0, 5, 10, 15, 20);
    private static final Set<String> BRANDS = Set.of("visa", "mastercard", "amex", "diners", "discover", "other");

    private final PaymentRepo paymentRepo;
    private final SavedCardRepo cardRepo;
    private final BankAccountRepo bankRepo;
    private final ProductRepo productRepo;
    private final TableReservationRepo reservationRepo;

    /** Garantía de reserva por persona (se acredita a la cuenta final). Configurable por entorno. */
    @Value("${nobu.payments.deposit-per-guest-cents:500}")
    private long depositPerGuestCents;

    // ───────────────────────── Cotización ─────────────────────────

    private record Amounts(long subtotal, long deposit, long tip, String itemsText) {
        long total() {
            return subtotal + deposit + tip;
        }
    }

    private Amounts compute(PaymentRequest req, boolean applyDeposit) {
        long subtotal = 0;
        StringBuilder txt = new StringBuilder();
        if (req.getItems() != null) {
            for (OrderItem it : req.getItems()) {
                int qty = Math.max(1, Math.min(50, it.getQty() == null ? 1 : it.getQty()));
                Product p = null;
                if (it.getProductId() != null) p = productRepo.findById(it.getProductId()).orElse(null);
                if (p == null && it.getName() != null && !it.getName().isBlank())
                    p = productRepo.findFirstByNameIgnoreCase(it.getName().trim()).orElse(null);
                if (p == null) continue; // producto desconocido: se ignora, nunca se confía en un precio enviado
                subtotal += (p.getPrice() == null ? 0 : p.getPrice()) * 100L * qty;
                if (txt.length() > 0) txt.append("; ");
                txt.append(qty).append("x ").append(p.getName());
            }
        }
        int guests = Math.max(1, Math.min(20, req.getGuests() == null ? 1 : req.getGuests()));
        long deposit = applyDeposit ? guests * depositPerGuestCents : 0;
        int tipPct = req.getTipPercent() == null ? 0 : req.getTipPercent();
        if (!TIPS.contains(tipPct)) tipPct = 0;
        long tip = subtotal * tipPct / 100;
        return new Amounts(subtotal, deposit, tip, txt.toString());
    }

    public QuoteResponse quote(PaymentRequest req) {
        boolean prepaid = req.getMethod() != PaymentMethodType.CASH;
        Amounts a = compute(req, req.getReservationId() != null && prepaid);
        QuoteResponse q = new QuoteResponse();
        q.setSubtotalCents(a.subtotal());
        q.setDepositCents(a.deposit());
        q.setTipCents(a.tip());
        q.setTotalCents(a.total());
        return q;
    }

    // ───────────────────────── Pagar ─────────────────────────

    @Transactional
    public PaymentDto pay(User user, PaymentRequest req) {
        if (req.getMethod() == null) throw new PaymentException(HttpStatus.BAD_REQUEST, "Elige un método de pago");

        if (req.getReservationId() != null) {
            Reservation r = reservationRepo.findById(req.getReservationId())
                    .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "La reserva no existe"));
            if (!r.getUser().getId().equals(user.getId()))
                throw new PaymentException(HttpStatus.FORBIDDEN, "Esa reserva no es tuya");
            boolean alreadyPaid = paymentRepo.findByReservationIdOrderByCreatedAtDesc(r.getId()).stream()
                    .anyMatch(p -> p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.PENDING);
            if (alreadyPaid) throw new PaymentException(HttpStatus.CONFLICT, "Esta reserva ya tiene un pago registrado");
        }

        boolean prepaid = req.getMethod() != PaymentMethodType.CASH;
        Amounts a = compute(req, req.getReservationId() != null && prepaid);
        if (a.total() <= 0) throw new PaymentException(HttpStatus.BAD_REQUEST, "No hay nada que pagar");

        Payment p = new Payment();
        p.setUserId(user.getId());
        p.setCustomerName(user.getName());
        p.setReservationId(req.getReservationId());
        p.setMethod(req.getMethod());
        p.setSubtotalCents(a.subtotal());
        p.setDepositCents(a.deposit());
        p.setTipCents(a.tip());
        p.setTotalCents(a.total());
        p.setTipPercent(req.getTipPercent() != null && TIPS.contains(req.getTipPercent()) ? req.getTipPercent() : 0);
        p.setGuests(req.getGuests());
        p.setItems(a.itemsText().isEmpty() ? null : a.itemsText());
        p.setConcept(a.subtotal() > 0 && a.deposit() > 0 ? PaymentConcept.MIXED
                : a.subtotal() > 0 ? PaymentConcept.ORDER : PaymentConcept.RESERVATION);
        p.setCreatedAt(Instant.now());

        switch (req.getMethod()) {
            case CARD -> payByCard(user, req, p);
            case CASH -> p.setStatus(PaymentStatus.PENDING);
            case TRANSFER -> {
                p.setStatus(PaymentStatus.PENDING);
                if (req.getTransferReference() != null && !req.getTransferReference().isBlank())
                    p.setTransferReference(clean(req.getTransferReference(), 40));
            }
        }

        Payment saved = paymentRepo.save(p);
        saved.setReference("NM-" + String.format("%06d", saved.getId()));
        return toDto(paymentRepo.save(saved));
    }

    private void payByCard(User user, PaymentRequest req, Payment p) {
        String brand;
        String last4;
        String holder;
        Integer expMonth;
        Integer expYear;

        if (req.getSavedCardId() != null) {
            SavedCard c = cardRepo.findById(req.getSavedCardId())
                    .filter(x -> x.getUserId().equals(user.getId()))
                    .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Tarjeta no encontrada"));
            brand = c.getBrand();
            last4 = c.getLast4();
            holder = c.getHolder();
            expMonth = c.getExpMonth();
            expYear = c.getExpYear();
        } else if (req.getCard() != null) {
            CardInput in = req.getCard();
            brand = in.getBrand() == null ? "other" : in.getBrand().toLowerCase();
            last4 = in.getLast4();
            holder = in.getHolder();
            expMonth = in.getExpMonth();
            expYear = in.getExpYear();
            validateCard(brand, last4, expMonth, expYear);
            if (in.isSave()) saveCard(user, brand, last4, holder, expMonth, expYear);
        } else {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Falta la tarjeta");
        }

        p.setCardBrand(brand);
        p.setCardLast4(last4);
        p.setCardHolder(holder == null ? null : clean(holder, 60));

        String failure = simulateGateway(last4);
        if (failure == null) {
            p.setStatus(PaymentStatus.PAID);
            p.setPaidAt(Instant.now());
        } else {
            p.setStatus(PaymentStatus.FAILED);
            p.setFailureReason(failure);
        }
    }

    /** Pasarela simulada. Tarjetas de prueba: terminadas en 0002 = rechazada, 9995 = fondos insuficientes. */
    private String simulateGateway(String last4) {
        if ("0002".equals(last4)) return "Tarjeta rechazada por el banco emisor";
        if ("9995".equals(last4)) return "Fondos insuficientes";
        return null;
    }

    private void validateCard(String brand, String last4, Integer m, Integer y) {
        if (!BRANDS.contains(brand)) throw new PaymentException(HttpStatus.BAD_REQUEST, "Marca de tarjeta no válida");
        if (last4 == null || !last4.matches("\\d{4}"))
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Tarjeta no válida");
        if (m == null || y == null || m < 1 || m > 12)
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Fecha de vencimiento no válida");
        int year = y < 100 ? 2000 + y : y;
        int now = Year.now().getValue();
        java.time.YearMonth exp = java.time.YearMonth.of(year, m);
        if (year < now || year > now + 20 || exp.isBefore(java.time.YearMonth.now()))
            throw new PaymentException(HttpStatus.BAD_REQUEST, "La tarjeta está vencida");
    }

    // ───────────────────────── Consultas del cliente ─────────────────────────

    public List<PaymentDto> myPayments(Long userId) {
        return paymentRepo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(PaymentService::toDto).collect(Collectors.toList());
    }

    public PaymentDto attachTransferReference(User user, Long paymentId, String reference) {
        Payment p = paymentRepo.findById(paymentId)
                .filter(x -> user.getId().equals(x.getUserId()))
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        if (p.getMethod() != PaymentMethodType.TRANSFER || p.getStatus() != PaymentStatus.PENDING)
            throw new PaymentException(HttpStatus.CONFLICT, "Este pago ya no admite comprobante");
        if (reference == null || reference.isBlank()) throw new PaymentException(HttpStatus.BAD_REQUEST, "Escribe el número de comprobante");
        p.setTransferReference(clean(reference, 40));
        return toDto(paymentRepo.save(p));
    }

    // ───────────────────────── Tarjetas guardadas ─────────────────────────

    public List<SavedCardDto> cards(Long userId) {
        return cardRepo.findByUserIdOrderByIdDesc(userId).stream().map(PaymentService::toDto).collect(Collectors.toList());
    }

    public SavedCardDto addCard(User user, CardInput in) {
        String brand = in.getBrand() == null ? "other" : in.getBrand().toLowerCase();
        validateCard(brand, in.getLast4(), in.getExpMonth(), in.getExpYear());
        return toDto(saveCard(user, brand, in.getLast4(), in.getHolder(), in.getExpMonth(), in.getExpYear()));
    }

    private SavedCard saveCard(User user, String brand, String last4, String holder, Integer m, Integer y) {
        List<SavedCard> existing = cardRepo.findByUserIdOrderByIdDesc(user.getId());
        for (SavedCard c : existing) {
            if (c.getBrand().equals(brand) && c.getLast4().equals(last4) && c.getExpMonth().equals(m) && c.getExpYear().equals(y)) return c;
        }
        SavedCard c = new SavedCard();
        c.setUserId(user.getId());
        c.setBrand(brand);
        c.setLast4(last4);
        c.setHolder(holder == null ? "" : clean(holder, 60));
        c.setExpMonth(m);
        c.setExpYear(y);
        c.setDefaultCard(existing.isEmpty());
        return cardRepo.save(c);
    }

    public void deleteCard(User user, Long id) {
        SavedCard c = cardRepo.findById(id).filter(x -> x.getUserId().equals(user.getId()))
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Tarjeta no encontrada"));
        boolean wasDefault = c.isDefaultCard();
        cardRepo.delete(c);
        if (wasDefault) {
            cardRepo.findByUserIdOrderByIdDesc(user.getId()).stream().findFirst().ifPresent(n -> {
                n.setDefaultCard(true);
                cardRepo.save(n);
            });
        }
    }

    public void makeDefault(User user, Long id) {
        List<SavedCard> all = cardRepo.findByUserIdOrderByIdDesc(user.getId());
        if (all.stream().noneMatch(c -> c.getId().equals(id))) throw new PaymentException(HttpStatus.NOT_FOUND, "Tarjeta no encontrada");
        for (SavedCard c : all) {
            c.setDefaultCard(c.getId().equals(id));
            cardRepo.save(c);
        }
    }

    // ───────────────────────── Cuenta bancaria del restaurante ─────────────────────────

    public BankAccountDto bankAccount() {
        BankAccount b = bankRepo.findById(1L).orElseGet(() -> {
            BankAccount d = new BankAccount();
            d.setBankName("Bancolombia");
            d.setAccountType("Ahorros");
            d.setAccountNumber("000-000000-00");
            d.setHolderName("Nobu Malibu S.A.S.");
            d.setDocumentId("NIT 000.000.000-0");
            d.setAlias("@nobumalibu");
            d.setConfigured(false);
            return bankRepo.save(d);
        });
        return toDto(b);
    }

    public BankAccountDto updateBankAccount(BankAccountDto in) {
        if (in.getBankName() == null || in.getBankName().isBlank() || in.getAccountNumber() == null || in.getAccountNumber().isBlank()
                || in.getHolderName() == null || in.getHolderName().isBlank())
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Banco, número de cuenta y titular son obligatorios");
        BankAccount b = bankRepo.findById(1L).orElseGet(BankAccount::new);
        b.setId(1L);
        b.setBankName(clean(in.getBankName(), 60));
        b.setAccountType(in.getAccountType() == null ? "Ahorros" : clean(in.getAccountType(), 30));
        b.setAccountNumber(clean(in.getAccountNumber(), 40));
        b.setHolderName(clean(in.getHolderName(), 80));
        b.setDocumentId(in.getDocumentId() == null ? "" : clean(in.getDocumentId(), 40));
        b.setAlias(in.getAlias() == null ? "" : clean(in.getAlias(), 40));
        b.setConfigured(true);
        return toDto(bankRepo.save(b));
    }

    // ───────────────────────── Mapeos ─────────────────────────

    private static String clean(String s, int max) {
        String t = s.replaceAll("[\\r\\n\\t<>]", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static PaymentDto toDto(Payment p) {
        PaymentDto d = new PaymentDto();
        d.setId(p.getId());
        d.setUserId(p.getUserId());
        d.setCustomerName(p.getCustomerName());
        d.setReservationId(p.getReservationId());
        d.setMethod(p.getMethod());
        d.setStatus(p.getStatus());
        d.setConcept(p.getConcept());
        d.setSubtotalCents(p.getSubtotalCents());
        d.setDepositCents(p.getDepositCents());
        d.setTipCents(p.getTipCents());
        d.setTotalCents(p.getTotalCents());
        d.setTipPercent(p.getTipPercent());
        d.setGuests(p.getGuests());
        d.setCardBrand(p.getCardBrand());
        d.setCardLast4(p.getCardLast4());
        d.setReference(p.getReference());
        d.setTransferReference(p.getTransferReference());
        d.setItems(p.getItems());
        d.setFailureReason(p.getFailureReason());
        d.setDemo(p.isDemo());
        d.setCreatedAt(p.getCreatedAt());
        d.setPaidAt(p.getPaidAt());
        return d;
    }

    public static SavedCardDto toDto(SavedCard c) {
        SavedCardDto d = new SavedCardDto();
        d.setId(c.getId());
        d.setBrand(c.getBrand());
        d.setLast4(c.getLast4());
        d.setHolder(c.getHolder());
        d.setExpMonth(c.getExpMonth());
        d.setExpYear(c.getExpYear());
        d.setDefaultCard(c.isDefaultCard());
        return d;
    }

    public static BankAccountDto toDto(BankAccount b) {
        BankAccountDto d = new BankAccountDto();
        d.setBankName(b.getBankName());
        d.setAccountType(b.getAccountType());
        d.setAccountNumber(b.getAccountNumber());
        d.setHolderName(b.getHolderName());
        d.setDocumentId(b.getDocumentId());
        d.setAlias(b.getAlias());
        d.setConfigured(b.isConfigured());
        return d;
    }
}
