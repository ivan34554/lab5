package com.example.lab5.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.lab5.dto.PodcastDto;
import com.example.lab5.model.Podcast;
import com.example.lab5.security.CurrentUser;
import com.example.lab5.service.PodcastService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/podcasts")
public class PodcastController {

    private final PodcastService service;

    public PodcastController(PodcastService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PodcastDto.Response> getAll() {
        return service.findAllVisible().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PodcastDto.Response getById(@PathVariable Long id) {
        return toResponse(service.findVisibleById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.canCreatePodcast(#request.authorId())")
    public PodcastDto.Response create(@Valid @RequestBody PodcastDto.CreateRequest request) {
        Long authorId = request.authorId() != null ? request.authorId() : CurrentUser.require().getId();
        return toResponse(service.create(request.title(), authorId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canUpdatePodcast(#id, #request.authorId())")
    public PodcastDto.Response update(@PathVariable Long id, @Valid @RequestBody PodcastDto.UpdateRequest request) {
        return toResponse(service.update(id, request.title(), request.authorId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.canManagePodcast(#id)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@access.canModeratePodcast(#id)")
    public PodcastDto.ArchiveResponse archive(@PathVariable Long id) {
        PodcastService.ArchiveResult result = service.archive(id);
        return new PodcastDto.ArchiveResponse(toResponse(result.podcast()),
                result.cancelledSlots(), result.episodesReturnedToDraft());
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@access.canModeratePodcast(#id)")
    public PodcastDto.Response restore(@PathVariable Long id) {
        return toResponse(service.restore(id));
    }

    private PodcastDto.Response toResponse(Podcast p) {
        return new PodcastDto.Response(p.getId(), p.getTitle(), p.getAuthor().getId(), p.getStatus(), p.getCreatedAt());
    }
}
