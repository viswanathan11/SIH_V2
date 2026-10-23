package com.vidyasetu.realtime;

import com.vidyasetu.security.TenantScope;
import com.vidyasetu.security.UserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RealtimeController {
    private final EventBusService eventBusService;

    public RealtimeController(EventBusService eventBusService) {
        this.eventBusService = eventBusService;
    }

    @GetMapping(value = "/stream/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        UserPrincipal user = TenantScope.currentUser();
        return eventBusService.subscribe(user);
    }

    @GetMapping("/events/recent")
    public List<EventBusService.DomainEvent> getRecentEvents() {
        UserPrincipal user = TenantScope.currentUser();
        return eventBusService.getRecentEvents(user);
    }
}
