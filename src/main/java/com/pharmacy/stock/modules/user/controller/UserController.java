package com.pharmacy.stock.modules.user.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.entity.service.StructureEntityService;
import com.pharmacy.stock.modules.user.dto.UserDto;
import com.pharmacy.stock.modules.user.dto.UserFormDto;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import com.pharmacy.stock.modules.user.service.RoleService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gestion des utilisateurs — routes : /users (réservé au rôle ADMIN)
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RoleService roleService;
    private final StructureEntityService structureEntityService;
    private final AuditService auditService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "pages/users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("userForm", new UserFormDto());
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("entities", structureEntityService.findAll());
        return "pages/users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserFormDto form,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("roles", roleService.findAll());
            model.addAttribute("entities", structureEntityService.findAll());
            return "pages/users/form";
        }
        try {
            UserDto created = userService.createUser(form);
            auditService.record(currentUser.getId(), "CREATION", "USERS", created.getId(),
                    "Création de l'utilisateur " + created.getUsername(), request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message", "Utilisateur " + created.getUsername() + " créé avec succès.");
            return "redirect:/users";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roles", roleService.findAll());
            model.addAttribute("entities", structureEntityService.findAll());
            return "pages/users/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        UserDto user = userService.findById(id)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + id));
        UserFormDto form = new UserFormDto();
        form.setUsername(user.getUsername());
        form.setFirstName(user.getFirstName());
        form.setLastName(user.getLastName());
        form.setEmail(user.getEmail());
        form.setRoleId(user.getRoleId());
        form.setEntityId(user.getEntityId());
        form.setIsActive(user.getIsActive());
        model.addAttribute("userForm", form);
        model.addAttribute("userId", id);
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("entities", structureEntityService.findAll());
        return "pages/users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserFormDto form,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("userId", id);
            model.addAttribute("roles", roleService.findAll());
            model.addAttribute("entities", structureEntityService.findAll());
            return "pages/users/form";
        }
        try {
            UserDto updated = userService.updateUser(id, form);
            auditService.record(currentUser.getId(), "MODIFICATION", "USERS", id,
                    "Modification de l'utilisateur " + updated.getUsername(), request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message", "Utilisateur mis à jour avec succès.");
            return "redirect:/users";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("userId", id);
            model.addAttribute("roles", roleService.findAll());
            model.addAttribute("entities", structureEntityService.findAll());
            return "pages/users/form";
        }
    }

    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id,
                               @AuthenticationPrincipal CustomUserDetails currentUser,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        UserDto updated = userService.toggleActive(id);
        auditService.record(currentUser.getId(), updated.getIsActive() ? "ACTIVATION" : "DESACTIVATION",
                "USERS", id,
                (updated.getIsActive() ? "Activation" : "Désactivation") + " de l'utilisateur " + updated.getUsername(),
                request.getRemoteAddr());
        redirectAttributes.addFlashAttribute("message",
                "Compte " + (updated.getIsActive() ? "activé" : "désactivé") + " pour " + updated.getUsername() + ".");
        return "redirect:/users";
    }
}
