package com.restaurant.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

/** Horario del restaurante. Siempre es una sola fila (id = 1). Las horas son minutos desde medianoche (hora local). */
@Entity
@Data
public class RestaurantSchedule {

    @Id
    private Long id = 1L;

    /** Apertura: 12:00 por defecto. */
    private int openMinute = 12 * 60;

    /** Cierre: 23:00 por defecto. Ninguna reserva puede terminar después. */
    private int closeMinute = 23 * 60;

    /** Días de la semana en que NO se abre, ISO (1 = lunes … 7 = domingo), separados por coma. Ej.: "1,2". */
    private String closedWeekdays = "";
}
