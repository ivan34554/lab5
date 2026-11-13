package com.example.lab5.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.lab5.dto.EpisodeDto;
import com.example.lab5.dto.PublicationSlotDto;
import com.example.lab5.model.Episode;
import com.example.lab5.model.PublicationSlot;
import com.example.lab5.service.EpisodeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/episodes")
public class EpisodeController {

    private final EpisodeService service;

    public EpisodeController(EpisodeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<EpisodeDto.Response> getAll() {
        return service.findAllVisible().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public EpisodeDto.Response getById(@PathVariable Long id) {
        return toResponse(service.findVisibleById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.canManagePodcast(#request.podcastId())")
    public EpisodeDto.Response create(@Valid @RequestBody EpisodeDto.CreateRequest request) {
        return toResponse(service.create(request.podcastId(), request.title(), request.durationSeconds()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canManageEpisode(#id)")
    public EpisodeDto.Response update(@PathVariable Long id, @Valid @RequestBody EpisodeDto.UpdateRequest request) {
        return toResponse(service.update(id, request.title(), request.durationSeconds()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.canManageEpisode(#id)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/publish-now")
    @PreAuthorize("@access.canManageEpisode(#id)")
    public EpisodeDto.PublishResponse publishNow(@PathVariable Long id) {
        EpisodeService.PublishResult result = service.publishNow(id);
        return new EpisodeDto.PublishResponse(toResponse(result.episode()), toResponse(result.slot()));
    }

    private EpisodeDto.Response toResponse(Episode e) {
        return new EpisodeDto.Response(e.getId(), e.getPodcast().getId(), e.getTitle(),
                e.getDurationSeconds(), e.getState(), e.getCreatedAt());
    }

    private PublicationSlotDto.Response toResponse(PublicationSlot s) {
        return new PublicationSlotDto.Response(s.getId(), s.getPodcast().getId(), s.getEpisode().getId(),
                s.getScheduledAt(), s.getStatus(), s.getPublishedAt(), s.getCreatedAt());
    }
}
