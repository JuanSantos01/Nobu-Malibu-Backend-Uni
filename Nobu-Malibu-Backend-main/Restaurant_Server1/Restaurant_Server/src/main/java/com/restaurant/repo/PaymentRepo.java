package com.restaurant.repo;

import com.restaurant.entity.Payment;
import com.restaurant.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {

    List<Payment> findByCreatedAtGreaterThanEqual(Instant from);

    List<Payment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    List<Payment> findTop40ByOrderByCreatedAtDesc();

    List<Payment> findByStatusOrderByCreatedAtAsc(PaymentStatus status);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByReservationIdOrderByCreatedAtDesc(Long reservationId);

    List<Payment> findByDemoTrue();

    @Query("select coalesce(max(p.id), 0) from Payment p")
    Long maxId();
}
