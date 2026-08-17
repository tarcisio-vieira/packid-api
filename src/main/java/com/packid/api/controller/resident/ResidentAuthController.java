package com.packid.api.controller.resident;

import com.packid.api.controller.resident.dto.ResidentLoginRequest;
import com.packid.api.controller.resident.dto.ResidentSessionResponse;
import com.packid.api.service.ResidentSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/resident-auth")
public class ResidentAuthController {
    private final ResidentSessionService residentSessionService;

    public ResidentAuthController(ResidentSessionService residentSessionService) {
        this.residentSessionService = residentSessionService;
    }

    @PostMapping("/login")
    public ResidentSessionResponse login(@Valid @RequestBody ResidentLoginRequest request, HttpServletRequest servletRequest) {
        return residentSessionService.login(request, servletRequest.getSession(true));
    }

    @GetMapping("/me")
    public ResidentSessionResponse me(HttpSession session) {
        return residentSessionService.current(session);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpSession session) {
        residentSessionService.logout(session);
        return Map.of("success", true);
    }
}
