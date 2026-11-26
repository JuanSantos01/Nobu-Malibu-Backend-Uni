package com.restaurant.services.finance;

import com.restaurant.dto.PaymentDtos.*;
import com.restaurant.entity.Payment;
import com.restaurant.enums.PaymentConcept;
import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;
import com.restaurant.repo.PaymentRepo;
import com.restaurant.services.payment.PaymentException;
import com.restaurant.services.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** NOBU FINANCE: cálculos del módulo administrativo (ingresos, métodos, series, por cobrar). */
@Service
@RequiredArgsConstructor
public class FinanceService {

    /** El restaurante opera en Cartagena: los "días" se cortan a medianoche de Colombia. */
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final String[] DAYS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final String[] NAMES = {"Valentina R.", "Santiago M.", "Camila G.", "Mateo L.", "Isabella P.", "Sebastián T.",
            "Mariana C.", "Daniel V.", "Sofía H.", "Andrés B.", "Laura D.", "Felipe O."};
    private static final String[] DISHES = {"2x Sushi Roll Nobu", "1x Black Cod Miso", "3x Edamame", "2x Tuna Tataki", "1x Wagyu Tacos", "2x Yellowtail Jalapeño"};

    private final PaymentRepo repo;

    public FinanceSummary summary(String period) {
        String per = period == null ? "7d" : period;
        LocalDate today = LocalDate.now(ZONE);
        Instant from;
        Instant prevFrom = null;
        switch (per) {
            case "today" -> {
                from = today.atStartOfDay(ZONE).toInstant();
                prevFrom = today.minusDays(1).atStartOfDay(ZONE).toInstant();
            }
            case "30d" -> {
                from = today.minusDays(29).atStartOfDay(ZONE).toInstant();
                prevFrom = today.minusDays(59).atStartOfDay(ZONE).toInstant();
            }
            case "all" -> from = Instant.EPOCH;
            default -> {
                per = "7d";
                from = today.minusDays(6).atStartOfDay(ZONE).toInstant();
                prevFrom = today.minusDays(13).atStartOfDay(ZONE).toInstant();
            }
        }

        List<Payment> list = repo.findByCreatedAtGreaterThanEqual(from);
        List<Payment> paid = list.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).collect(Collectors.toList());

