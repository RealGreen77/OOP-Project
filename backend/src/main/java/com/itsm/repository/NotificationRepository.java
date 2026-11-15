package com.itsm.repository;

import com.itsm.domain.notification.Notification;
import com.itsm.domain.notification.NotificationChannelType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(Long recipientId, NotificationChannelType channel,
                                                                      Pageable pageable);

    long countByRecipientIdAndChannelAndReadFalse(Long recipientId, NotificationChannelType channel);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @EntityGraph(attributePaths = "recipient")
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Query("""
            update Notification n set n.read = true, n.readAt = :at
            where n.recipient.id = :recipientId and n.channel = :channel and n.read = false
            """)
    int markAllRead(@Param("recipientId") Long recipientId, @Param("channel") NotificationChannelType channel,
                    @Param("at") Instant at);
}
