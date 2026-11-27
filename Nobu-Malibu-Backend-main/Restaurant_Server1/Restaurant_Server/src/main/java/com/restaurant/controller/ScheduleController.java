package com.restaurant.controller;

import com.restaurant.entity.ClosedDay;
import com.restaurant.entity.RestaurantSchedule;
import com.restaurant.services.customer.TableConflictException;
import com.restaurant.services.schedule.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Horario del restaurante.
 *  - Clientes: GET /api/customer/schedule (para saber qué días/horas se puede reservar).
 *  - Admin: GET/PUT /api/admin/schedule, POST /api/admin/closed-days, DELETE /api/admin/closed-days/{id}.
 */
@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService schedule;

    private Map<String, Object> snapshot() {
        RestaurantSchedule s = schedule.get();
        List<Map<String, Object>> closed = new ArrayList<>();
        for (ClosedDay c : schedule.upcomingClosedDays()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("date", c.getDay().toString());
            m.put("reason", c.getReason());
            closed.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("openMinute", s.getOpenMinute());
        out.put("closeMinute", s.getCloseMinute());
        out.put("closedWeekdays", new ArrayList<>(schedule.closedWeekdays(s)));
        out.put("closedDays", closed);
        out.put("timezone", schedule.timezoneId());
        return out;
    }

    @GetMapping("/api/customer/schedule")
    public ResponseEntity<Map<String, Object>> customerSchedule() {
        return ResponseEntity.ok(snapshot());
    }

    @GetMapping("/api/admin/schedule")
    public ResponseEntity<Map<String, Object>> adminSchedule() {
        return ResponseEntity.ok(snapshot());
    }

    @PutMapping("/api/admin/schedule")
    public ResponseEntity<?> update(@RequestBody Map<String, Object> body) {
        try {
            int open = ((Number) body.get("openMinute")).intValue();
            int close = ((Number) body.get("closeMinute")).intValue();
            List<?> raw = body.get("closedWeekdays") instanceof List<?> l ? l : List.of();
            List<Integer> weekdays = raw.stream().map(x -> ((Number) x).intValue()).collect(Collectors.toList());
            schedule.update(open, close, weekdays);
            return ResponseEntity.ok(snapshot());
        } catch (TableConflictException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (ClassCastException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Datos de horario no válidos"));
        }
    }

    @PostMapping("/api/admin/closed-days")
    public ResponseEntity<?> closeDay(@RequestBody Map<String, String> body) {
        try {
            LocalDate day = LocalDate.parse(body.get("date"));
            int affected = schedule.closeDay(day, body.get("reason"));
            Map<String, Object> out = snapshot();
            out.put("affectedReservations", affected);
            return ResponseEntity.ok(out);
        } catch (TableConflictException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (DateTimeParseException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Fecha no válida (usa AAAA-MM-DD)"));
        }
    }

    @DeleteMapping("/api/admin/closed-days/{id}")
    public ResponseEntity<Map<String, Object>> reopen(@PathVariable Long id) {
        schedule.reopenDay(id);
        return ResponseEntity.ok(snapshot());
    }
}
