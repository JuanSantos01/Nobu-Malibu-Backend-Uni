package com.restaurant.dto;

import com.restaurant.enums.ReservationStatus;
import lombok.Data;

import java.util.Date;

@Data
public class TableReservationDto {

    private Long id;

    private String tableType;

    private String description;

    private Date dateTime;

    private Integer durationMinutes;

    /** Solo en la solicitud: true = la duración será hasta la hora de cierre. */
    private Boolean untilClose;

    private ReservationStatus reservationStatus;

    private Long userId;

    private String username;

}
