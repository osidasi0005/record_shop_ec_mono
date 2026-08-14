package com.example.recordshop.web.customer;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ログイン画面(GET /login)。
 * POST /login 自体は Spring Security の formLogin フィルタが処理するため、ここにはハンドラを置かない。
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String showForm() {
        return "login";
    }
}
