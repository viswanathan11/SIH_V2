package com.vidyasetu.realtime;

import com.vidyasetu.common.Json;
import com.vidyasetu.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class EventBusService {
    private static final Logger log = LoggerFactory.getLogger(EventBusService.class);

    public record DomainEvent(
            String id,
            String type,
            String ts,
            UUID institutionId,
            String institutionCode,
            String severity,
            String message,
            Map<String, Object> payload
    ) {}

    private record Subscriber(SseEmitter emitter, UserPrincipal user) {}

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final List<DomainEvent> recentEvents = new CopyOnWriteArrayList<>();
    private static final int MAX_RECENT = 100;

    public SseEmitter subscribe(UserPrincipal user) {
        SseEmitter emitter = new SseEmitter(0L); // Infinite timeout, kept alive by heartbeat
        Subscriber sub = new Subscriber(emitter, user);
        subscribers.add(sub);

        emitter.onCompletion(() -> subscribers.remove(sub));
        emitter.onTimeout(() -> subscribers.remove(sub));
        emitter.onError(e -> subscribers.remove(sub));

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("message", "Connected to VidyaSetu real-time stream", "ts", Instant.now().toString())));
        } catch (IOException e) {
            subscribers.remove(sub);
        }

        return emitter;
    }

    public void publish(String type, UUID institutionId, String institutionCode, String severity,
                        String message, Map<String, Object> payload) {
        DomainEvent event = new DomainEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now().toString(),
                institutionId,
                institutionCode,
                severity,
                message,
                payload != null ? payload : Collections.emptyMap()
        );

        recentEvents.add(0, event);
        if (recentEvents.size() > MAX_RECENT) {
            recentEvents.remove(recentEvents.size() - 1);
        }

        for (Subscriber sub : subscribers) {
            // Scope filtering: National roles see all; Institution roles see only their institution
            if (sub.user() == null || sub.user().isNationalAdmin() ||
                    (event.institutionId() == null || (sub.user().getInstitutionId() != null && sub.user().getInstitutionId().equals(event.institutionId())))) {
                try {
                    sub.emitter().send(SseEmitter.event()
                            .id(event.id())
                            .name(event.type())
                            .data(event));
                } catch (Exception e) {
                    subscribers.remove(sub);
                }
            }
        }
    }

    public List<DomainEvent> getRecentEvents(UserPrincipal user) {
        List<DomainEvent> list = new ArrayList<>();
        for (DomainEvent ev : recentEvents) {
            if (user == null || user.isNationalAdmin() ||
                    (ev.institutionId() == null || (user.getInstitutionId() != null && user.getInstitutionId().equals(ev.institutionId())))) {
                list.add(ev);
            }
        }
        return list;
    }

    @Scheduled(fixedRate = 20000)
    public void sendHeartbeat() {
        for (Subscriber sub : subscribers) {
            try {
                sub.emitter().send(SseEmitter.event().name("HEARTBEAT").data(Map.of("ts", Instant.now().toString())));
            } catch (Exception e) {
                subscribers.remove(sub);
            }
        }
    }
}
