package com.example.lab5.security;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab5.model.AudioFile;
import com.example.lab5.model.Episode;
import com.example.lab5.model.Podcast;
import com.example.lab5.model.PublicationSlot;
import com.example.lab5.repository.AudioFileRepository;
import com.example.lab5.repository.EpisodeRepository;
import com.example.lab5.repository.PodcastRepository;
import com.example.lab5.repository.PublicationSlotRepository;

@Component("access")
@Transactional(readOnly = true)
public class AccessPolicy {

    private final PodcastRepository podcasts;
    private final EpisodeRepository episodes;
    private final AudioFileRepository audioFiles;
    private final PublicationSlotRepository slots;

    public AccessPolicy(PodcastRepository podcasts, EpisodeRepository episodes,
                        AudioFileRepository audioFiles, PublicationSlotRepository slots) {
        this.podcasts = podcasts;
        this.episodes = episodes;
        this.audioFiles = audioFiles;
        this.slots = slots;
    }

    public boolean isAdmin() {
        return hasAuthority(Authority.USER_MANAGE);
    }

    public boolean isStaff() {
        return hasAuthority(Authority.PODCAST_MODERATE);
    }

    public boolean canAccessUser(Long userId) {
        return isOwner(userId) || isAdmin();
    }

    public boolean canCreatePodcast(Long authorId) {
        return authorId == null || isOwner(authorId) || isAdmin();
    }

    public boolean canManagePodcast(Long podcastId) {
        return ownerOrAdmin(podcasts.findAuthorId(podcastId));
    }

    public boolean canUpdatePodcast(Long podcastId, Long newAuthorId) {
        Optional<Long> author = podcasts.findAuthorId(podcastId);
        if (!ownerOrAdmin(author)) {
            return false;
        }
        return newAuthorId == null || isAdmin() || author.map(newAuthorId::equals).orElse(true);
    }

    public boolean canModeratePodcast(Long podcastId) {
        return ownerOrStaff(podcasts.findAuthorId(podcastId));
    }

    public boolean canManageEpisode(Long episodeId) {
        return ownerOrAdmin(episodes.findAuthorId(episodeId));
    }

    public boolean canManageAudioFile(Long audioFileId) {
        return ownerOrAdmin(audioFiles.findAuthorId(audioFileId));
    }

    public boolean canManageSlot(Long slotId) {
        return ownerOrAdmin(slots.findAuthorId(slotId));
    }

    public boolean canCancelSlot(Long slotId) {
        return ownerOrStaff(slots.findAuthorId(slotId));
    }

    public boolean canView(Podcast podcast) {
        return podcast.getStatus() == Podcast.Status.ACTIVE || privileged(podcast);
    }

    public boolean canView(Episode episode) {
        Podcast podcast = episode.getPodcast();
        return privileged(podcast)
                || (episode.getState() == Episode.State.PUBLISHED && podcast.getStatus() == Podcast.Status.ACTIVE);
    }

    public boolean canView(AudioFile file) {
        return canView(file.getEpisode());
    }

    public boolean canView(PublicationSlot slot) {
        return privileged(slot.getPodcast());
    }

    private boolean privileged(Podcast podcast) {
        return isStaff() || isOwner(podcast.getAuthor().getId());
    }

    private boolean hasAuthority(String authority) {
        return CurrentUser.find().map(user -> user.hasAuthority(authority)).orElse(false);
    }

    private boolean isOwner(Long ownerId) {
        return CurrentUser.find().map(user -> user.getId().equals(ownerId)).orElse(false);
    }

    private boolean ownerOrAdmin(Optional<Long> authorId) {
        return authorId.map(id -> isAdmin() || isOwner(id)).orElseGet(this::isAdmin);
    }

    private boolean ownerOrStaff(Optional<Long> authorId) {
        return authorId.map(id -> isStaff() || isOwner(id)).orElseGet(this::isStaff);
    }
}
