package com.restaurant.repo;

import com.restaurant.entity.ClosedDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClosedDayRepo extends JpaRepository<ClosedDay, Long> {
    List<ClosedDay> findAllByDayGreaterThanEqualOrderByDayAsc(LocalDate from);

    Optional<ClosedDay> findByDay(LocalDate day);
}
