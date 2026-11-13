package com.example.lab5.dto;

import java.time.Instant;
import com.example.lab5.model.Podcast;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PodcastDto {
    private PodcastDto() {}

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            Long authorId) {}

    public record UpdateRequest(
            @Size(min = 1, max = 200) String title,
            Long authorId) {}

    public record Response(Long id, String title, Long authorId, Podcast.Status status, Instant createdAt) {}

    public record ArchiveResponse(Response podcast, int cancelledSlots, int episodesReturnedToDraft) {}
}
