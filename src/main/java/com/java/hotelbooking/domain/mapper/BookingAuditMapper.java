package com.java.hotelbooking.domain.mapper;

import com.java.hotelbooking.domain.dto.BookingAuditResponse;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookingAuditMapper {

    BookingAuditResponse toResponse(BookingAuditEntry entry);
}
