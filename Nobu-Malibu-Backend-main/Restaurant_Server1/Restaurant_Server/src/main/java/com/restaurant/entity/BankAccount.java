package com.restaurant.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Cuenta bancaria del restaurante donde los clientes hacen transferencias. Una sola fila (id = 1). */
@Entity
@Data
@Table(name = "bank_account")
public class BankAccount {

    @Id
    private Long id = 1L;

    private String bankName;

    private String accountType;

    private String accountNumber;

    private String holderName;

    private String documentId;

    /** Llave / alias de pago instantáneo (Bre-B, Nequi, etc.). */
    private String alias;

    /** false mientras sigan los datos de ejemplo. */
    private boolean configured;
}
