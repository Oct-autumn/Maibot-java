package org.maibot.sdk.model.payload


@ExposedCopyVisibility
@JvmRecord
data class AvailableFunctionItem
private constructor(
    val name: String,
    val description: String,
    val params: Class<*>?
) {
    companion object {
        @JvmStatic
        fun of(name: String, description: String = "", params: Class<*>? = null): AvailableFunctionItem {
            return AvailableFunctionItem(name, description, params)
        }
    }
}
