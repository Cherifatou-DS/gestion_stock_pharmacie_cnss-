package com.pharmacy.stock.modules.user.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.common.util.ClientIpResolver;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.user.dto.ChangePasswordFormDto;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import com.pharmacy.stock.modules.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Changement de mot de passe par l'utilisateur — routes : /change-password
 *
 * Route accessible meme lorsque le changement de mot de passe est force
 * (liste blanche de ForcePasswordChangeFilter). Apres modification, la
 * session est invalidee : la reconnexion est demandee.
 */
@Controller
@RequestMapping("/change-password")
@RequiredArgsConstructor
public class ChangePasswordController {

    private final UserService userService;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("changePasswordForm", new ChangePasswordFormDto());
        return "pages/auth/change-password";
    }

    @PostMapping
    public String change(@Valid @ModelAttribute("changePasswordForm") ChangePasswordFormDto form,
                         BindingResult result,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         Model model) {
        if (result.hasErrors()) {
            return "pages/auth/change-password";
        }
        if (!form.getNewPassword().equals(form.getConfirmNewPassword())) {
            result.rejectValue("confirmNewPassword", "mismatch",
                    "La confirmation ne correspond pas au nouveau mot de passe.");
            return "pages/auth/change-password";
        }
        try {
            userService.changePassword(currentUser.getId(), form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "pages/auth/change-password";
        }
        auditService.record(currentUser.getId(), "CHANGEMENT_MOT_DE_PASSE", "AUTH", currentUser.getId(),
                "Changement de mot de passe de l'utilisateur " + currentUser.getUsername(),
                clientIpResolver.resolve(request));
        // Nouveau mot de passe pris en compte : nouvelle authentification requise.
        request.getSession().invalidate();
        return "redirect:/login?passwordChanged";
    }
}
