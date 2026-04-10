package org.maibot.sdk.model.payload

/**
 * 消息上下文
 */
@ConsistentCopyVisibility
@JvmRecord
data class MessageContextItem
private constructor(
    /**
     * 消息角色
     */
    val role: Role,
    /**
     * 消息内容
     */
    val contentList: List<Content>,
    /**
     * 工具调用ID，仅当role为TOOL时有效，标识该消息内容来源于哪个工具调用
     */
    val toolCallId: String?
) {
    enum class Role(val roleName: String) {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        TOOL("tool");

        companion object {
            private val map = entries.associateBy(Role::roleName)
            fun fromRoleName(roleName: String) = map[roleName]
        }
    }

    class Content
    private constructor(
        /**
         * 内容类型
         */
        private val type: Type,
        /**
         * 内容值
         */
        private val value: ContentInner
    ) {
        enum class Type(val typeName: String) {
            TEXT("text"),
            IMAGE("image"),
            AUDIO("audio"),
            VIDEO("video");

            companion object {
                private val map = entries.associateBy(Type::typeName)
                fun fromTypeName(typeName: String) = map[typeName]
            }
        }

        enum class Detail(val levelName: String) {
            AUTO("auto"),
            LOW("low"),
            HIGH("high");

            companion object {
                private val map = entries.associateBy(Detail::levelName)
                fun fromLevelName(levelName: String) = map[levelName]
            }
        }

        interface ContentInner

        @ConsistentCopyVisibility
        @JvmRecord
        data class TextContent
        internal constructor(val text: String) : ContentInner

        @ConsistentCopyVisibility
        @JvmRecord
        data class ImageContent
        internal constructor(
            val format: String,
            val data: ByteArray,
            val detail: Detail
        ) :
            ContentInner {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ImageContent

                if (format != other.format) return false
                if (!data.contentEquals(other.data)) return false
                if (detail != other.detail) return false

                return true
            }

            override fun hashCode(): Int {
                var result = format.hashCode()
                result = 31 * result + data.contentHashCode()
                result = 31 * result + detail.hashCode()
                return result
            }
        }

        @ConsistentCopyVisibility
        @JvmRecord
        data class AudioContent
        internal constructor(
            val format: String,
            val data: ByteArray
        ) : ContentInner {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as AudioContent

                if (format != other.format) return false
                if (!data.contentEquals(other.data)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = format.hashCode()
                result = 31 * result + data.contentHashCode()
                return result
            }
        }

        @ConsistentCopyVisibility
        @JvmRecord
        data class VideoContent
        internal constructor(
            val format: String,
            val data: ByteArray,
            val detail: Detail,
            val maxFrames: Int,
            val frameRate: Int
        ) : ContentInner {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as VideoContent

                if (maxFrames != other.maxFrames) return false
                if (frameRate != other.frameRate) return false
                if (format != other.format) return false
                if (!data.contentEquals(other.data)) return false
                if (detail != other.detail) return false

                return true
            }

            override fun hashCode(): Int {
                var result = maxFrames
                result = 31 * result + frameRate
                result = 31 * result + format.hashCode()
                result = 31 * result + data.contentHashCode()
                result = 31 * result + detail.hashCode()
                return result
            }
        }

        fun asText(): String? {
            return if (type == Type.TEXT) {
                (value as TextContent).text
            } else {
                null
            }
        }

        fun asImage(): ImageContent? {
            return if (type == Type.IMAGE) {
                value as ImageContent
            } else {
                null
            }
        }

        fun asAudio(): AudioContent? {
            return if (type == Type.AUDIO) {
                value as AudioContent
            } else {
                null
            }
        }

        fun asVideo(): VideoContent? {
            return if (type == Type.VIDEO) {
                value as VideoContent
            } else {
                null
            }
        }

        fun isText() = type == Type.TEXT

        fun isImage() = type == Type.IMAGE

        fun isAudio() = type == Type.AUDIO

        fun isVideo() = type == Type.VIDEO

        companion object {
            // 支持的图片格式列表
            // 特别的，gif仅支持非动图
            val SUPPORTED_IMAGE_FORMATS = setOf("png", "jpg", "jpeg", "webp", "gif")

            // 支持的音频格式列表
            val SUPPORTED_AUDIO_FORMATS = setOf("mp3", "wav", "ogg")

            // 支持的视频格式列表
            val SUPPORTED_VIDEO_FORMATS = setOf("mp4", "avi", "mov", "webm")

            @JvmStatic
            fun ofText(text: String) = Content(Type.TEXT, TextContent(text))

            @JvmStatic
            fun ofImage(format: String, data: ByteArray, detail: Detail = Detail.AUTO): Content {
                val lowerCaseFormat = format.lowercase()
                if (!SUPPORTED_IMAGE_FORMATS.contains(lowerCaseFormat)) {
                    throw IllegalArgumentException("Unsupported image format: $format. Supported formats are: $SUPPORTED_IMAGE_FORMATS")
                }
                return Content(Type.IMAGE, ImageContent(format, data, detail))
            }

            @JvmStatic
            fun ofAudio(format: String, data: ByteArray): Content {
                val lowerCaseFormat = format.lowercase()
                if (!SUPPORTED_AUDIO_FORMATS.contains(lowerCaseFormat)) {
                    throw IllegalArgumentException("Unsupported audio format: $format. Supported formats are: $SUPPORTED_AUDIO_FORMATS")
                }
                return Content(Type.AUDIO, AudioContent(format, data))
            }

            @JvmStatic
            fun ofVideo(
                format: String,
                data: ByteArray,
                detail: Detail,
                maxFrames: Int,
                frameRate: Int
            ): Content {
                val lowerCaseFormat = format.lowercase()
                if (!SUPPORTED_VIDEO_FORMATS.contains(lowerCaseFormat)) {
                    throw IllegalArgumentException("Unsupported video format: $format. Supported formats are: $SUPPORTED_VIDEO_FORMATS")
                }
                return Content(
                    Type.VIDEO,
                    VideoContent(format, data, detail, maxFrames, frameRate)
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun ofUser(contentList: List<Content>) = MessageContextItem(Role.USER, contentList, null)

        @JvmStatic
        fun ofAssistant(contentList: List<Content>) = MessageContextItem(Role.ASSISTANT, contentList, null)

        @JvmStatic
        fun ofSystem(content: String) = MessageContextItem(Role.SYSTEM, listOf(Content.ofText(content)), null)

        @JvmStatic
        fun ofTool(contentList: List<Content>, toolCallId: String) =
            MessageContextItem(Role.TOOL, contentList, toolCallId)
    }
}
