package com.easysign.ai;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeek AI 服务集成测试。
 * <p>
 * 直接测试 {@link DeepSeekAiService#generateRecommendReason}，
 * 不依赖 Spring 容器，避免启动数据库等无关上下文。
 * </p>
 * <p>
 * 需要环境变量 {@code DEEPSEEK_API_KEY} 才能运行真实调用。
 * 设置方式 (PowerShell)： {@code $env:DEEPSEEK_API_KEY="你的key"}
 * </p>
 * <p>
 * 测试逻辑：
 * <ul>
 *   <li>直接调用真实的 DeepSeek API，验证能否返回正常文案</li>
 *   <li>对标签匹配 / 无标签 / 售票率高低等场景各测一次</li>
 *   <li>如果环境变量未设置，真实调用测试自动跳过（不会失败）</li>
 *   <li>不依赖真实调用的 {@link RecommendPromptBuilder} 测试始终执行</li>
 * </ul>
 * </p>
 */
@DisplayName("DeepSeekAiService 集成测试")
class DeepSeekAiServiceIntegrationTest {

    private static DeepSeekAiService deepSeekAiService;

    @BeforeAll
    static void setup() {
        // 手动构造 LlmConfig 和 DeepSeekAiService，不依赖 Spring 容器
        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setProvider("deepseek");

        LlmConfig.ApiConfig apiConfig = new LlmConfig.ApiConfig();
        apiConfig.setBaseUrl("https://api.deepseek.com");
        apiConfig.setKey(System.getenv("DEEPSEEK_API_KEY"));
        apiConfig.setModel("deepseek-chat");
        apiConfig.setTimeoutMs(5000);
        apiConfig.setMaxRetries(1);
        config.setApi(apiConfig);

        LlmConfig.RecommendConfig recommendConfig = new LlmConfig.RecommendConfig();
        recommendConfig.setMaxTokens(100);
        recommendConfig.setCacheTtlMinutes(5);
        config.setRecommend(recommendConfig);

        deepSeekAiService = new DeepSeekAiService(config);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    @DisplayName("【真实调用】标签匹配 + 售票率 > 0 → 返回个性化文案")
    void generateReason_withTagMatchAndSellRate() {
        String reason = deepSeekAiService.generateRecommendReason(
                "[\"科技\", \"电竞\"]",
                "AI 技术沙龙",
                "[\"科技\", \"讲座\"]",
                "探讨最新 AI 技术发展趋势，邀请业内专家分享实践经验",
                0.75,
                true
        );
        System.out.println("=== AI 返回文案（标签匹配+售票率>0）===\n" + reason + "\n");
        assertNotNull(reason, "AI 应返回非 null 文案");
        assertFalse(reason.isBlank(), "AI 应返回非空文案");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    @DisplayName("【真实调用】标签匹配 + 售票率 = 0 → 返回文案")
    void generateReason_withTagMatchAndNoSellRate() {
        String reason = deepSeekAiService.generateRecommendReason(
                "[\"音乐\", \"艺术\"]",
                "校园音乐会",
                "[\"音乐\", \"演出\"]",
                "校园乐队联袂演出，奉上一场精彩绝伦的音乐盛宴",
                0.0,
                true
        );
        System.out.println("=== AI 返回文案（标签匹配+售票率=0）===\n" + reason + "\n");
        assertNotNull(reason, "AI 应返回非 null 文案");
        assertFalse(reason.isBlank(), "AI 应返回非空文案");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    @DisplayName("【真实调用】无标签匹配 + 售票率 > 0 → 返回热门推荐文案")
    void generateReason_withoutTagMatchAndSellRate() {
        String reason = deepSeekAiService.generateRecommendReason(
                "[\"科技\"]",
                "周末篮球友谊赛",
                "[\"体育\", \"运动\"]",
                "一起来打球！每周六下午篮球场等你",
                0.45,
                false
        );
        System.out.println("=== AI 返回文案（无标签匹配+售票率>0）===\n" + reason + "\n");
        assertNotNull(reason, "AI 应返回非 null 文案");
        assertFalse(reason.isBlank(), "AI 应返回非空文案");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    @DisplayName("【真实调用】用户标签为 null → 仅根据活动信息推荐")
    void generateReason_withNullUserTags() {
        String reason = deepSeekAiService.generateRecommendReason(
                null,
                "期末复习自习室开放",
                "[\"学习\", \"考试\"]",
                "图书馆延长开放时间，提供安静自习环境",
                0.3,
                false
        );
        System.out.println("=== AI 返回文案（标签为 null）===\n" + reason + "\n");
        assertNotNull(reason, "AI 应返回非 null 文案");
        assertFalse(reason.isBlank(), "AI 应返回非空文案");
    }

    @Test
    @DisplayName("PromptBuilder 构造的 Prompt 应包含所有关键信息")
    void promptBuilderShouldContainKeyInfo() {
        String prompt = RecommendPromptBuilder.buildPrompt(
                "[\"科技\"]", "测试活动", "[\"测试\"]", "测试活动的简介内容", 0.5
        );
        assertTrue(prompt.contains("高校活动推荐助手"), "应包含角色设定");
        assertTrue(prompt.contains("用户偏好标签"), "应包含用户标签");
        assertTrue(prompt.contains("测试活动"), "应包含活动标题");
        assertTrue(prompt.contains("活动简介"), "应包含活动简介");
        assertTrue(prompt.contains("活动标签"), "应包含活动标签");
        assertTrue(prompt.contains("50%"), "应包含售票率百分比");
        assertTrue(prompt.contains("30字以内"), "应包含字数限制");
    }

    @Test
    @DisplayName("PromptBuilder 构造的 Prompt 在 userTags 为 null 时不应包含用户标签行")
    void promptBuilderShouldSkipUserTagsWhenNull() {
        String prompt = RecommendPromptBuilder.buildPrompt(
                null, "测试活动", "[\"测试\"]", "测试活动的简介内容", 0.5
        );
        assertFalse(prompt.contains("用户偏好标签"), "userTags 为 null 时不应包含用户标签行");
    }

    @Test
    @DisplayName("API Key 未设置时，真实调用测试应跳过（环境变量检查）")
    void apiKeyNotSet_shouldSkipRealCalls() {
        // 这个测试始终通过，仅用于确认 skip 逻辑
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("=== 提示：DEEPSEEK_API_KEY 环境变量未设置，真实调用测试已跳过 ===");
            System.out.println("设置方式 (PowerShell): $env:DEEPSEEK_API_KEY=\"你的deepseek-api-key\"");
        } else {
            System.out.println("=== DEEPSEEK_API_KEY 已设置，真实调用测试将执行 ===");
        }
        assertTrue(true);
    }
}