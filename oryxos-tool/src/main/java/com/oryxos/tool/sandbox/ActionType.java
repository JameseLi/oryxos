package com.oryxos.tool.sandbox;

/**
 * 受控动作类型（技术方案 §6.7）：文件读 / 文件写 / Shell 命令 / HTTP 请求四值。
 *
 * <p>为什么文件读写分开：便于未来按读/写分权限（§6.7）；24 节 {@code WhitelistSandbox} 的 {@code enforce} 会把 {@code
 * FILE_READ}、{@code FILE_WRITE} 两 case 同路由到 {@code checkFilePath}。
 */
public enum ActionType {
  FILE_READ,
  FILE_WRITE,
  SHELL_COMMAND,
  HTTP_REQUEST
}
