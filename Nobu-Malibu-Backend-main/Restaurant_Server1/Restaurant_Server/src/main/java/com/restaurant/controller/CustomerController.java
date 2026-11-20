package com.restaurant.controller;

import com.restaurant.dto.CategoryDto;
import com.restaurant.dto.ProductDto;
import com.restaurant.dto.TableReservationDto;
import com.restaurant.services.customer.CustomerService;
import lombok.RequiredArgsConstructor;
import com.restaurant.services.customer.TableConflictException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    //// Categories Operations

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getAllCategories() {
        List<CategoryDto> categoryDtos = customerService.getAllCategories();
        return ResponseEntity.ok(categoryDtos);
    }

    @GetMapping("/categories/{title}")
    public ResponseEntity<List<CategoryDto>> searchCategoryByTitle(@PathVariable("title") String title) {
        List<CategoryDto> categoryDtos = customerService.searchCategoryByTitle(title);
        return ResponseEntity.ok(categoryDtos);
    }


    //// Product Operations

    @GetMapping("/{categoryId}/products")
    public ResponseEntity<List<ProductDto>> getProductsByCategory(@PathVariable Long categoryId) {
        List<ProductDto> productDtos = customerService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(productDtos);
    }

    @GetMapping("/{categoryId}/products/{title}")
    public ResponseEntity<List<ProductDto>> searchProductByCategoryAndTitle(@PathVariable("categoryId") Long categoryId, @PathVariable("title") String title) {
        List<ProductDto> productDtos = customerService.searchProductByCategoryAndTitle(categoryId, title);
        return ResponseEntity.ok(productDtos);
    }


    ////// Reserve Table Operations

    @PostMapping("/reservation")
    public ResponseEntity<?> applyForReservation(@RequestBody TableReservationDto tableReservationDto) {
        try {
            TableReservationDto appliedTableReservationDto = customerService.applyForReservation(tableReservationDto);
            if (appliedTableReservationDto == null)
                return new ResponseEntity<>("Something went wrong!", HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(HttpStatus.CREATED).body(appliedTableReservationDto);
        } catch (TableConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    /** Intervalos ocupados de un tipo de mesa entre `from` y `to` (ISO-8601), para pintar horarios agotados. */
    @GetMapping("/availability")
    public ResponseEntity<?> getAvailability(@RequestParam String tableType,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date to) {
        try {
            return ResponseEntity.ok(customerService.getAvailability(tableType, from, to));
        } catch (TableConflictException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/reservations/{userId}")
    public ResponseEntity<List<TableReservationDto>> getAllReservationByUserId(@PathVariable Long userId) {
        List<TableReservationDto> tableReservationDtos = customerService.getAllReservationByUserId(userId);
        return ResponseEntity.ok(tableReservationDtos);
    }

}
