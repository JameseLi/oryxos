package com.oryxos.core.support;

import com.oryxos.core.audit.AuditStore;
import com.oryxos.core.audit.LlmCallRecord;
import com.oryxos.core.audit.ToolInvocationRecord;
import java.util.ArrayList;
import java.util.List;

/** 收集型 AuditStore（单测断言用）。 */
public class RecordingAuditStore implements AuditStore {

  public final List<LlmCallRecord> llmCalls = new ArrayList<>();
  public final List<ToolInvocationRecord> toolInvocations = new ArrayList<>();

  @Override
  public void saveLlmCall(LlmCallRecord record) {
    llmCalls.add(record);
  }

  @Override
  public void saveToolInvocation(ToolInvocationRecord record) {
    toolInvocations.add(record);
  }
}
