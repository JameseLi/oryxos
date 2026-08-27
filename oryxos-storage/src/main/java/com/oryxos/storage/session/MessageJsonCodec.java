package com.oryxos.storage.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * {@code messages_json} 编解码器（第 18 节）：{@code List<Message>} 与 JSON 数组互转。
 *
 * <p>自写分派而非让 Jackson 直接序列化 Message 对象--Spring AI 的消息类带 metadata/media 等运行时字段，
 * 直接序列化既臃肿又跨版本脆；这里只保真「下一轮 Prompt 组装所需」的字段：四型消息（system/user/assistant 含 toolCalls/tool 含 responses）的
 * 文本与工具调用配对信息（id/name/arguments、id/name/responseData）。metadata 等不保真是核心阶段的有意口径 （课件：历史截断、上下文压缩都从简）。
 *
 * <p>未知 type 反序列化时抛清晰异常--存量的 JSON 若损坏，宁可炸出来也不静默吞历史。
 */
final class MessageJsonCodec {

  private static final String TYPE_SYSTEM = "system";
  private static final String TYPE_USER = "user";
  private static final String TYPE_ASSISTANT = "assistant";
  private static final String TYPE_TOOL = "tool";
  private static final String FIELD_TOOL_CALLS = "toolCalls";
  private static final String FIELD_RESPONSES = "responses";

  private final ObjectMapper mapper = new ObjectMapper();

  /** 序列化：消息列表 -> JSON 数组文本（空历史序列化为 {@code []}）。 */
  String serialize(List<Message> messages) {
    ArrayNode array = mapper.createArrayNode();
    for (Message message : messages) {
      array.add(toNode(message));
    }
    return array.toString();
  }

  /** 反序列化：JSON 数组文本 -> 消息列表（按 type 构造回 Spring AI 消息）。 */
  List<Message> deserialize(String json) {
    List<Message> messages = new ArrayList<>();
    if (json == null || json.isBlank()) {
      return messages;
    }
    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("messages_json 解析失败: " + e.getMessage(), e);
    }
    if (!root.isArray()) {
      throw new IllegalStateException("messages_json 顶层必须是 JSON 数组");
    }
    for (JsonNode node : root) {
      messages.add(fromNode(node));
    }
    return messages;
  }

  private ObjectNode toNode(Message message) {
    ObjectNode node = mapper.createObjectNode();
    if (message instanceof SystemMessage) {
      node.put("type", TYPE_SYSTEM);
      node.put("content", text(message));
      return node;
    }
    if (message instanceof UserMessage) {
      node.put("type", TYPE_USER);
      node.put("content", text(message));
      return node;
    }
    if (message instanceof AssistantMessage assistant) {
      node.put("type", TYPE_ASSISTANT);
      node.put("content", text(message));
      ArrayNode calls = node.putArray(FIELD_TOOL_CALLS);
      for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
        ObjectNode callNode = calls.addObject();
        callNode.put("id", call.id());
        callNode.put("type", call.type());
        callNode.put("name", call.name());
        callNode.put("arguments", call.arguments());
      }
      return node;
    }
    if (message instanceof ToolResponseMessage toolMessage) {
      node.put("type", TYPE_TOOL);
      ArrayNode responses = node.putArray(FIELD_RESPONSES);
      for (ToolResponseMessage.ToolResponse response : toolMessage.getResponses()) {
        ObjectNode responseNode = responses.addObject();
        responseNode.put("id", response.id());
        responseNode.put("name", response.name());
        responseNode.put("responseData", response.responseData());
      }
      return node;
    }
    throw new IllegalStateException("不支持序列化的消息类型: " + message.getClass().getName());
  }

  private Message fromNode(JsonNode node) {
    String type = node.path("type").asText("");
    String content = node.path("content").asText(null);
    if (TYPE_SYSTEM.equals(type)) {
      return new SystemMessage(content);
    }
    if (TYPE_USER.equals(type)) {
      return new UserMessage(content);
    }
    if (TYPE_ASSISTANT.equals(type)) {
      List<AssistantMessage.ToolCall> calls = new ArrayList<>();
      for (JsonNode call : node.path(FIELD_TOOL_CALLS)) {
        calls.add(
            new AssistantMessage.ToolCall(
                call.path("id").asText(null),
                call.path("type").asText(null),
                call.path("name").asText(null),
                call.path("arguments").asText(null)));
      }
      AssistantMessage.Builder builder = AssistantMessage.builder();
      if (content != null) {
        builder.content(content);
      }
      if (!calls.isEmpty()) {
        builder.toolCalls(calls);
      }
      return builder.build();
    }
    if (TYPE_TOOL.equals(type)) {
      List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
      for (JsonNode response : node.path(FIELD_RESPONSES)) {
        responses.add(
            new ToolResponseMessage.ToolResponse(
                response.path("id").asText(null),
                response.path("name").asText(null),
                response.path("responseData").asText(null)));
      }
      return ToolResponseMessage.builder().responses(responses).build();
    }
    throw new IllegalStateException("messages_json 含未知消息 type: " + type);
  }

  private static String text(Message message) {
    // AbstractMessage.getText() 统一取文本（17 节已核），null 序列化由 Jackson 存 null
    return ((AbstractMessage) message).getText();
  }
}
