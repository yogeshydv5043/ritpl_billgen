package com.ritpl.billgen.web;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Error pages that are not handled by the servlet container, e.g. a URL the role may not open. */
@Controller
public class WebErrorController {
    @GetMapping("/error/403")
    public String forbidden() { return "error/403"; }

    @GetMapping("/error/404")
    public String notFound() { return "error/404"; }
}