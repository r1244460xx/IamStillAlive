package com.imsa.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("業務邏輯檢核不通過: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("請求欄位驗證不通過: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }

    // Edge Case 3: 攔截資料庫唯一約束衝突 (如併發重複註冊、重複資料衝突)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("資料庫約束衝突 (可能為併發重複請求): {}", ex.getMessage());
        String msg = "該資料已存在或違反唯一約束，請勿重複提交";
        String lowerMsg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (lowerMsg.contains("phone") || lowerMsg.contains("users_phone_key")) {
            msg = "該手機號碼已註冊，請直接登入或使用其他號碼";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg));
    }

    // 格式解析錯誤 (例如 JSON 格式無效、型態不相容)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("請求 JSON 格式解析失敗: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "請求內容格式錯誤或非合法 JSON 格式"));
    }

    // Edge Case 2: 全域未知異常兜底，保證 100% 回傳 JSON，防止 Android 客戶端接收到 HTML 500 頁面而閃退
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("伺服器發生非預期系統異常: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "伺服器忙碌中或發生非預期錯誤，請稍後再試"));
    }
}
