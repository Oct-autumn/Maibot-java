package org.maibot.sdk.eventchannel

interface GlobalEventService {
    fun listHandlers(): List<String>

    fun addHandler(name: String?, handler: SimpleEventHandler<*>?)

    fun removeHandler(name: String?)

    fun fireEvent(event: Any?)
}
