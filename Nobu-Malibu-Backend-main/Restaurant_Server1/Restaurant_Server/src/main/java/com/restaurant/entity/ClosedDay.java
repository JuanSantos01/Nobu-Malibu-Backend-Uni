package com.restaurant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/** Día puntual en que el restaurante no abre (feriado, evento privado, mantenimiento…). */
@Entity
@Data
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "closed_date"))
public class ClosedDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closed_date", nullable = false)
    private LocalDate day;

    private String reason;
}
