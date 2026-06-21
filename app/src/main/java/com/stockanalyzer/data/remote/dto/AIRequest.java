package com.stockanalyzer.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * AI API 请求/响应 DTO
 * 兼容 Anthropic Claude API 格式
 */
public class AIRequest {

    // ========== Claude API 请求 ==========
    public static class ClaudeMessageRequest {
        @SerializedName("model")
        public String model;

        @SerializedName("max_tokens")
        public int maxTokens = 4096;

        @SerializedName("messages")
        public List<Message> messages;

        @SerializedName("system")
        public String system;

        @SerializedName("temperature")
        public double temperature = 0.7;

        public ClaudeMessageRequest(String model, List<Message> messages, String system) {
            this.model = model;
            this.messages = messages;
            this.system = system;
        }
    }

    public static class Message {
        @SerializedName("role")
        public String role;  // "user" 或 "assistant"

        @SerializedName("content")
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    // ========== Claude API 响应 ==========
    public static class ClaudeResponse {
        @SerializedName("id")
        public String id;

        @SerializedName("type")
        public String type;

        @SerializedName("role")
        public String role;

        @SerializedName("content")
        public List<ContentBlock> content;

        @SerializedName("model")
        public String model;

        @SerializedName("stop_reason")
        public String stopReason;

        @SerializedName("usage")
        public Usage usage;

        @SerializedName("error")
        public ApiError error;
    }

    public static class ContentBlock {
        @SerializedName("type")
        public String type;  // "text"

        @SerializedName("text")
        public String text;
    }

    public static class Usage {
        @SerializedName("input_tokens")
        public int inputTokens;

        @SerializedName("output_tokens")
        public int outputTokens;
    }

    public static class ApiError {
        @SerializedName("type")
        public String type;

        @SerializedName("message")
        public String message;
    }

    // ========== DeepSeek / OpenAI 兼容格式 ==========

    /**
     * DeepSeek 请求 (OpenAI 兼容格式)
     * POST /chat/completions
     */
    public static class DeepSeekRequest {
        @SerializedName("model")
        public String model;

        @SerializedName("messages")
        public List<ChatMessage> messages;

        @SerializedName("max_tokens")
        public int maxTokens = 4096;

        @SerializedName("temperature")
        public double temperature = 0.7;

        @SerializedName("stream")
        public boolean stream = false;

        public DeepSeekRequest(String model, List<ChatMessage> messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    /**
     * Chat 消息 (OpenAI 格式: system/user/assistant)
     */
    public static class ChatMessage {
        @SerializedName("role")
        public String role;  // "system", "user", "assistant"

        @SerializedName("content")
        public String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * DeepSeek 响应 (OpenAI 兼容格式)
     */
    public static class DeepSeekResponse {
        @SerializedName("id")
        public String id;

        @SerializedName("object")
        public String object;

        @SerializedName("created")
        public long created;

        @SerializedName("model")
        public String model;

        @SerializedName("choices")
        public List<Choice> choices;

        @SerializedName("usage")
        public OpenUsage usage;

        @SerializedName("error")
        public ApiError error;
    }

    public static class Choice {
        @SerializedName("index")
        public int index;

        @SerializedName("message")
        public ChatMessage message;

        @SerializedName("finish_reason")
        public String finishReason;
    }

    public static class OpenUsage {
        @SerializedName("prompt_tokens")
        public int promptTokens;

        @SerializedName("completion_tokens")
        public int completionTokens;

        @SerializedName("total_tokens")
        public int totalTokens;
    }

    // ========== 通义千问 Qwen API 兼容格式 ==========
    public static class QwenRequest {
        @SerializedName("model")
        public String model;

        @SerializedName("input")
        public QwenInput input;

        @SerializedName("parameters")
        public QwenParameters parameters;

        public QwenRequest(String model, QwenInput input) {
            this.model = model;
            this.input = input;
            this.parameters = new QwenParameters();
        }
    }

    public static class QwenInput {
        @SerializedName("messages")
        public List<Message> messages;

        public QwenInput(List<Message> messages) {
            this.messages = messages;
        }
    }

    public static class QwenParameters {
        @SerializedName("result_format")
        public String resultFormat = "text";

        @SerializedName("temperature")
        public double temperature = 0.7;

        @SerializedName("max_tokens")
        public int maxTokens = 4096;
    }

    public static class QwenResponse {
        @SerializedName("output")
        public QwenOutput output;

        @SerializedName("usage")
        public Usage usage;
    }

    public static class QwenOutput {
        @SerializedName("text")
        public String text;

        @SerializedName("finish_reason")
        public String finishReason;
    }
}
