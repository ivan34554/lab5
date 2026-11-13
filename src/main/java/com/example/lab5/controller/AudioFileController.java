package com.example.lab5.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.lab5.dto.AudioFileDto;
import com.example.lab5.model.AudioFile;
import com.example.lab5.service.AudioFileService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/audio-files")
public class AudioFileController {

    private final AudioFileService service;

    public AudioFileController(AudioFileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AudioFileDto.Response> getAll() {
        return service.findAllVisible().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public AudioFileDto.Response getById(@PathVariable Long id) {
        return toResponse(service.findVisibleById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.canManageEpisode(#request.episodeId())")
    public AudioFileDto.Response create(@Valid @RequestBody AudioFileDto.CreateRequest request) {
        return toResponse(service.create(request.episodeId(), request.path(), request.format(),
                request.sizeBytes(), Boolean.TRUE.equals(request.main())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canManageAudioFile(#id)")
    public AudioFileDto.Response update(@PathVariable Long id, @Valid @RequestBody AudioFileDto.UpdateRequest request) {
        return toResponse(service.update(id, request.path(), request.format(), request.sizeBytes()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.canManageAudioFile(#id)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/make-main")
    @PreAuthorize("@access.canManageAudioFile(#id)")
    public AudioFileDto.Response makeMain(@PathVariable Long id) {
        return toResponse(service.makeMain(id));
    }

    private AudioFileDto.Response toResponse(AudioFile a) {
        return new AudioFileDto.Response(a.getId(), a.getEpisode().getId(), a.getPath(), a.getFormat(),
                a.getSizeBytes(), a.isMain(), a.getCreatedAt());
    }
}
