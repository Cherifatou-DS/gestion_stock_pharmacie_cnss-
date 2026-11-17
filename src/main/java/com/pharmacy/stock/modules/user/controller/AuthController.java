package com.pharmacy.stock.modules.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Écran de connexion — route : /login
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(Model model) {
        return "pages/auth/login";
    }
}
