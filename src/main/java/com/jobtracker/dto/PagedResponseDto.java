package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable page envelope.
 *
 * <p>Deliberately hand-rolled rather than returning Spring Data's {@code Page} directly. Serialising
 * {@code PageImpl} emits a large, version-dependent structure (Spring Data explicitly warns against
 * relying on it), and it leaks internals like {@code pageable.sort.unsorted} that no client needs.
 * This exposes exactly the five fields the frontend asked for, plus first/last for convenience.
 */
@Getter
@Setter
public class PagedResponseDto<T> {

    private List<T> content;
    /** Zero-based, matching the `page` query parameter. */
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <E, D> PagedResponseDto<D> from(Page<E> page, Function<E, D> mapper) {
        PagedResponseDto<D> dto = new PagedResponseDto<>();
        dto.setContent(page.getContent().stream().map(mapper).toList());
        dto.setNumber(page.getNumber());
        dto.setSize(page.getSize());
        dto.setTotalElements(page.getTotalElements());
        dto.setTotalPages(page.getTotalPages());
        dto.setFirst(page.isFirst());
        dto.setLast(page.isLast());
        return dto;
    }
}
