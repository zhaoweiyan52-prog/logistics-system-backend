package com.example.logistics.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {
    @GetMapping("/")
    public String index() { return "redirect:/logistics-admin.html"; }
}
