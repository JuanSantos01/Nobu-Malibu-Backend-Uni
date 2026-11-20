package com.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityDto {

    private String tableType;

    /** Cantidad de mesas físicas de este tipo. */
    private int capacity;

    /** Intervalos ya ocupados (reservas PENDING o APPROVED). */
    private List<Booking> bookings;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Booking {
        private Date start;
        private Date end;
    }
}
