package com.itsm.web.controller;

import com.itsm.service.NotificationService;
import com.itsm.web.dto.CommonDtos.MessageResponse;
import com.itsm.web.dto.CommonDtos.PageResponse;
import com.itsm.web.dto.IncidentDtos.NotificationDto;
import com.itsm.web.dto.IncidentDtos.UnreadCount;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Current user's in-app notification inbox")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationDto> inbox(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return notificationService.inbox(page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread() {
        return notificationService.unread();
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    public MessageResponse markAllRead() {
        return new MessageResponse(notificationService.markAllRead() + " notification(s) marked as read");
    }
}
