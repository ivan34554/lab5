package com.example.lab5.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.lab5.model.Podcast;
import jakarta.persistence.LockModeType;

public interface PodcastRepository extends JpaRepository<Podcast, Long> {

    @Query("select p.author.id from Podcast p where p.id = :id")
    Optional<Long> findAuthorId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Podcast p where p.id = :id")
    Optional<Podcast> lockExclusive(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Podcast p where p.id = :id")
    Optional<Podcast> lockShared(@Param("id") Long id);
}
