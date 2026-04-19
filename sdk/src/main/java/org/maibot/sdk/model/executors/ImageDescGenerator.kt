package org.maibot.sdk.model.executors

import org.maibot.sdk.config.ChoosableModel
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.ModelManager
import org.maibot.sdk.model.payload.MessageContextItem

@Component
class ImageDescGenerator
@AutoInject
private constructor(
    @Value($$"${model_tasks.image_desc}") taskConfig: List<ChoosableModel>,
    private val modelManager: ModelManager,
) {
    init {
        // 向ModelManager注册图像描述模型的配置
        modelManager["image_desc"] = taskConfig
    }

    fun generateImageDescription(
        imageData: ByteArray, imageType: String, isEmoji: Boolean
    ): String? {
        // 构造请求体
        // TODO: 支持GIF
        val input = MessageContextItem.ofUser(
            listOf(
                MessageContextItem.Content.ofImage(imageType, imageData), MessageContextItem.Content.ofText(
                    when {
                        isEmoji && imageType == "gif" -> EMOJI_GIF_DESC_PROMPT
                        isEmoji -> EMOJI_DESC_PROMPT
                        imageType == "gif" -> GIF_DESC_PROMPT
                        else -> IMAGE_DESC_PROMPT
                    }
                )
            )
        )

        modelManager["image_desc"]!!.getResponse(listOf(input)).get().let { resp ->
            return resp.response?.contentList[0]?.asText()
        }
    }

    companion object {
        private const val IMAGE_DESC_PROMPT =
            "这是一张图片。\n" +
                    "请用中文描述这张图片的内容：请留意其主题、直观感受；如果有文字，请把文字描述概括出来；输出为一段平文本，最多30字，请注意不要分点，只输出一段文本。"
        private const val GIF_DESC_PROMPT =
            "这是一个动图的抽帧，每一张图代表了动图的某一帧，黑色背景代表透明。\n" +
                    "请用中文描述这张动图的内容：请留意其主题、直观感受；如果有文字，请把文字描述概括出来；输出为一段平文本，最多30字，请注意不要分点，只输出一段文本。"
        private const val EMOJI_DESC_PROMPT =
            "这是一个表情包。\n" +
                    "请用中文描述这张表情包所表达的情感和内容：简单描述内容、从互联网梗/meme的角度去分析。输出为一段平文本，最多30字，请注意不要分点，只输出一段文本。"
        private const val EMOJI_GIF_DESC_PROMPT =
            "这是一个动图表情包的抽帧，每一张图代表了动图的某一帧，黑色背景代表透明。\n" +
                    "请用中文描述这张表情包所表达的情感和内容：简单描述内容、从互联网梗/meme的角度去分析。输出为一段平文本，最多30字，请注意不要分点，只输出一段文本。"
    }
}