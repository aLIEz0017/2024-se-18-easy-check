package com.easysign.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

/**
 * 标签工具类
 * <p>
 * 提供 JSON 格式的标签字符串与 {@link List<String>} 之间的转换能力，
 * 供 {@link com.easysign.service.impl.EventServiceImpl} 和
 * {@link com.easysign.service.impl.RecommendServiceImpl} 共用。
 * </p>
 */
public class TagUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TagUtils() {
        // 工具类禁止实例化
    }

    /**
     * 将 DB 存储的 JSON 字符串转换为 {@code List<String>}。
     *
     * @param tagsJson JSON 数组字符串，如 {@code ["科技","电竞"]}
     * @return 标签列表，null / 空字符串 / 解析失败时返回空列表
     */
    public static List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 将 {@code List<String>} 序列化为 JSON 字符串。
     *
     * @param tags 标签列表
     * @return JSON 数组字符串，null 时返回 null
     */
    public static String toTagsJson(List<String> tags) {
        if (tags == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("标签序列化失败", e);
        }
    }
}