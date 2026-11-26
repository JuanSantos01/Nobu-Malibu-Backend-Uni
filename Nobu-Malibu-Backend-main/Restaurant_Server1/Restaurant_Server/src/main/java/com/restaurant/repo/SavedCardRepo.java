package com.restaurant.repo;

import com.restaurant.entity.SavedCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedCardRepo extends JpaRepository<SavedCard, Long> {

    List<SavedCard> findByUserIdOrderByIdDesc(Long userId);
}
