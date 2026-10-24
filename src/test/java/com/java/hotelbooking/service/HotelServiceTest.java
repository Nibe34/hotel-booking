package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.HotelRequest;
import com.java.hotelbooking.domain.dto.HotelResponse;
import com.java.hotelbooking.domain.mapper.HotelMapper;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.exception.ConflictException;
import com.java.hotelbooking.exception.NotFoundException;
import com.java.hotelbooking.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private HotelMapper hotelMapper;

    @InjectMocks
    private HotelService hotelService;

    private Hotel hotel(long id, String name, String city) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName(name);
        hotel.setCity(city);
        hotel.setAddress("Main st. 1");
        hotel.setStars(4);
        return hotel;
    }

    private HotelResponse response(Hotel hotel) {
        return new HotelResponse(hotel.getId(), hotel.getName(), hotel.getCity(), hotel.getAddress(),
                hotel.getStars(), hotel.getDescription());
    }

    private HotelRequest request(String name, String city) {
        return new HotelRequest(name, city, "Main st. 1", 4, null);
    }

    // ---------- list ----------

    @Test
    void list_withCity_trimsCityAndFiltersByIt() {
        Hotel hotel = hotel(1L, "Grand", "Kyiv");
        when(hotelRepository.findByCityIgnoreCaseOrderByName("Kyiv")).thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response(hotel));

        List<HotelResponse> result = hotelService.list("  Kyiv ");

        assertThat(result).extracting(HotelResponse::name).containsExactly("Grand");
        verify(hotelRepository, never()).findAllByOrderByName();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void list_withoutCity_returnsAllHotels(String city) {
        Hotel first = hotel(1L, "Alpha", "Lviv");
        Hotel second = hotel(2L, "Beta", "Kyiv");
        when(hotelRepository.findAllByOrderByName()).thenReturn(List.of(first, second));
        when(hotelMapper.toResponse(any(Hotel.class))).thenAnswer(inv -> response(inv.getArgument(0)));

        List<HotelResponse> result = hotelService.list(city);

        assertThat(result).extracting(HotelResponse::id).containsExactly(1L, 2L);
        verify(hotelRepository, never()).findByCityIgnoreCaseOrderByName(anyString());
    }

    // ---------- getById / findHotel ----------

    @Test
    void getById_existingHotel_returnsMappedResponse() {
        Hotel hotel = hotel(3L, "Opera", "Odesa");
        when(hotelRepository.findById(3L)).thenReturn(Optional.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response(hotel));

        HotelResponse result = hotelService.getById(3L);

        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.city()).isEqualTo("Odesa");
    }

    @Test
    void getById_missingHotel_throwsNotFound() {
        when(hotelRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getById(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Hotel with id 9 not found");
    }

    @Test
    void findHotel_existingHotel_returnsEntity() {
        Hotel hotel = hotel(3L, "Opera", "Odesa");
        when(hotelRepository.findById(3L)).thenReturn(Optional.of(hotel));

        assertThat(hotelService.findHotel(3L)).isSameAs(hotel);
    }

    // ---------- create ----------

    @Test
    void create_uniqueNameInCity_savesMappedEntity() {
        HotelRequest request = request("Grand", "Kyiv");
        Hotel mapped = hotel(0L, "Grand", "Kyiv");
        mapped.setId(null);
        Hotel saved = hotel(10L, "Grand", "Kyiv");
        when(hotelRepository.existsByNameIgnoreCaseAndCityIgnoreCase("Grand", "Kyiv")).thenReturn(false);
        when(hotelMapper.toEntity(request)).thenReturn(mapped);
        when(hotelRepository.save(mapped)).thenReturn(saved);
        when(hotelMapper.toResponse(saved)).thenReturn(response(saved));

        HotelResponse result = hotelService.create(request);

        ArgumentCaptor<Hotel> captor = ArgumentCaptor.forClass(Hotel.class);
        verify(hotelRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(mapped);
        assertThat(result.id()).isEqualTo(10L);
    }

    @Test
    void create_duplicateNameInCity_throwsConflictAndDoesNotSave() {
        HotelRequest request = request("Grand", "Kyiv");
        when(hotelRepository.existsByNameIgnoreCaseAndCityIgnoreCase("Grand", "Kyiv")).thenReturn(true);

        assertThatThrownBy(() -> hotelService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Hotel 'Grand' already exists in Kyiv");
        verify(hotelRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void update_renameToFreeName_updatesEntity() {
        Hotel hotel = hotel(1L, "Old", "Kyiv");
        HotelRequest request = request("New", "Kyiv");
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(hotelRepository.existsByNameIgnoreCaseAndCityIgnoreCase("New", "Kyiv")).thenReturn(false);
        when(hotelMapper.toResponse(hotel)).thenReturn(response(hotel(1L, "New", "Kyiv")));

        HotelResponse result = hotelService.update(1L, request);

        verify(hotelMapper).updateEntity(request, hotel);
        assertThat(result.name()).isEqualTo("New");
    }

    @Test
    void update_sameNameDifferentCase_skipsDuplicateCheck() {
        Hotel hotel = hotel(1L, "Grand", "Kyiv");
        HotelRequest request = request("GRAND", "kyiv");
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response(hotel));

        hotelService.update(1L, request);

        verify(hotelRepository, never()).existsByNameIgnoreCaseAndCityIgnoreCase(anyString(), anyString());
        verify(hotelMapper).updateEntity(request, hotel);
    }

    @Test
    void update_moveToCityWhereNameIsTaken_throwsConflictAndDoesNotModify() {
        Hotel hotel = hotel(1L, "Grand", "Kyiv");
        HotelRequest request = request("Grand", "Lviv");
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(hotelRepository.existsByNameIgnoreCaseAndCityIgnoreCase("Grand", "Lviv")).thenReturn(true);

        assertThatThrownBy(() -> hotelService.update(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Hotel 'Grand' already exists in Lviv");
        verify(hotelMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_missingHotel_throwsNotFound() {
        when(hotelRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.update(2L, request("X", "Y")))
                .isInstanceOf(NotFoundException.class);
        verify(hotelMapper, never()).updateEntity(any(), any());
    }

    // ---------- delete ----------

    @Test
    void delete_existingHotel_deletesThatEntity() {
        Hotel hotel = hotel(1L, "Grand", "Kyiv");
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));

        hotelService.delete(1L);

        verify(hotelRepository).delete(hotel);
    }

    @Test
    void delete_missingHotel_throwsNotFoundAndDeletesNothing() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.delete(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Hotel with id 1 not found");
        verify(hotelRepository, never()).delete(any());
    }
}
