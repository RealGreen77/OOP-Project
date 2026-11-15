package com.itsm.service;

import com.itsm.domain.notification.Notification;
import com.itsm.domain.notification.NotificationChannelType;
import com.itsm.exception.ResourceNotFoundException;
import com.itsm.repository.NotificationRepository;
import com.itsm.security.CurrentUserProvider;
import com.itsm.web.dto.CommonDtos.PageResponse;
import com.itsm.web.dto.DtoMapper;
import com.itsm.web.dto.IncidentDtos.NotificationDto;
import com.itsm.web.dto.IncidentDtos.UnreadCount;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** The current user's in-app inbox, plus the administrator's delivery log for all channels. */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final CurrentUserProvider currentUser;
    private final DtoMapper mapper;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, CurrentUserProvider currentUser, DtoMapper mapper,
                               Clock clock) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> inbox(int page, int size) {
        Long userId = currentUser.currentUserId();
        return PageResponse.of(repository.findByRecipientIdAndChannelOrderByCreatedAtDesc(userId,
                NotificationChannelType.IN_APP, PageRequest.of(page, Math.min(size, 100))), mapper::notification);
    }

    @Transactional(readOnly = true)
    public UnreadCount unread() {
        return new UnreadCount(repository.countByRecipientIdAndChannelAndReadFalse(currentUser.currentUserId(),
                NotificationChannelType.IN_APP));
    }

    @Transactional
    public NotificationDto markRead(Long id) {
        Notification n = repository.findByIdAndRecipientId(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.markRead(clock.instant());
        return mapper.notification(n);
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentUser.currentUserId(), NotificationChannelType.IN_APP, clock.instant());
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> deliveryLog(int page, int size) {
        return PageResponse.of(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200))),
                mapper::notification);
    }
}
