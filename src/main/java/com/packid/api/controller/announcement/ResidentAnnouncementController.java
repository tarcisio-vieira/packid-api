package com.packid.api.controller.announcement;

import com.packid.api.controller.announcement.dto.AnnouncementResponse;
import com.packid.api.service.AnnouncementService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resident/announcements")
public class ResidentAnnouncementController {
    private final AnnouncementService service;
    public ResidentAnnouncementController(AnnouncementService service) { this.service = service; }

    @GetMapping
    public List<AnnouncementResponse> list(HttpSession session) {
        return service.residentList(session);
    }
}
