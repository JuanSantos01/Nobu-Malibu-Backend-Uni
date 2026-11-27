package com.restaurant.services.schedule;

import com.restaurant.configs.TableCapacity;
import com.restaurant.entity.ClosedDay;
import com.restaurant.entity.RestaurantSchedule;
import com.restaurant.enums.ReservationStatus;
import com.restaurant.repo.ClosedDayRepo;
import com.restaurant.repo.ScheduleRepo;
import com.restaurant.repo.TableReservationRepo;
import com.restaurant.services.customer.TableConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Horario de apertura/cierre, días de descanso y días cerrados puntuales. */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String[] DIAS = {"", "lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos"};

    private final ScheduleRepo scheduleRepo;
    private final ClosedDayRepo closedDayRepo;
    private final TableReservationRepo reservationRepo;

    @Value("${restaurant.timezone:America/Bogota}")
    private String timezone;

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public String timezoneId() {
        return timezone;
    }

    public synchronized RestaurantSchedule get() {
        return scheduleRepo.findById(1L).orElseGet(() -> scheduleRepo.save(new RestaurantSchedule()));
    }

    public Set<Integer> closedWeekdays(RestaurantSchedule s) {
        if (s.getClosedWeekdays() == null || s.getClosedWeekdays().isBlank()) return new TreeSet<>();
        return Arrays.stream(s.getClosedWeekdays().split(","))
                .map(String::trim).filter(x -> !x.isEmpty())
                .map(Integer::parseInt).filter(n -> n >= 1 && n <= 7)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public synchronized RestaurantSchedule update(int openMinute, int closeMinute, Collection<Integer> closedWeekdays) {
        if (openMinute < 0 || closeMinute > 24 * 60 || closeMinute - openMinute < 60)
            throw new TableConflictException("El horario debe tener al menos una hora de atención");
        if (closedWeekdays.stream().anyMatch(n -> n < 1 || n > 7)) throw new TableConflictException("Día de la semana no válido");
        if (closedWeekdays.size() >= 7) throw new TableConflictException("No puedes cerrar todos los días de la semana");
        RestaurantSchedule s = get();
        s.setOpenMinute(openMinute);
        s.setCloseMinute(closeMinute);
        s.setClosedWeekdays(closedWeekdays.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        return scheduleRepo.save(s);
    }

    public List<ClosedDay> upcomingClosedDays() {
        return closedDayRepo.findAllByDayGreaterThanEqualOrderByDayAsc(LocalDate.now(zone()));
    }

    /** Cierra un día puntual. Devuelve cuántas reservas vivas (pendientes/aprobadas) ese día quedan por resolver. */
    public synchronized int closeDay(LocalDate day, String reason) {
        if (day.isBefore(LocalDate.now(zone()))) throw new TableConflictException("No puedes cerrar un día que ya pasó");
        ClosedDay c = closedDayRepo.findByDay(day).orElseGet(ClosedDay::new);
        c.setDay(day);
        c.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        closedDayRepo.save(c);
        return affectedReservations(day);
    }

    public void reopenDay(Long id) {
        closedDayRepo.deleteById(id);
    }

    public int affectedReservations(LocalDate day) {
        Date from = Date.from(day.atStartOfDay(zone()).toInstant());
        Date to = Date.from(day.plusDays(1).atStartOfDay(zone()).toInstant());
        long n = 0;
        for (String type : TableCapacity.BY_TYPE.keySet())
            n += reservationRepo.findBlocking(type, from, to).stream()
                    .filter(r -> r.getReservationStatus() != ReservationStatus.DISAPPROVED).count();
        return (int) n;
    }

    /** Minutos desde medianoche (hora local del restaurante) del instante dado. */
    public int minuteOfDay(Date when) {
        ZonedDateTime z = when.toInstant().atZone(zone());
        return z.getHour() * 60 + z.getMinute();
    }

    /** Minutos que faltan para el cierre desde `start`. */
    public int minutesUntilClose(Date start) {
        return get().getCloseMinute() - minuteOfDay(start);
    }

    /** Momento (instante) del cierre del día de `start`. */
    public Date closeInstant(Date start) {
        LocalDate d = start.toInstant().atZone(zone()).toLocalDate();
        return Date.from(d.atStartOfDay(zone()).plusMinutes(get().getCloseMinute()).toInstant());
    }

    public String formatMinute(int minute) {
        int h = (minute / 60) % 24, m = minute % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + String.format("%02d", m) + (h < 12 ? " a. m." : " p. m.");
    }

    public String formatInstant(Date when) {
        return formatMinute(minuteOfDay(when));
    }

    /** Verifica que el restaurante esté abierto durante todo el intervalo [start, start + durationMinutes]. */
    public void assertOpen(Date start, int durationMinutes) {
        ZonedDateTime z = start.toInstant().atZone(zone());
        LocalDate day = z.toLocalDate();
        RestaurantSchedule s = get();

        Optional<ClosedDay> closed = closedDayRepo.findByDay(day);
        if (closed.isPresent()) {
            String why = closed.get().getReason();
            throw new TableConflictException("Ese día estaremos cerrados" + (why == null ? "." : " (" + why + ").") + " ¡Te esperamos otro día!", "CLOSED", null);
        }
        int dow = z.getDayOfWeek().getValue();
        if (closedWeekdays(s).contains(dow))
            throw new TableConflictException("Los " + DIAS[dow] + " descansamos. ¡Te esperamos otro día!", "CLOSED", null);

        int minute = z.getHour() * 60 + z.getMinute();
        if (minute < s.getOpenMinute())
            throw new TableConflictException("Abrimos a las " + formatMinute(s.getOpenMinute()) + ".", "CLOSED", null);
        if (minute + TableCapacity.MIN_DURATION_MINUTES > s.getCloseMinute())
            throw new TableConflictException("La última reserva es 30 minutos antes del cierre (" + formatMinute(s.getCloseMinute()) + ").", "CLOSED", null);
        if (minute + durationMinutes > s.getCloseMinute())
            throw new TableConflictException("Cerramos a las " + formatMinute(s.getCloseMinute()) + ": tu reserva terminaría a las "
                    + formatMinute(minute + durationMinutes) + ". Elige una duración menor o 'hasta el cierre'.", "CLOSED", null);
    }
}