        FinanceSummary s = new FinanceSummary();
        s.setPeriod(per);
        s.setServerTime(Instant.now());
        s.setLastPaymentId(repo.maxId());
        s.setCollectedCents(sum(paid));
        s.setPaidCount(paid.size());
        s.setAvgTicketCents(paid.isEmpty() ? 0 : sum(paid) / paid.size());
        s.setPendingCents(sum(list.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).collect(Collectors.toList())));
        s.setPendingCount(list.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).count());
        s.setRefundedCents(sum(list.stream().filter(p -> p.getStatus() == PaymentStatus.REFUNDED).collect(Collectors.toList())));
        s.setFailedCount(list.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count());

        if (prevFrom != null) {
            s.setPreviousCollectedCents(sum(repo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(prevFrom, from).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.PAID).collect(Collectors.toList())));
        }

        Map<String, long[]> byMethod = new LinkedHashMap<>();
        for (PaymentMethodType m : PaymentMethodType.values()) byMethod.put(m.name(), new long[2]);
        Map<String, long[]> byConcept = new LinkedHashMap<>();
        for (PaymentConcept c : PaymentConcept.values()) byConcept.put(c.name(), new long[2]);
        for (Payment p : paid) {
            long[] a = byMethod.get(p.getMethod().name());
            a[0] += p.getTotalCents();
            a[1]++;
            long[] b = byConcept.get(p.getConcept().name());
            b[0] += p.getTotalCents();
            b[1]++;
        }
        s.setByMethod(byMethod.entrySet().stream().map(e -> new Slice(e.getKey(), e.getValue()[0], e.getValue()[1])).collect(Collectors.toList()));
        s.setByConcept(byConcept.entrySet().stream().map(e -> new Slice(e.getKey(), e.getValue()[0], e.getValue()[1])).collect(Collectors.toList()));

        buildSeries(s, per, paid, today);

        s.setRecent(repo.findTop40ByOrderByCreatedAtDesc().stream().map(PaymentService::toDto).collect(Collectors.toList()));
        s.setToCollect(repo.findByStatusOrderByCreatedAtAsc(PaymentStatus.PENDING).stream().map(PaymentService::toDto).collect(Collectors.toList()));
        return s;
    }

    private void buildSeries(FinanceSummary s, String per, List<Payment> paid, LocalDate today) {
        List<SeriesPoint> pts = new ArrayList<>();
        if (per.equals("today")) {
            long[] hours = new long[24];
            int minH = 12;
            int maxH = ZonedDateTime.now(ZONE).getHour();
            for (Payment p : paid) {
                int h = p.getCreatedAt().atZone(ZONE).getHour();
                hours[h] += p.getTotalCents();
                minH = Math.min(minH, h);
                maxH = Math.max(maxH, h);
            }
            maxH = Math.max(maxH, Math.min(23, minH + 5));
            for (int h = minH; h <= maxH; h++) pts.add(new SeriesPoint(h + "h", hours[h]));
            s.setSeriesUnit("hour");
        } else {
            int days = per.equals("30d") ? 30 : per.equals("all") ? 14 : 7;
            Map<LocalDate, Long> byDay = new HashMap<>();
            for (Payment p : paid) byDay.merge(p.getCreatedAt().atZone(ZONE).toLocalDate(), p.getTotalCents(), Long::sum);
            for (int i = days - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                String label = days <= 14 ? DAYS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() : String.valueOf(d.getDayOfMonth());
                pts.add(new SeriesPoint(label, byDay.getOrDefault(d, 0L)));
            }
            s.setSeriesUnit("day");
        }
        s.setSeries(pts);
    }

    private static long sum(List<Payment> l) {
        return l.stream().mapToLong(Payment::getTotalCents).sum();
    }

    // ───────────────────────── Acciones del administrador ─────────────────────────

    public PaymentDto setStatus(Long id, PaymentStatus status) {
        if (status == null) throw new PaymentException(HttpStatus.BAD_REQUEST, "Estado no válido");
        Payment p = repo.findById(id).orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        if (status == PaymentStatus.PAID && p.getStatus() == PaymentStatus.PENDING) {
            p.setStatus(PaymentStatus.PAID);
            p.setPaidAt(Instant.now());
        } else if (status == PaymentStatus.REFUNDED && p.getStatus() == PaymentStatus.PAID) {
            p.setStatus(PaymentStatus.REFUNDED);
        } else if (status == PaymentStatus.FAILED && p.getStatus() == PaymentStatus.PENDING) {
            p.setStatus(PaymentStatus.FAILED);
            p.setFailureReason("Rechazado por el administrador");
        } else {
            throw new PaymentException(HttpStatus.CONFLICT, "Ese cambio de estado no es posible");
        }
        return PaymentService.toDto(repo.save(p));
    }

    // ───────────────────────── Datos de demostración ─────────────────────────

    /** Genera pagos de ejemplo de los últimos días (marcados demo=true) para poder presentar el módulo. */
    public int seedDemo() {
        Random rnd = new Random();
        int created = 0;
        for (int i = 0; i < 28; i++) {
            Instant when = Instant.now().minus(Duration.ofMinutes((long) (rnd.nextDouble() * 60 * 24 * 9)));
            repo.save(randomDemo(rnd, when, i % 11 == 0));
            created++;
        }
        return created;
    }

    /** Un pago nuevo "ahora": sirve para ver cómo el panel reacciona en vivo durante la presentación. */
    public PaymentDto demoTick() {
        Payment p = repo.save(randomDemo(new Random(), Instant.now(), false));
        return PaymentService.toDto(p);
    }

    public int clearDemo() {
        List<Payment> demo = repo.findByDemoTrue();
        repo.deleteAll(demo);
        return demo.size();
    }

    private Payment randomDemo(Random rnd, Instant when, boolean failed) {
        Payment p = new Payment();
        p.setDemo(true);
        p.setUserId(null);
        p.setCustomerName(NAMES[rnd.nextInt(NAMES.length)]);
        PaymentMethodType[] ms = {PaymentMethodType.CARD, PaymentMethodType.CARD, PaymentMethodType.CARD, PaymentMethodType.TRANSFER, PaymentMethodType.CASH};
        p.setMethod(ms[rnd.nextInt(ms.length)]);
        int guests = 1 + rnd.nextInt(6);
        long subtotal = (18 + rnd.nextInt(90)) * 100L;
        boolean hasOrder = rnd.nextInt(4) != 0;
        boolean prepaid = p.getMethod() != PaymentMethodType.CASH;
        p.setGuests(guests);
        p.setSubtotalCents(hasOrder ? subtotal : 0);
        p.setDepositCents(prepaid ? guests * 500L : 0);
        int tipPct = hasOrder ? new int[]{0, 10, 10, 15}[rnd.nextInt(4)] : 0;
        p.setTipPercent(tipPct);
        p.setTipCents(p.getSubtotalCents() * tipPct / 100);
        p.setTotalCents(p.getSubtotalCents() + p.getDepositCents() + p.getTipCents());
        if (p.getTotalCents() == 0) {
            p.setSubtotalCents(subtotal);
            p.setTotalCents(subtotal);
            hasOrder = true;
        }
        p.setItems(hasOrder ? DISHES[rnd.nextInt(DISHES.length)] : null);
        p.setConcept(p.getSubtotalCents() > 0 && p.getDepositCents() > 0 ? PaymentConcept.MIXED
                : p.getSubtotalCents() > 0 ? PaymentConcept.ORDER : PaymentConcept.RESERVATION);
        p.setCreatedAt(when);
        if (p.getMethod() == PaymentMethodType.CARD) {
            p.setCardBrand(rnd.nextBoolean() ? "visa" : "mastercard");
            p.setCardLast4(String.format("%04d", 1000 + rnd.nextInt(8999)));
            if (failed) {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureReason("Tarjeta rechazada por el banco emisor");
            } else {
                p.setStatus(PaymentStatus.PAID);
                p.setPaidAt(when);
            }
        } else if (rnd.nextInt(5) == 0 && when.isAfter(Instant.now().minus(Duration.ofHours(30)))) {
            p.setStatus(PaymentStatus.PENDING);
        } else {
            p.setStatus(PaymentStatus.PAID);
            p.setPaidAt(when);
        }
        Payment saved = repo.save(p);
        saved.setReference("NM-" + String.format("%06d", saved.getId()));
        return saved;
    }
}
