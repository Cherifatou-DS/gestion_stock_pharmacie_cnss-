package com.pharmacy.stock.modules.product.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.common.util.ClientIpResolver;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.product.dto.ProductDto;
import com.pharmacy.stock.modules.product.dto.ProductFormDto;
import com.pharmacy.stock.modules.product.service.ProductService;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Catalogue des produits pharmaceutiques — routes : /products
 */
@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("products", productService.search(keyword));
        model.addAttribute("keyword", keyword);
        return "pages/products/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("productForm", new ProductFormDto());
        return "pages/products/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") ProductFormDto form,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "pages/products/form";
        }
        try {
            ProductDto created = productService.createProduct(form);
            auditService.record(currentUser.getId(), "CREATION", "PRODUCTS", created.getId(),
                    "Création du produit " + created.getCode() + " (" + created.getDesignation() + ")",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Produit " + created.getCode() + " ajouté au catalogue avec succès.");
            return "redirect:/products";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "pages/products/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ProductDto product = productService.findById(id)
                .orElseThrow(() -> new BusinessException("Produit introuvable : id=" + id));
        ProductFormDto form = new ProductFormDto();
        form.setCode(product.getCode());
        form.setDesignation(product.getDesignation());
        form.setUnit(product.getUnit());
        form.setUnitPrice(product.getUnitPrice());
        model.addAttribute("productForm", form);
        model.addAttribute("productId", id);
        return "pages/products/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("productForm") ProductFormDto form,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("productId", id);
            return "pages/products/form";
        }
        try {
            ProductDto updated = productService.updateProduct(id, form);
            auditService.record(currentUser.getId(), "MODIFICATION", "PRODUCTS", id,
                    "Modification du produit " + updated.getCode() + " (" + updated.getDesignation() + ")",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Produit " + updated.getCode() + " mis à jour avec succès.");
            return "redirect:/products";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("productId", id);
            return "pages/products/form";
        }
    }

    @GetMapping("/search")
    public String search(@RequestParam String keyword, Model model) {
        model.addAttribute("keyword", keyword);
        return "redirect:/products";
    }
}
