package com.example.lab5.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.lab5.dto.PublicationSlotDto;
import com.example.lab5.model.PublicationSlot;
import com.example.lab5.service.PublicationSlotService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/publication-slots")
public class PublicationSlotController {

    private final PublicationSlotService service;

    public PublicationSlotController(PublicationSlotService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PublicationSlotDto.Response> getAll() {
        return service.findAllVisible().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PublicationSlotDto.Response getById(@PathVariable Long id) {
        return toResponse(service.findVisibleById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.canManagePodcast(#request.podcastId())")
    public PublicationSlotDto.Response create(@Valid @RequestBody PublicationSlotDto.CreateRequest request) {
        return toResponse(service.create(request.podcastId(), request.episodeId(), request.scheduledAt()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canManageSlot(#id)")
    public PublicationSlotDto.Response update(@PathVariable Long id,
                                              @Valid @RequestBody PublicationSlotDto.UpdateRequest request) {
        return toResponse(service.update(id, request.scheduledAt()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.canManageSlot(#id)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@access.canManageSlot(#id)")
    public PublicationSlotDto.Response publish(@PathVariable Long id) {
        return toResponse(service.publish(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@access.canCancelSlot(#id)")
    public PublicationSlotDto.Response cancel(@PathVariable Long id) {
        return toResponse(service.cancel(id));
    }

    private PublicationSlotDto.Response toResponse(PublicationSlot s) {
        return new PublicationSlotDto.Response(s.getId(), s.getPodcast().getId(), s.getEpisode().getId(),
                s.getScheduledAt(), s.getStatus(), s.getPublishedAt(), s.getCreatedAt());
    }
}
