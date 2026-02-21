package com.codeops.courier.dto;

import com.codeops.courier.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PageResponse covering conversion from Spring Page.
 */
class PageResponseTest {

    @Test
    void from_mapsSpringPageCorrectly() {
        List<String> items = List.of("a", "b", "c");
        Page<String> springPage = new PageImpl<>(items, PageRequest.of(0, 10), 3);

        PageResponse<String> response = PageResponse.from(springPage);

        assertThat(response.content()).hasSize(3);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    void from_handlesEmptyPage() {
        Page<String> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PageResponse<String> response = PageResponse.from(emptyPage);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0);
        assertThat(response.totalPages()).isEqualTo(0);
        assertThat(response.isLast()).isTrue();
    }
}
