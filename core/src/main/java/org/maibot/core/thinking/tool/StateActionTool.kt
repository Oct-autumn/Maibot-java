package org.maibot.core.thinking.tool

/**
 * 流状态动作工具
 *
 * 工具只负责触发状态切换；状态、退避间隔和 cap 计数由 ThinkingFlow 的状态切换方法统一处理。
 */
interface StateActionTool : TerminalObservationTool
