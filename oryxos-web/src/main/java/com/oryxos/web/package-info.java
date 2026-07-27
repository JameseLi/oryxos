/**
 * oryxos-web：核心能力五（Web Service，OryxOS 对外完整门面）。
 *
 * <p>包含：{@code WebServer}（Spring MVC + Java 21 virtual thread）、6 个 ApiController
 * （Session / Agent / Profile / Memory / Tool / System）、{@code GlobalExceptionHandler}
 * （统一 {@code ApiResponse} 响应信封：code / message / data / timestamp）、OpenAPI 文档。
 *
 * <p>关键约束（技术方案第 7 章）：Controller 只做参数校验、响应包装、错误处理，
 * 实际逻辑委托核心层服务；核心阶段无认证（假设内网）、不做 SSE / WebSocket / RBAC / 限流。
 */
package com.oryxos.web;
