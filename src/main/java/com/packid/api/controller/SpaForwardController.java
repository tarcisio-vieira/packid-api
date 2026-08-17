package com.packid.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/colaborador", "/user"})
    public String forwardPackidRoutes() {
        return "forward:/index.html";
    }
}
