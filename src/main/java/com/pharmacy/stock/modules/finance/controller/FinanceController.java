package com.pharmacy.stock.modules.finance.controller;

import com.pharmacy.stock.modules.finance.dto.FinanceSummaryDto;
import com.pharmacy.stock.modules.finance.dto.FinancialTransactionDto;
import com.pharmacy.stock.modules.finance.entity.FinancialTransactionType;
import com.pharmacy.stock.modules.finance.service.FinanceService;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;

/**
 * Suivi financier DBI : engagements fournisseurs et sorties valorisées — routes : /finance
 */
@Controller
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping
    public String index(@RequestParam(required = false) String type,
                        @AuthenticationPrincipal CustomUserDetails currentUser,
                        Model model) {
        List<FinancialTransactionDto> transactions = financeService.findTransactions(type);
        FinanceSummaryDto summary = financeService.summarize();
        model.addAttribute("transactions", transactions);
        model.addAttribute("summary", summary);
        model.addAttribute("types", Arrays.asList(FinancialTransactionType.values()));
        model.addAttribute("selectedType", type);
        return "pages/finance/index";
    }
}
