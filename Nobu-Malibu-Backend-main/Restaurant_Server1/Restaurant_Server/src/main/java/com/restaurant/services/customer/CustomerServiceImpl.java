package com.restaurant.services.customer;

import com.restaurant.dto.AvailabilityDto;
import com.restaurant.dto.CategoryDto;
import com.restaurant.dto.ProductDto;
import com.restaurant.dto.TableReservationDto;
import com.restaurant.entity.Category;
import com.restaurant.entity.Product;
import com.restaurant.entity.Reservation;
import com.restaurant.entity.User;
import com.restaurant.enums.ReservationStatus;
import com.restaurant.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final UserRepo userRepo;

    private final CategoryRepo categoryRepo;

    private final ProductRepo productRepo;

    private final TableReservationRepo tableReservationRepo;

    private final TableAvailabilityService availabilityService;


    //// Categories Operations

    @Override
    public List<CategoryDto> getAllCategories() {
        return categoryRepo.findAll().stream().map(Category::getCategoryDto).collect(Collectors.toList());
    }

    @Override
    public List<CategoryDto> searchCategoryByTitle(String title) {
        return categoryRepo.findAllByNameContaining(title).stream().map(Category::getCategoryDto).collect(Collectors.toList());
    }


    //// Product Operations

    @Override
    public List<ProductDto> getProductsByCategory(Long categoryId) {
        return productRepo.findAllByCategoryId(categoryId).stream().map(Product::getProductDto).collect(Collectors.toList());
    }

    @Override
    public List<ProductDto> searchProductByCategoryAndTitle(Long categoryId, String title) {
        return productRepo.findAllByCategoryIdAndNameContaining(categoryId, title).stream().map(Product::getProductDto).collect(Collectors.toList());
    }


    ////// Reserve Table Operations

    /**
     * synchronized: validar cupo y guardar deben ser atómicos para que dos clientes no tomen la última mesa
     * a la vez (válido con una sola instancia del servidor; con varias habría que usar un bloqueo en BD).
     */
    @Override
    public synchronized TableReservationDto applyForReservation(TableReservationDto tableReservationDto) {
        Optional<User> optionalUser = userRepo.findById(tableReservationDto.getUserId());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            Date start = tableReservationDto.getDateTime();
            if (start != null && start.getTime() < System.currentTimeMillis() - 5 * 60_000L)
                throw new TableConflictException("La fecha de la reserva ya pasó");
            availabilityService.assertRoom(tableReservationDto.getTableType(), start, tableReservationDto.getDurationMinutes(), null);
            Reservation reservation = new Reservation();
            reservation.setTableType(tableReservationDto.getTableType());
            reservation.setDescription(tableReservationDto.getDescription());
            reservation.setReservationStatus(ReservationStatus.PENDING);
            reservation.setDateTime(tableReservationDto.getDateTime());
            reservation.setDurationMinutes(TableAvailabilityService.durationOrDefault(tableReservationDto.getDurationMinutes()));
            reservation.setUser(user);
            Reservation requestedReservation = tableReservationRepo.save(reservation);
            TableReservationDto requestedTableReservationDto = new TableReservationDto();
            requestedTableReservationDto.setId(requestedReservation.getId());
            return requestedTableReservationDto;
        } else {
            return null;
        }
    }

    @Override
    public AvailabilityDto getAvailability(String tableType, Date from, Date to) {
        return availabilityService.availability(tableType, from, to);
    }

    @Override
    public List<TableReservationDto> getAllReservationByUserId(Long userId) {
        return tableReservationRepo.findAllByUserId(userId).stream().map(Reservation::getTableReservationDto).collect(Collectors.toList());
    }

}

