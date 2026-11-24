package com.kongklang.controller;

import com.kongklang.service.AccessService;
import com.kongklang.service.KkAiService;
import com.kongklang.service.KkAiLocalModel;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class KkAiController {
    private final AccessService access;
    private final KkAiService assistant;
    private final KkAiLocalModel localModel;

    public KkAiController(AccessService access, KkAiService assistant, KkAiLocalModel localModel) {
        this.access = access;
        this.assistant = assistant;
        this.localModel = localModel;
    }

    @GetMapping("/kk-ai")
    public String page(HttpSession session) {
        access.require(session, "OWNER", "ADMIN", "ACCOUNTING");
        return "kk-ai";
    }

    public record Question(String question, String productCode, String previousQuestion) {}
    public record Reply(String title, String text, List<String> facts, String sourcePath, String suggestion) {}

    @PostMapping("/api/kk-ai/ask")
    @ResponseBody
    public ResponseEntity<?> ask(@RequestBody Question input, HttpSession session) {
        var actor = access.require(session, "OWNER", "ADMIN", "ACCOUNTING");
        if (input == null || input.question() == null || input.question().isBlank() || input.question().length() > 500
                || input.productCode() != null && input.productCode().length() > 255
                || input.previousQuestion() != null && input.previousQuestion().length() > 500) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "กรุณากรอกคำถามไม่เกิน 500 ตัวอักษร"));
        }
        var answer = assistant.answer(actor.storeId(), input.question(), input.productCode(), input.previousQuestion());
        var suggestion = localModel.suggest(input.question(), answer).orElse(null);
        return ResponseEntity.ok(new Reply(answer.title(), answer.text(), answer.facts(), answer.sourcePath(), suggestion));
    }
}
