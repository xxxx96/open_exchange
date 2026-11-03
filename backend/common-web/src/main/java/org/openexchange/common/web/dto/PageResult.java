package org.openexchange.common.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private int page;
    private int size;
    private long total;
    private int totalPages;

    public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
        return PageResult.<T>builder()
                .items(items)
                .page(page)
                .size(size)
                .total(total)
                .totalPages((int) Math.ceil((double) total / size))
                .build();
    }
}
