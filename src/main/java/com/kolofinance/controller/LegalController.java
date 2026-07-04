package com.kolofinance.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

    @GetMapping({"/privacy", "/privacy/"})
    public String privacy() {
        return "forward:/privacy.html";
    }

    @GetMapping({"/data-deletion", "/data-deletion/"})
    public String dataDeletion() {
        return "forward:/data-deletion.html";
    }
}
