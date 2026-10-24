package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.RoomRequest;
import com.java.hotelbooking.domain.dto.RoomResponse;
import com.java.hotelbooking.domain.mapper.RoomMapper;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.domain.model.Room;
import com.java.hotelbooking.exception.BadRequestException;
import com.java.hotelbooking.exception.ConflictException;
import com.java.hotelbooking.exception.NotFoundException;
import com.java.hotelbooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelService hotelService;
    private final RoomMapper roomMapper;

    public List<RoomResponse> listByHotel(Long hotelId) {
        hotelService.findHotel(hotelId);
        return roomRepository.findByHotelIdOrderByRoomNumber(hotelId).stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    public RoomResponse getById(Long id) {
        return roomMapper.toResponse(findRoom(id));
    }

    public List<RoomResponse> findAvailable(LocalDate checkIn, LocalDate checkOut, int guests) {
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("checkOut must be after checkIn");
        }
        return roomRepository.findAvailable(checkIn, checkOut, guests, BookingService.ACTIVE_STATUSES).stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Transactional
    public RoomResponse create(Long hotelId, RoomRequest request) {
        Hotel hotel = hotelService.findHotel(hotelId);
        if (roomRepository.existsByHotelIdAndRoomNumber(hotelId, request.roomNumber())) {
            throw new ConflictException("Room " + request.roomNumber() + " already exists in this hotel");
        }
        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = findRoom(id);
        if (!room.getRoomNumber().equals(request.roomNumber())
                && roomRepository.existsByHotelIdAndRoomNumber(room.getHotel().getId(), request.roomNumber())) {
            throw new ConflictException("Room " + request.roomNumber() + " already exists in this hotel");
        }
        roomMapper.updateEntity(request, room);
        return roomMapper.toResponse(room);
    }

    @Transactional
    public void delete(Long id) {
        roomRepository.delete(findRoom(id));
    }

    private Room findRoom(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> new NotFoundException("Room", id));
    }
}
