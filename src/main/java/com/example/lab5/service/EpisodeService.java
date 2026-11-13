package com.example.lab5.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab5.exception.BadRequestException;
import com.example.lab5.exception.ConflictException;
import com.example.lab5.exception.NotFoundException;
import com.example.lab5.model.Episode;
import com.example.lab5.model.Podcast;
import com.example.lab5.model.PublicationSlot;
import com.example.lab5.repository.AudioFileRepository;
import com.example.lab5.repository.EpisodeRepository;
import com.example.lab5.repository.PublicationSlotRepository;
import com.example.lab5.security.AccessPolicy;

@Service
@Transactional
public class EpisodeService {

    public record PublishResult(Episode episode, PublicationSlot slot) {}

    private final EpisodeRepository episodes;
    private final AudioFileRepository audioFiles;
    private final PublicationSlotRepository slots;
    private final EntityLocks locks;
    private final Clock clock;
    private final AccessPolicy access;

    public EpisodeService(EpisodeRepository episodes, AudioFileRepository audioFiles,
                          PublicationSlotRepository slots, EntityLocks locks, Clock clock,
                          AccessPolicy access) {
        this.episodes = episodes;
        this.audioFiles = audioFiles;
        this.slots = slots;
        this.locks = locks;
        this.clock = clock;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Episode> findAll() {
        return episodes.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Episode findById(Long id) {
        return episodes.findById(id)
                .orElseThrow(() -> new NotFoundException("Episode " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Episode> findAllVisible() {
        return episodes.findAll(Sort.by("id")).stream().filter(access::canView).toList();
    }

    @Transactional(readOnly = true)
    public Episode findVisibleById(Long id) {
        Episode episode = findById(id);
        if (!access.canView(episode)) {
            throw new NotFoundException("Episode " + id + " not found");
        }
        return episode;
    }

    public Episode create(Long podcastId, String title, int durationSeconds) {
        Podcast podcast = locks.lockPodcastShared(podcastId)
                .orElseThrow(() -> new BadRequestException("Podcast " + podcastId + " does not exist"));
        if (podcast.getStatus() != Podcast.Status.ACTIVE) {
            throw new ConflictException("Podcast " + podcastId + " is archived");
        }
        Episode episode = new Episode();
        episode.setPodcast(podcast);
        episode.setTitle(title);
        episode.setDurationSeconds(durationSeconds);
        episode.setState(Episode.State.DRAFT);
        episode.setCreatedAt(Instant.now(clock));
        return episodes.save(episode);
    }

    public Episode update(Long id, String title, Integer durationSeconds) {
        Episode episode = lock(id);
        if (title != null) episode.setTitle(title);
        if (durationSeconds != null) episode.setDurationSeconds(durationSeconds);
        return episode;
    }

    public void delete(Long id) {
        episodes.delete(lock(id));
    }

    public PublishResult publishNow(Long id) {
        Episode episode = lock(id);
        if (episode.getPodcast().getStatus() != Podcast.Status.ACTIVE) {
            throw new ConflictException("Podcast of episode " + id + " is archived");
        }
        if (episode.getState() == Episode.State.PUBLISHED) {
            throw new ConflictException("Episode " + id + " is already published");
        }
        if (!audioFiles.existsByEpisodeIdAndMainTrue(id)) {
            throw new ConflictException("Episode " + id + " has no main audio file");
        }
        Instant now = Instant.now(clock);
        PublicationSlot slot;
        if (episode.getState() == Episode.State.SCHEDULED) {
            slot = slots.findByEpisodeIdAndStatus(id, PublicationSlot.Status.PLANNED)
                    .orElseThrow(() -> new ConflictException("Scheduled episode " + id + " has no planned slot"));
        } else {
            slot = new PublicationSlot();
            slot.setPodcast(episode.getPodcast());
            slot.setEpisode(episode);
            slot.setScheduledAt(now);
            slot.setCreatedAt(now);
        }
        slot.setStatus(PublicationSlot.Status.PUBLISHED);
        slot.setPublishedAt(now);
        slot = slots.save(slot);
        episode.setState(Episode.State.PUBLISHED);
        return new PublishResult(episode, slot);
    }

    private Episode lock(Long id) {
        return locks.lockEpisode(id)
                .orElseThrow(() -> new NotFoundException("Episode " + id + " not found"));
    }
}
