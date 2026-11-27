package com.restaurant.services.customer;

import com.restaurant.configs.TableCapacity;
import com.restaurant.dto.AvailabilityDto;
import com.restaurant.entity.Reservation;
import com.restaurant.repo.TableReservationRepo;
import com.restaurant.services.schedule.ScheduleService;
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
    private final ScheduleService scheduleService;

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

    /** true si en todo [start, end) hay menos reservas simultáneas que mesas. */
    private static boolean fits(int capacity, List<Reservation> others, long start, long end) {
        List<Long> checkpoints = new ArrayList<>();
        checkpoints.add(start);
        for (Reservation r : others) {
            long t = r.getDateTime().getTime();
            if (t > start && t < end) checkpoints.add(t);
        }
        for (long t : checkpoints) {
            long active = others.stream()
                    .filter(r -> r.getDateTime().getTime() <= t && endOf(r.getDateTime(), r.getDurationMinutes()).getTime() > t)
                    .count();
            if (active >= capacity) return false;
        }
        return true;
    }

    /** Primer instante (cuando termina alguna reserva) desde el que cabe la duración pedida; null si hoy ya no. */
    private Date nextFree(int capacity, List<Reservation> others, Date start, int duration, boolean untilClose) {
        Date close = scheduleService.closeInstant(start);
        List<Long> candidates = others.stream()
                .map(r -> endOf(r.getDateTime(), r.getDurationMinutes()).getTime())
                .filter(e -> e > start.getTime()).distinct().sorted().collect(Collectors.toList());
        for (long c : candidates) {
            long minutesLeft = (close.getTime() - c) / MIN_MS;
            if (minutesLeft < TableCapacity.MIN_DURATION_MINUTES) return null;
            long d = untilClose ? minutesLeft : duration;
            if (c + d * MIN_MS > close.getTime()) continue;
            if (fits(capacity, others, c, c + d * MIN_MS)) return new Date(c);
        }
        return null;
    }

    public void assertRoom(String tableType, Date start, Integer durationMinutes, Long excludeId) {
        assertRoom(tableType, start, durationMinutes, excludeId, false);
    }

    /**
     * Lanza TableConflictException si no queda ninguna mesa libre durante TODO el intervalo.
     * Si está lleno, la excepción trae `freeAt`: cuándo se libera una mesa (para el mensaje al cliente).
     * `excludeId` permite re-validar una reserva existente sin contarse a sí misma.
     */
    public void assertRoom(String tableType, Date start, Integer durationMinutes, Long excludeId, boolean untilClose) {
        Integer capacity = TableCapacity.BY_TYPE.get(tableType);
        if (capacity == null) throw new TableConflictException("Tipo de mesa no válido");
        if (start == null) throw new TableConflictException("Fecha no válida");
        int d = durationOrDefault(durationMinutes);
        if (d < TableCapacity.MIN_DURATION_MINUTES || d > TableCapacity.MAX_DURATION_MINUTES)
            throw new TableConflictException("La duración no es válida");

        Date end = endOf(start, d);
        // Ventana amplia: sirve tanto para validar como para calcular cuándo se libera una mesa.
        Date wide = new Date(Math.max(end.getTime(), start.getTime() + 24L * 60 * MIN_MS));
        List<Reservation> others = blocking(tableType, start, wide).stream()
                .filter(r -> excludeId == null || !r.getId().equals(excludeId))
                .collect(Collectors.toList());

        if (!fits(capacity, others, start.getTime(), end.getTime())) {
            Date freeAt = nextFree(capacity, others, start, d, untilClose);
            String msg = freeAt != null
                    ? "Todas nuestras mesas de este tipo están ocupadas a esa hora. Se libera una a las " + scheduleService.formatInstant(freeAt) + "."
                    : "Todas nuestras mesas de este tipo están ocupadas en ese horario. Prueba otro horario, otro día u otro tipo de mesa.";
            throw new TableConflictException(msg, "FULL", freeAt);
        }
    }
}
