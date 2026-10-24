package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.HotelRequest;
import com.java.hotelbooking.domain.dto.HotelResponse;
import com.java.hotelbooking.domain.dto.RoomRequest;
import com.java.hotelbooking.domain.dto.RoomResponse;
import com.java.hotelbooking.domain.enums.RoomType;
import com.java.hotelbooking.service.HotelService;
import com.java.hotelbooking.service.RoomService;
import com.java.hotelbooking.web.annotation.PostCreated;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @PostCreated} must behave exactly like {@code @PostMapping} + {@code @ResponseStatus(CREATED)}.
 */
@WebMvcTest(
        controllers = HotelController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(PostCreatedWebMvcTest.EquivalenceController.class)
class PostCreatedWebMvcTest {

    /** The same endpoint written twice: with the original annotations and with the composed one. */
    @RestController
    @RequestMapping("/test/equivalence")
    static class EquivalenceController {

        @PostMapping("/original")
        @ResponseStatus(HttpStatus.CREATED)
        public String original() {
            return "created";
        }

        @PostCreated("/composed")
        public String composed() {
            return "created";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelService hotelService;

    @MockitoBean
    private RoomService roomService;

    @Test
    void composedAnnotation_mergesToSameMappingAndStatusAsOriginalPair() throws Exception {
        Method original = EquivalenceController.class.getMethod("original");
        Method composed = EquivalenceController.class.getMethod("composed");

        RequestMapping originalMapping = AnnotatedElementUtils.findMergedAnnotation(original, RequestMapping.class);
        RequestMapping composedMapping = AnnotatedElementUtils.findMergedAnnotation(composed, RequestMapping.class);
        ResponseStatus originalStatus = AnnotatedElementUtils.findMergedAnnotation(original, ResponseStatus.class);
        ResponseStatus composedStatus = AnnotatedElementUtils.findMergedAnnotation(composed, ResponseStatus.class);

        assertThat(composedMapping).isNotNull();
        assertThat(originalMapping).isNotNull();
        assertThat(composedMapping.method()).containsExactly(RequestMethod.POST).isEqualTo(originalMapping.method());
        assertThat(composedMapping.path()).containsExactly("/composed");
        assertThat(composedStatus).isNotNull();
        assertThat(originalStatus).isNotNull();
        assertThat(composedStatus.code()).isEqualTo(HttpStatus.CREATED).isEqualTo(originalStatus.code());
    }

    @Test
    void originalAndComposed_bothAnswerPostWith201() throws Exception {
        mockMvc.perform(post("/test/equivalence/original"))
                .andExpect(status().isCreated())
                .andExpect(content().string("created"));
        mockMvc.perform(post("/test/equivalence/composed"))
                .andExpect(status().isCreated())
                .andExpect(content().string("created"));
    }

    @Test
    void originalAndComposed_bothRejectGetWith405() throws Exception {
        mockMvc.perform(get("/test/equivalence/original")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/test/equivalence/composed")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void createHotel_withPostCreated_returns201AndBody() throws Exception {
        when(hotelService.create(any(HotelRequest.class)))
                .thenReturn(new HotelResponse(5L, "Grand", "Kyiv", "Main 1", 4, null));

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grand","city":"Kyiv","address":"Main 1","stars":4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Grand"));
    }

    @Test
    void createRoom_withPostCreatedPath_routesHotelIdAndReturns201() throws Exception {
        when(roomService.create(eq(3L), any(RoomRequest.class)))
                .thenReturn(new RoomResponse(10L, 3L, "Grand", "101", RoomType.DOUBLE, 2, new BigDecimal("80.00")));

        mockMvc.perform(post("/api/hotels/3/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","type":"DOUBLE","capacity":2,"pricePerNight":80.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(3));

        verify(roomService).create(eq(3L), any(RoomRequest.class));
    }

    @Test
    void createHotel_invalidBody_stillValidatedAndReturns400() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","city":"Kyiv","address":"Main 1","stars":9}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.stars").exists());
    }
}
