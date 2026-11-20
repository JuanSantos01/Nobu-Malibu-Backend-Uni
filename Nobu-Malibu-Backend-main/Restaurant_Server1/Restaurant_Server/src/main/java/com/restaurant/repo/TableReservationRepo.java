package com.restaurant.repo;

import com.restaurant.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;

@Repository
public interface TableReservationRepo extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByUserId(Long userId);

    /** Reservas que ocupan mesa (todas menos las rechazadas) con inicio en [from, to). */
    @Query("select r from Reservation r where r.tableType = :type "
            + "and r.reservationStatus <> com.restaurant.enums.ReservationStatus.DISAPPROVED "
            + "and r.dateTime >= :from and r.dateTime < :to")
    List<Reservation> findBlocking(@Param("type") String type, @Param("from") Date from, @Param("to") Date to);
}
