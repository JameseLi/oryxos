package com.oryxos.provider;

/** Provider 名找不到对应的 ChatModel 时抛（课件「第三步」：未知名直接报错，不悄悄用错、不留空跑过）。 */
public class ProviderNotFoundException extends RuntimeException {

  public ProviderNotFoundException(String providerName) {
    super("Provider 未在全局层声明或未构造成功: " + providerName);
  }
}
