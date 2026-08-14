package com.example.recordshop.web.customer;

import com.example.recordshop.domain.customer.CustomerRegistrationService;
import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.shared.InvariantViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Instant;

/** 会員登録画面(GET/POST /register)。 */
@Controller
public class RegistrationController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final CustomerRegistrationService customerRegistrationService;

    public RegistrationController(CustomerRegistrationService customerRegistrationService) {
        this.customerRegistrationService = customerRegistrationService;
    }

    @GetMapping("/register")
    public String showForm(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterForm form, Model model) {
        if (form.getDisplayName() == null || form.getDisplayName().isBlank()) {
            model.addAttribute("errorMessage", "表示名を入力してください");
            return "register";
        }
        if (form.getPassword() == null || form.getPassword().length() < MIN_PASSWORD_LENGTH) {
            model.addAttribute("errorMessage", "パスワードは%d文字以上で入力してください".formatted(MIN_PASSWORD_LENGTH));
            return "register";
        }

        try {
            Email email = new Email(form.getEmail() == null ? "" : form.getEmail());
            customerRegistrationService.register(email, form.getPassword(), form.getDisplayName(), Instant.now());
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", "メールアドレスの形式が正しくありません");
            return "register";
        } catch (InvariantViolationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "register";
        }

        return "redirect:/login?registered";
    }
}
