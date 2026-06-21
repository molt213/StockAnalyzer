package com.stockanalyzer.data.remote;

import com.stockanalyzer.data.remote.dto.AIRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * AI API 接口
 * 支持 DeepSeek (OpenAI 兼容格式) / Claude / 通义千问
 */
public interface AiApiService {

    /**
     * DeepSeek / OpenAI 兼容 Chat Completions API
     * POST /chat/completions
     * Authorization: Bearer <api-key>
     */
    @POST("chat/completions")
    Call<AIRequest.DeepSeekResponse> sendChatCompletion(
            @Header("Authorization") String authHeader,
            @Header("Content-Type") String contentType,
            @Body AIRequest.DeepSeekRequest request
    );

    /**
     * Claude Messages API (备用)
     */
    @POST("messages")
    Call<AIRequest.ClaudeResponse> sendClaudeMessage(
            @Header("x-api-key") String apiKey,
            @Header("anthropic-version") String version,
            @Header("Content-Type") String contentType,
            @Body AIRequest.ClaudeMessageRequest request
    );

    /**
     * 通义千问兼容接口 (备用)
     */
    @POST("services/aigc/text-generation/generation")
    Call<AIRequest.QwenResponse> sendQwenMessage(
            @Header("Authorization") String authHeader,
            @Body AIRequest.QwenRequest request
    );
}
