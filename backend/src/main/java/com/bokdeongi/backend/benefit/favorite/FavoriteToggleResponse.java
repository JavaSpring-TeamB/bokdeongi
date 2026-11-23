package com.bokdeongi.backend.benefit.favorite;

public record FavoriteToggleResponse(
        long benefitId,
        boolean bookmarked
) {
}