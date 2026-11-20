package com.bokdeongi.backend.search.service;

import com.bokdeongi.backend.benefit.hashtag.HashtagResponse;
import com.bokdeongi.backend.benefit.hashtag.HashtagService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class BenefitSearchSuggestionService {

    private final HashtagService hashtagService;

    public BenefitSearchSuggestionService(
            HashtagService hashtagService
    ) {
        this.hashtagService = hashtagService;
    }

    public List<String> suggest(String keyword) {
        String normalized = keyword.trim()
                .toLowerCase(Locale.ROOT);

        HashtagResponse response = hashtagService.hashtags();

        return response.tags().stream()
                .filter(tag -> tag.count() > 0)
                .filter(tag -> {
                    String name = tag.name()
                            .toLowerCase(Locale.ROOT);

                    return name.contains(normalized)
                            || normalized.contains(name);
                })
                .sorted(Comparator
                        .comparingLong(HashtagResponse.Tag::count)
                        .reversed())
                .limit(5)
                .map(HashtagResponse.Tag::name)
                .toList();
    }
}