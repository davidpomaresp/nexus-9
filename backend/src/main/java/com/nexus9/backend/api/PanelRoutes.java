package com.nexus9.backend.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** So /simulations/{id} links work when opened directly. */
@Controller
class PanelRoutes {

    @GetMapping("/simulations/{id:[0-9a-fA-F-]{36}}")
    String simulation() {
        return "forward:/index.html";
    }
}
