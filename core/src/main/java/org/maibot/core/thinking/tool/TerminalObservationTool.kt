package org.maibot.core.thinking.tool

/**
 * 终止本轮观察的工具
 *
 * 执行后 ObserverAI 不再继续工具轮，按 [org.maibot.core.thinking.observer.ObservationResult.Wait]
 * 收尾。具体是否改变状态由工具自己决定。
 */
interface TerminalObservationTool
