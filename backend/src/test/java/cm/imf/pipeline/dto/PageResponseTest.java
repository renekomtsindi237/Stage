package cm.imf.pipeline.dto;

import cm.imf.pipeline.dto.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires PageResponse — pagination standardisée IMF Pipeline.
 */
@DisplayName("PageResponse — pagination standardisée")
class PageResponseTest {

    // ── PageResponse.from(Page<E>, mapper) ────────────────────────────────────

    @Nested
    @DisplayName("from(Page<E>, mapper)")
    class FromSpringPage {

        @Test
        @DisplayName("→ mappe le contenu, total et flags first/last")
        void mappe_correctement() {
            var springPage = new PageImpl<>(
                    List.of("alpha", "beta"),
                    PageRequest.of(0, 20),
                    2L);

            PageResponse<String> resp = PageResponse.from(springPage, String::toUpperCase);

            assertThat(resp.content()).containsExactly("ALPHA", "BETA");
            assertThat(resp.page()).isZero();
            assertThat(resp.size()).isEqualTo(20);
            assertThat(resp.totalElements()).isEqualTo(2L);
            assertThat(resp.totalPages()).isEqualTo(1);
            assertThat(resp.first()).isTrue();
            assertThat(resp.last()).isTrue();
        }

        @Test
        @DisplayName("Page 2/3 → first=false, last=false")
        void page_milieu_first_false_last_false() {
            var springPage = new PageImpl<>(
                    List.of("item"),
                    PageRequest.of(1, 1),
                    3L);

            PageResponse<String> resp = PageResponse.from(springPage, s -> s);

            assertThat(resp.first()).isFalse();
            assertThat(resp.last()).isFalse();
            assertThat(resp.page()).isEqualTo(1);
            assertThat(resp.totalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("Dernière page → last=true")
        void derniere_page_last_true() {
            var springPage = new PageImpl<>(
                    List.of("z"),
                    PageRequest.of(2, 1),
                    3L);

            PageResponse<String> resp = PageResponse.from(springPage, s -> s);

            assertThat(resp.last()).isTrue();
            assertThat(resp.first()).isFalse();
        }

        @Test
        @DisplayName("Page vide → contenu vide, totalElements=0")
        void page_vide() {
            var springPage = new PageImpl<String>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0L);

            PageResponse<String> resp = PageResponse.from(springPage, s -> s);

            assertThat(resp.content()).isEmpty();
            assertThat(resp.totalElements()).isZero();
            assertThat(resp.first()).isTrue();
            assertThat(resp.last()).isTrue();
        }
    }

    // ── PageResponse.of(List, page, size, total) ──────────────────────────────

    @Nested
    @DisplayName("of(List, page, size, total)")
    class OfListRaw {

        @Test
        @DisplayName("→ calcule totalPages correctement")
        void totalPages_calcule() {
            PageResponse<String> resp =
                    PageResponse.of(List.of("a", "b", "c"), 0, 3, 9L);

            assertThat(resp.totalPages()).isEqualTo(3);
            assertThat(resp.first()).isTrue();
            assertThat(resp.last()).isFalse();
        }

        @ParameterizedTest(name = "page={0}, size={1}, total={2} → totalPages={3}, first={4}, last={5}")
        @CsvSource({
                "0, 20, 5,  1, true,  true",
                "0, 20, 20, 1, true,  true",
                "0, 20, 21, 2, true,  false",
                "1, 20, 40, 2, false, true",
                "0, 10, 0,  1, true,  true"
        })
        @DisplayName("Permutations first/last/totalPages")
        void permutations(int page, int size, long total,
                          int expectedPages, boolean expectedFirst, boolean expectedLast) {
            PageResponse<String> resp = PageResponse.of(List.of(), page, size, total);

            assertThat(resp.totalPages()).isEqualTo(expectedPages);
            assertThat(resp.first()).isEqualTo(expectedFirst);
            assertThat(resp.last()).isEqualTo(expectedLast);
        }

        @Test
        @DisplayName("size=0 → totalPages=1 (protection division par zéro)")
        void size_zero_evite_division_par_zero() {
            assertThatCode(() -> PageResponse.of(List.of(), 0, 0, 100L))
                    .doesNotThrowAnyException();
            assertThat(PageResponse.of(List.of(), 0, 0, 100L).totalPages()).isEqualTo(1);
        }
    }
}
