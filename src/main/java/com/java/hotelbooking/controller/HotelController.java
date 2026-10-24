package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.HotelRequest;
import com.java.hotelbooking.domain.dto.HotelResponse;
import com.java.hotelbooking.domain.dto.RoomRequest;
import com.java.hotelbooking.domain.dto.RoomResponse;
import com.java.hotelbooking.service.HotelService;
import com.java.hotelbooking.service.RoomService;
import com.java.hotelbooking.web.annotation.PostCreated;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final RoomService roomService;

    @GetMapping
    public List<HotelResponse> list(@RequestParam(required = false) String city) {
        return hotelService.list(city);
    }

    @GetMapping("/{id}")
    public HotelResponse getById(@PathVariable Long id) {
        return hotelService.getById(id);
    }

    @PostCreated
    public HotelResponse create(@Valid @RequestBody HotelRequest request) {
        return hotelService.create(request);
    }

    @PutMapping("/{id}")
    public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelRequest request) {
        return hotelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        hotelService.delete(id);
    }

    @GetMapping("/{hotelId}/rooms")
    public List<RoomResponse> listRooms(@PathVariable Long hotelId) {
        return roomService.listByHotel(hotelId);
    }

    @PostCreated("/{hotelId}/rooms")
    public RoomResponse createRoom(@PathVariable Long hotelId, @Valid @RequestBody RoomRequest request) {
        return roomService.create(hotelId, request);
    }
}
