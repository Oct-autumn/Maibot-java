package org.maibot.core.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class JsonValidator {
    /**
     * 验证 JSON 对象是否包含类定义的所有字段
     *
     * @param json  JSON 对象
     * @param clazz 类定义
     */
    public static List<String> validateJsonAgainstClass(JsonObject json, Class<?> clazz) {
        List<String> missing = new ArrayList<>();
        recursiveValidation(json, clazz, "", missing);
        return missing;
    }

    /**
     * 递归验证 JSON 元素是否包含类定义的所有字段
     *
     * @param element    JSON 元素
     * @param clazz      类定义
     * @param pathPrefix 当前路径前缀
     * @param missing    缺失字段列表
     */
    private static void recursiveValidation(JsonElement element, Class<?> clazz, String pathPrefix, List<String> missing) {
        if (element == null || !element.isJsonObject()) {
            missing.add(pathPrefix.isEmpty() ? clazz.getSimpleName() : pathPrefix);
            return;
        }
        JsonObject obj = element.getAsJsonObject();

        for (Field field : clazz.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                continue;   // 忽略静态与瞬态字段
            }

            // 获取字段名称，考虑 @SerializedName 注解
            String key = field.getName();
            SerializedName sn = field.getAnnotation(SerializedName.class);
            if (sn != null && sn.value() != null && !sn.value().isEmpty()) {
                key = sn.value();
            }

            // 构建完整路径
            String fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;

            // 检查字段是否存在且非 null
            // 允许标记为 @Nullable 的字段为null，否则记录缺失
            if ((!obj.has(key) || obj.get(key).isJsonNull())) {
                if (!field.isAnnotationPresent(Nullable.class)) {
                    missing.add(fullPath);
                }
                continue;
            }

            Class<?> fieldType = field.getType();
            JsonElement child = obj.get(key);

            // 如果是简单类型，不再递归
            if (isSimpleType(fieldType)) {
                continue;
            }

            // 集合/数组/映射等直接跳过深度校验（可按需扩展）
            if (Collection.class.isAssignableFrom(fieldType) ||
                    Map.class.isAssignableFrom(fieldType) ||
                    fieldType.isArray()) {
                // 如果需要，可以尝试根据泛型类型对元素进行校验（较复杂，留作扩展）
                continue;
            }

            // 复杂对象递归检查
            recursiveValidation(child, fieldType, fullPath, missing);
        }
    }

    /**
     * 判断类型是否为简单类型（基本类型、字符串、数字）
     *
     * @param type 类型
     * @return 是否为简单类型
     */
    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || Number.class.isAssignableFrom(type);
    }
}
