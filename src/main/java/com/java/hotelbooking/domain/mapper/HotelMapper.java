package com.java.hotelbooking.domain.mapper;

import com.java.hotelbooking.domain.dto.HotelRequest;
import com.java.hotelbooking.domain.dto.HotelResponse;
import com.java.hotelbooking.domain.model.Hotel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HotelMapper {

    HotelResponse toResponse(Hotel hotel);

    @Mapping(target = "id", ignore = true)
    Hotel toEntity(HotelRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(HotelRequest request, @MappingTarget Hotel hotel);
}
