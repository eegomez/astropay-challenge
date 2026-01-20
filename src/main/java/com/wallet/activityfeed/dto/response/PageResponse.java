package com.wallet.activityfeed.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cursor-based paginated response
 * - Use nextCursor to fetch the next page
 * - nextCursor is null when there are no more results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private String nextCursor; // Cursor for next page (null if no more results)
    private int size; // Number of items in current page
    private boolean hasMore; // True if more results available
}
