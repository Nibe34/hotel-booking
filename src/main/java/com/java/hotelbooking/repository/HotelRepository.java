package com.java.hotelbooking.repository;

import com.java.hotelbooking.domain.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    List<Hotel> findByCityIgnoreCaseOrderByName(String city);

    List<Hotel> findAllByOrderByName();

    boolean existsByNameIgnoreCaseAndCityIgnoreCase(String name, String city);
}
