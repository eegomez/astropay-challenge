package com.wallet.activityfeed.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result wrapper for cursor-based pagination
 * Contains items and optional cursor for next page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorResult<T> {
    private List<T> items;
    private String nextCursor; // Null if no more results
}
