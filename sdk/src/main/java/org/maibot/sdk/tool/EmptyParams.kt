package org.maibot.sdk.tool

/**
 * 无参数工具的占位参数类
 *
 * OpenAI function schema 需要 parameters 至少是空对象；用它让无参工具也生成
 * `{"type":"object","properties":{}}`。
 */
class EmptyParams
