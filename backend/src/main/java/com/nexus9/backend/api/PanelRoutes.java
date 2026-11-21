package com.nexus9.backend.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets the browser open the panel's own URLs directly; the panel reads the path itself. */
@Controller
class PanelRoutes {

    @GetMapping("/simulations/{id:[0-9a-fA-F-]{36}}")
    String simulation() {
        return "forward:/index.html";
    }
}
