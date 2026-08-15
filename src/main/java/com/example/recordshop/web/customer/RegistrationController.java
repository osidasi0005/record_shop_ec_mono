package com.example.recordshop.web.customer;

import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailVerificationService;
import com.example.recordshop.domain.shared.InvariantViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** 会員登録画面(GET/POST /register)。登録要求は確認コードの発行・送信のみ行い、
 *  本登録は確認コード入力画面({@link EmailVerificationController})で確定する。 */
@Controller
public class RegistrationController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final EmailVerificationService emailVerificationService;

    public RegistrationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
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

        Email email;
        try {
            email = new Email(form.getEmail() == null ? "" : form.getEmail());
            emailVerificationService.requestVerification(email, form.getPassword(), form.getDisplayName(), Instant.now());
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", "メールアドレスの形式が正しくありません");
            return "register";
        } catch (InvariantViolationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "register";
        }

        return "redirect:/register/confirm?email=" + URLEncoder.encode(email.value(), StandardCharsets.UTF_8);
    }
}
