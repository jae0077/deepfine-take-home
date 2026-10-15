package com.assignment.deepfine.dto;

import java.util.List;
import java.util.function.ToLongFunction;

public record CursorPage<T>(List<T> items, Long nextCursor) {
    public static <T> CursorPage<T> from(List<T> rows, int size, ToLongFunction<T> id) {
        // rows는 size + 1건까지 들어온다. 초과한 한 건은 다음 페이지 확인용이다.
        boolean hasNext = rows.size() > size;
        List<T> items = List.copyOf(rows.subList(0, Math.min(rows.size(), size)));
        // 다음 페이지가 있을 때만 이번 페이지의 마지막 ID를 커서로 전달한다.
        return new CursorPage<>(items, hasNext ? id.applyAsLong(items.getLast()) : null);
    }
}
