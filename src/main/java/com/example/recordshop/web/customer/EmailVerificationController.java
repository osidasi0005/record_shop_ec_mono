package com.example.recordshop.web.customer;

import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailVerificationService;
import com.example.recordshop.domain.shared.InvariantViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/** 会員登録の確認コード入力画面(GET/POST /register/confirm)。 */
@Controller
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/register/confirm")
    public String showForm(@RequestParam String email, Model model) {
        if (!model.containsAttribute("confirmForm")) {
            EmailVerificationForm form = new EmailVerificationForm();
            form.setEmail(email);
            model.addAttribute("confirmForm", form);
        }
        return "register-confirm";
    }

    @PostMapping("/register/confirm")
    public String confirm(@ModelAttribute EmailVerificationForm form, Model model) {
        try {
            Email email = new Email(form.getEmail() == null ? "" : form.getEmail());
            emailVerificationService.confirmRegistration(email, form.getCode(), Instant.now());
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", "メールアドレスの形式が正しくありません");
            return "register-confirm";
        } catch (InvariantViolationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "register-confirm";
        }

        return "redirect:/login?registered";
    }
}
