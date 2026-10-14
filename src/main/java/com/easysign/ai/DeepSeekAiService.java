package com.easysign.ai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * DeepSeek AI 服务实现（真实 LLM 接入）。
 * <p>
 * 通过 {@link ConditionalOnProperty} 按配置切换：
 * <pre>
 * llm.provider=deepseek
 * llm.enabled=true
 * </pre>
 * </p>
 * <p>
 * 使用 Hutool {@link HttpUtil} 调用 DeepSeek API（OpenAI 兼容格式），
 * 无需额外引入 HTTP 客户端依赖。
 * </p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "deepseek")
public class DeepSeekAiService implements AiService {

    private final LlmConfig llmConfig;

    public DeepSeekAiService(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
    }

    @Override
    public String generateDescription(String title) {
        log.info("DeepSeekAiService.generateDescription: title={}", title);

        String prompt = "你是一个活动宣传文案撰写助手。请根据以下活动标题，生成一段吸引人的宣传文案。" +
                "要求：简洁有力，突出活动亮点，语气热情但不浮夸，不超过100字。\n\n活动标题：" + title;

        String requestBody = buildRequestBody(prompt);

        LlmConfig.ApiConfig apiConfig = llmConfig.getApi();
        String url = apiConfig.getBaseUrl() + "/v1/chat/completions";

        // 带重试的 HTTP 调用
        int maxRetries = Math.max(apiConfig.getMaxRetries(), 1);
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse response = HttpRequest.post(url)
                        .header("Authorization", "Bearer " + apiConfig.getKey())
                        .header("Content-Type", "application/json")
                        .body(requestBody)
                        .timeout(apiConfig.getTimeoutMs())
                        .execute();

                int status = response.getStatus();
                String body = response.body();

                if (status == 200) {
                    String content = parseResponse(body);
                    if (content != null && !content.isBlank()) {
                        return content;
                    }
                    log.warn("DeepSeek 返回空内容: attempt={}", attempt);
                } else {
                    log.warn("DeepSeek API 返回非 200 状态码: status={}, body={}, attempt={}",
                            status, body, attempt);
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("DeepSeek API 调用失败: attempt={}/{}", attempt, maxRetries, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(200L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("DeepSeek API 调用全部重试失败，返回 null", lastException);
        return null;
    }

    /**
     * ⚠️ 废弃：该方法目前无调用方，保留仅用于接口兼容，后续版本可移除。
     */
    @Override
    public double predictArrivalRate(Long eventId) {
        // 保留接口兼容，但推荐理由不使用此方法
        throw new UnsupportedOperationException("DeepSeek 暂不支持 predictArrivalRate");
    }

    @Override
    public String generateRecommendReason(String userTags, String eventTitle, String eventTags,
                                           String eventDescription, double sellRate,
                                           boolean hasTagMatch) {
        log.info("DeepSeekAiService.generateRecommendReason: title={}", eventTitle);

        String prompt = RecommendPromptBuilder.buildPrompt(userTags, eventTitle, eventTags,
                eventDescription, sellRate);
        String requestBody = buildRequestBody(prompt);

        LlmConfig.ApiConfig apiConfig = llmConfig.getApi();
        String url = apiConfig.getBaseUrl() + "/v1/chat/completions";

        // 带重试的 HTTP 调用
        int maxRetries = Math.max(apiConfig.getMaxRetries(), 1);
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse response = HttpRequest.post(url)
                        .header("Authorization", "Bearer " + apiConfig.getKey())
                        .header("Content-Type", "application/json")
                        .body(requestBody)
                        .timeout(apiConfig.getTimeoutMs())
                        .execute();

                int status = response.getStatus();
                String body = response.body();

                if (status == 200) {
                    String content = parseResponse(body);
                    if (content != null && !content.isBlank()) {
                        return content;
                    }
                    log.warn("DeepSeek 返回空内容: attempt={}", attempt);
                } else {
                    log.warn("DeepSeek API 返回非 200 状态码: status={}, body={}, attempt={}",
                            status, body, attempt);
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("DeepSeek API 调用失败: attempt={}/{}", attempt, maxRetries, e);
                if (attempt < maxRetries) {
                    // 简单退避
                    try {
                        Thread.sleep(200L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("DeepSeek API 调用全部重试失败，返回 null", lastException);
        return null;
    }

    /**
     * 构建请求体 JSON。
     */
    private String buildRequestBody(String prompt) {
        JSONObject body = new JSONObject();
        body.set("model", llmConfig.getApi().getModel());

        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", prompt);
        messages.add(userMessage);
        body.set("messages", messages);

        body.set("max_tokens", llmConfig.getRecommend().getMaxTokens());
        body.set("temperature", 0.7);

        return JSONUtil.toJsonStr(body);
    }

    /**
     * 解析 DeepSeek API 返回的 OpenAI 兼容格式响应。
     * <pre>
     * {
     *   "choices": [{
     *     "message": {
     *       "content": "生成的推荐理由..."
     *     }
     *   }]
     * }
     * </pre>
     *
     * @param responseBody API 返回的 JSON 字符串
     * @return 解析出的文案内容，解析失败返回 null
     */
    private String parseResponse(String responseBody) {
        try {
            JSONObject json = JSONUtil.parseObj(responseBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("DeepSeek 返回 choices 为空");
                return null;
            }
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            if (message == null) {
                log.warn("DeepSeek 返回 message 为空");
                return null;
            }
            return message.getStr("content");
        } catch (Exception e) {
            log.warn("DeepSeek 响应解析失败: body={}", responseBody, e);
            return null;
        }
    }
}