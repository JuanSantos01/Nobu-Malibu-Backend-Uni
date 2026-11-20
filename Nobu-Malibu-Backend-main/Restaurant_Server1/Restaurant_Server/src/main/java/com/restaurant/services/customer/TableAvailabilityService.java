package com.restaurant.services.customer;

import com.restaurant.configs.TableCapacity;
import com.restaurant.dto.AvailabilityDto;
import com.restaurant.entity.Reservation;
import com.restaurant.repo.TableReservationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/** Reglas de cupo: cada tipo de mesa tiene N mesas físicas; PENDING y APPROVED ocupan mesa. */
@Service
@RequiredArgsConstructor
public class TableAvailabilityService {

    private static final long MIN_MS = 60_000L;

    private final TableReservationRepo repo;

    public static int durationOrDefault(Integer minutes) {
        return minutes == null || minutes <= 0 ? TableCapacity.DEFAULT_DURATION_MINUTES : minutes;
    }

    private static Date endOf(Date start, Integer minutes) {
        return new Date(start.getTime() + durationOrDefault(minutes) * MIN_MS);
    }

    /** Reservas que ocupan mesa y que podrían tocar el intervalo [from, to). */
    private List<Reservation> blocking(String tableType, Date from, Date to) {
        Date earliestStart = new Date(from.getTime() - TableCapacity.MAX_DURATION_MINUTES * MIN_MS);
        return repo.findBlocking(tableType, earliestStart, to).stream()
                .filter(r -> endOf(r.getDateTime(), r.getDurationMinutes()).after(from))
                .collect(Collectors.toList());
    }

    public AvailabilityDto availability(String tableType, Date from, Date to) {
        Integer capacity = TableCapacity.BY_TYPE.get(tableType);
        if (capacity == null) throw new TableConflictException("Tipo de mesa no válido");
        List<AvailabilityDto.Booking> bookings = new ArrayList<>();
        for (Reservation r : blocking(tableType, from, to)) {
            bookings.add(new AvailabilityDto.Booking(r.getDateTime(), endOf(r.getDateTime(), r.getDurationMinutes())));
        }
        return new AvailabilityDto(tableType, capacity, bookings);
    }

    /**
     * Lanza TableConflictException si no queda ninguna mesa libre durante TODO el intervalo.
     * `excludeId` permite re-validar una reserva existente sin contarse a sí misma.
     */
    public void assertRoom(String tableType, Date start, Integer durationMinutes, Long excludeId) {
        Integer capacity = TableCapacity.BY_TYPE.get(tableType);
        if (capacity == null) throw new TableConflictException("Tipo de mesa no válido");
        if (start == null) throw new TableConflictException("Fecha no válida");
        int d = durationOrDefault(durationMinutes);
        if (d < TableCapacity.MIN_DURATION_MINUTES || d > TableCapacity.MAX_DURATION_MINUTES)
            throw new TableConflictException("La duración debe estar entre 30 minutos y 4 horas");

        Date end = endOf(start, d);
        List<Reservation> others = blocking(tableType, start, end).stream()
                .filter(r -> excludeId == null || !r.getId().equals(excludeId))
                .collect(Collectors.toList());

        // Ocupación máxima simultánea: basta revisar el inicio y cada inicio ajeno dentro del intervalo.
        List<Long> checkpoints = new ArrayList<>();
        checkpoints.add(start.getTime());
        for (Reservation r : others) {
            long t = r.getDateTime().getTime();
            if (t > start.getTime() && t < end.getTime()) checkpoints.add(t);
        }
        for (long t : checkpoints) {
            long active = others.stream()
                    .filter(r -> r.getDateTime().getTime() <= t && endOf(r.getDateTime(), r.getDurationMinutes()).getTime() > t)
                    .count();
            if (active >= capacity)
                throw new TableConflictException("No hay mesas disponibles de este tipo en ese horario. Elige otro horario o tipo de mesa.");
        }
    }
}
