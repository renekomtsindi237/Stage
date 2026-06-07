package cm.imf.pipeline.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Réponse paginée standardisée pour tous les endpoints de liste.
 *
 * Utilisation :
 *   PageResponse.from(springPage, mapper)   — depuis Spring Data Page<E>
 *   PageResponse.of(list, page, size, total) — depuis liste brute + count
 */
@Schema(description = "Résultat paginé")
public record PageResponse<T>(

    @Schema(description = "Éléments de la page courante")
    List<T> content,

    @Schema(description = "Numéro de la page (0-indexé)", example = "0")
    int page,

    @Schema(description = "Taille de la page demandée", example = "20")
    int size,

    @Schema(description = "Nombre total d'éléments toutes pages confondues", example = "154")
    long totalElements,

    @Schema(description = "Nombre total de pages", example = "8")
    int totalPages,

    @Schema(description = "Indique si c'est la première page")
    boolean first,

    @Schema(description = "Indique si c'est la dernière page")
    boolean last

) {
    /** Construit depuis un Spring Data {@link Page} avec transformation. */
    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /** Construit depuis une liste brute avec total connu (service non-Page). */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (size <= 0) ? 1 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                (page + 1) >= totalPages
        );
    }
}
