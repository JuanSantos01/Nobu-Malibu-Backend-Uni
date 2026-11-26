package com.restaurant.dto;

import com.restaurant.enums.PaymentConcept;
import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** Contenedor de los DTO del módulo de pagos y de NOBU FINANCE. Importes en centavos. */
public class PaymentDtos {

    @Data
    public static class OrderItem {
        /** Preferido: el precio se toma del servidor, nunca del cliente. */
        private Long productId;
        /** Alternativa (reservas antiguas solo guardan el nombre). */
        private String name;
        private Integer qty;
    }

    @Data
    public static class CardInput {
        private String brand;
        private String last4;
        private String holder;
        private Integer expMonth;
        private Integer expYear;
        private boolean save;
    }

    @Data
    public static class PaymentRequest {
        private Long reservationId;
        private PaymentMethodType method;
        private Long savedCardId;
        private CardInput card;
        private List<OrderItem> items;
        private Integer guests;
        private Integer tipPercent;
        private String transferReference;
    }

    @Data
    public static class QuoteResponse {
        private long subtotalCents;
        private long depositCents;
        private long tipCents;
        private long totalCents;
    }

    @Data
    public static class PaymentDto {
        private Long id;
        private Long userId;
        private String customerName;
        private Long reservationId;
        private PaymentMethodType method;
        private PaymentStatus status;
        private PaymentConcept concept;
        private long subtotalCents;
        private long depositCents;
        private long tipCents;
        private long totalCents;
        private Integer tipPercent;
        private Integer guests;
        private String cardBrand;
        private String cardLast4;
        private String reference;
        private String transferReference;
        private String items;
        private String failureReason;
        private boolean demo;
        private Instant createdAt;
        private Instant paidAt;
    }

    @Data
    public static class SavedCardDto {
        private Long id;
        private String brand;
        private String last4;
        private String holder;
        private Integer expMonth;
        private Integer expYear;
        private boolean defaultCard;
    }

    @Data
    public static class BankAccountDto {
        private String bankName;
        private String accountType;
        private String accountNumber;
        private String holderName;
        private String documentId;
        private String alias;
        private boolean configured;
    }

    @Data
    public static class StatusRequest {
        private PaymentStatus status;
    }

    @Data
    public static class ReferenceRequest {
        private String reference;
    }

    @Data
    public static class Slice {
        private String key;
        private long cents;
        private long count;

        public Slice() {
        }

        public Slice(String key, long cents, long count) {
            this.key = key;
            this.cents = cents;
            this.count = count;
        }
    }

    @Data
    public static class SeriesPoint {
        private String label;
        private long cents;

        public SeriesPoint() {
        }

        public SeriesPoint(String label, long cents) {
            this.label = label;
            this.cents = cents;
        }
    }

    @Data
    public static class FinanceSummary {
        private String period;
        private Instant serverTime;
        private long lastPaymentId;
        private long collectedCents;
        private long previousCollectedCents;
        private long pendingCents;
        private long refundedCents;
        private long paidCount;
        private long pendingCount;
        private long failedCount;
        private long avgTicketCents;
        private List<Slice> byMethod;
        private List<Slice> byConcept;
        private String seriesUnit;
        private List<SeriesPoint> series;
        private List<PaymentDto> recent;
        private List<PaymentDto> toCollect;
    }
}
