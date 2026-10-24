package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.HotelRequest;
import com.java.hotelbooking.domain.dto.HotelResponse;
import com.java.hotelbooking.domain.mapper.HotelMapper;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.exception.ConflictException;
import com.java.hotelbooking.exception.NotFoundException;
import com.java.hotelbooking.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotelService {

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    public List<HotelResponse> list(String city) {
        List<Hotel> hotels = StringUtils.hasText(city)
                ? hotelRepository.findByCityIgnoreCaseOrderByName(city.trim())
                : hotelRepository.findAllByOrderByName();
        return hotels.stream().map(hotelMapper::toResponse).toList();
    }

    public HotelResponse getById(Long id) {
        return hotelMapper.toResponse(findHotel(id));
    }

    @Transactional
    public HotelResponse create(HotelRequest request) {
        if (hotelRepository.existsByNameIgnoreCaseAndCityIgnoreCase(request.name(), request.city())) {
            throw new ConflictException("Hotel '" + request.name() + "' already exists in " + request.city());
        }
        Hotel hotel = hotelRepository.save(hotelMapper.toEntity(request));
        return hotelMapper.toResponse(hotel);
    }

    @Transactional
    public HotelResponse update(Long id, HotelRequest request) {
        Hotel hotel = findHotel(id);
        boolean identityChanged = !hotel.getName().equalsIgnoreCase(request.name())
                || !hotel.getCity().equalsIgnoreCase(request.city());
        if (identityChanged
                && hotelRepository.existsByNameIgnoreCaseAndCityIgnoreCase(request.name(), request.city())) {
            throw new ConflictException("Hotel '" + request.name() + "' already exists in " + request.city());
        }
        hotelMapper.updateEntity(request, hotel);
        return hotelMapper.toResponse(hotel);
    }

    @Transactional
    public void delete(Long id) {
        // rooms and their bookings are removed by ON DELETE CASCADE
        hotelRepository.delete(findHotel(id));
    }

    Hotel findHotel(Long id) {
        return hotelRepository.findById(id).orElseThrow(() -> new NotFoundException("Hotel", id));
    }
}
