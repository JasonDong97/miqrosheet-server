package com.era.miqrosheet.app.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.era.miqrosheet.domain.model.bo.ReplyMessage;
import com.era.miqrosheet.domain.service.ISheetOperationService;
import com.era.miqrosheet.domain.service.impl.SheetOperationServiceImpl;
import com.era.miqrosheet.infra.config.OAuthConfig;
import com.era.miqrosheet.infra.util.GzipUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Socket处理器(包括发送信息，接收信息，信息错误等方法。)
 * <p>
 * 代码中rv，rv_end说明
 * 因为websocket传输大小限制
 * 批量更新范围单元格的时候
 * 一次最多1000个单元格
 * 要求 每次传'rv'  最后一次传他'rv_end'
 * rv_end就是个信号
 * 表示这次范围更新数据全部传输完,它自身这次不带数据过去的
 * </p>
 *
 */
@Slf4j
@Component
@ServerEndpoint(value = "/websocket")
public class SheetWebSocket {

    // 用线程安全集合存储所有连接
    private static final Map<String, Set<SheetWebSocket>> SESSION_MAP = new ConcurrentHashMap<>();
    private static final String PARTIAL_MESSAGE_KEY = "partialMessage";
    private static final String SUCCESS = "success";
    private static final String FAIL = "fail";
    private final ISheetOperationService sheetOperationService = SpringUtil.getBean(SheetOperationServiceImpl.class);
    private final OkHttpClient httpClient = new OkHttpClient();
    private final OAuthConfig oAuthConfig = SpringUtil.getBean(OAuthConfig.class);
    private Session session;
    private String gridKey;
    private ReplyMessage lastReply; // 记录最后一次的数据
    private String username;

    // 连接打开
    @OnOpen
    public void onOpen(Session session) {
        Map<String, List<String>> parameters = session.getRequestParameterMap();
        this.session = session;
        this.gridKey = CollUtil.get(parameters.get("g"), 0);
        String token = CollUtil.get(parameters.get("t"), 0);
        this.username = getUserName(token);

        if (StrUtil.isBlank(gridKey)) {
            log.warn("连接参数错误，gridKey不能为空");
            sendMessage("gridKey不能为空");
            closeSession(CloseReason.CloseCodes.CANNOT_ACCEPT, "gridKey不能为空");
            return;
        }

        SESSION_MAP.computeIfAbsent(gridKey, k -> ConcurrentHashMap.newKeySet()).add(this);
        log.info("新连接[{}][{}], 当前表格协同 {} 人。", gridKey, username, SESSION_MAP.get(gridKey).size());
        // 同步其他用户的最后操作信息
        syncReplyMsg();
    }

    private String getUserName(String token) {
        Request request = new Request.Builder()
                .url(oAuthConfig.getUserInfoURL())
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONObject json = JSON.parseObject(body);
                if (json != null && 200 == json.getIntValue("code")) {
                    JSONObject userInfo = json.getJSONObject("data");
                    if (userInfo != null) {
                        String name = userInfo.getString("nickName");
                        if (StrUtil.isNotBlank(name)) {
                            return name;
                        }
                    }
                } else {
                    log.warn("获取用户信息失败: {}", json != null ? json.getString("msg") : body);
                    return "匿名用户";
                }
            } else {
                log.warn("获取用户信息失败: {}", response.code());
            }
        } catch (IOException e) {
            log.error("获取用户信息异常", e);
        }
        return "匿名用户";
    }

    private void syncReplyMsg() {
        for (SheetWebSocket client : SESSION_MAP.get(gridKey)) {
            if (client != this && client.lastReply != null) {
                this.sendMessage(client.lastReply);
            }
        }
    }

    // 收到消息
    @OnMessage
    public void onMessage(String message, boolean last) {
        if ("rub".equalsIgnoreCase(message)) {
            return;
        }
        // 处理部分消息
        byte[] bytes = putPartialMessage(message);
        if (!last) {
            return;
        }

        removePartialMessage();
        String msg = URLUtil.decode(GzipUtil.uncompress(bytes));
        if (msg == null) {
            return;
        }

        if (lastReply != null && lastReply.getData().equals(msg)) {
            log.debug("重复消息，忽略不处理[{}][{}]:{}", gridKey, username, msg);
            return;
        }
        // 处理操作
        try {
            JSONObject operation = JSON.parseObject(msg);
            if (operation != null) {
                JSONObject result = sheetOperationService.processOperation(operation, gridKey);
                if ("error".equals(result.getString("status"))) {
                    log.error("操作处理失败: {}", result.getString("message"));
                    sendMessage(buildErrorMessage(result.getString("message")));
                    return;
                }
            }
        } catch (Exception e) {
            log.error("接收消息发生异常！[{}][{}]:{}", gridKey, username, msg);
            log.error("", e);
            sendMessage(buildErrorMessage("操作处理异常: " + e.getMessage()));
            return;
        }

        sendMessage(msg);
        broadcast(msg);
    }

    // 连接关闭
    @OnClose
    public void onClose(CloseReason closeReason) {
        if (StrUtil.isBlank(gridKey)) {
            return;
        }
        SESSION_MAP.computeIfPresent(gridKey, (k, v) -> {
            v.remove(this);
            return v;
        });
        log.info("[websocket] 连接断开：id={}，reason={}, 当前连接数={}", username, closeReason, SESSION_MAP.size());
    }

    // 异常
    @OnError
    public void onError(Throwable throwable) {
        log.error("[websocket] 连接异常：", throwable);
        closeSession(CloseReason.CloseCodes.UNEXPECTED_CONDITION, throwable.getMessage());
    }

    // 安全关闭
    private void closeSession(CloseReason.CloseCodes code, String reason) {
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(code, reason));
            }
        } catch (IOException e) {
            log.error("[websocket] 关闭异常: {}", e.getMessage(), e);
        }
    }

    private String getFullMessage(String message) {
        try {
            byte[] partialBytes = (byte[]) session.getUserProperties().get(PARTIAL_MESSAGE_KEY);
            if (partialBytes == null) {
                return URLUtil.decode(GzipUtil.uncompress(message.getBytes(StandardCharsets.ISO_8859_1)));
            }

            byte[] messageBytes = message.getBytes(StandardCharsets.ISO_8859_1);
            byte[] fullBytes = new byte[partialBytes.length + messageBytes.length];
            System.arraycopy(partialBytes, 0, fullBytes, 0, partialBytes.length);
            System.arraycopy(messageBytes, 0, fullBytes, partialBytes.length, messageBytes.length);
            return URLUtil.decode(GzipUtil.uncompress(fullBytes));
        } catch (Exception e) {
            log.error("获取完整消息异常", e);
        }
        removePartialMessage();
        return null;
    }

    private void removePartialMessage() {
        session.getUserProperties().remove(PARTIAL_MESSAGE_KEY);
    }

    private byte[] putPartialMessage(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.ISO_8859_1);
        Map<String, Object> userProperties = session.getUserProperties();
        log.debug("收到部分消息，等待接收完成[{}][{}]", gridKey, username);
        byte[] parts = (byte[]) userProperties.get(PARTIAL_MESSAGE_KEY);
        if (parts != null) {
            byte[] newParts = new byte[parts.length + bytes.length];
            System.arraycopy(parts, 0, newParts, 0, parts.length);
            System.arraycopy(bytes, 0, newParts, parts.length, bytes.length);
            parts = newParts;
        } else {
            parts = bytes;
        }
        userProperties.put(PARTIAL_MESSAGE_KEY, parts);
        return parts;
    }

    private ReplyMessage buildMessage(String data, Integer type) {
        ReplyMessage message = new ReplyMessage();
        message.setType(type);
        message.setId(session.getId());
        message.setUsername(username);
        message.setStatus(0);
        message.setReturnMessage(SUCCESS);
        message.setCreateTime(System.currentTimeMillis());
        message.setData(data);
        return message;
    }

    private ReplyMessage buildErrorMessage(String errorMessage) {
        ReplyMessage message = new ReplyMessage();
        message.setType(1);
        message.setId(session.getId());
        message.setUsername(username);
        message.setStatus(1);
        message.setReturnMessage(FAIL);
        message.setCreateTime(System.currentTimeMillis());
        message.setData(errorMessage);
        return message;
    }

    // 群发消息
    private void broadcast(String data) {
        JSONObject json = JSON.parseObject(data);
        if (json == null) {
            return;
        }
        String t = json.getString("t");
        if (t == null) {
            return;
        }

        ReplyMessage message = buildMessage(data, "mv".equals(t) ? 3 : 2);
        this.lastReply = message;

        Set<SheetWebSocket> clients = SESSION_MAP.get(gridKey);
        for (SheetWebSocket client : clients) {
            if (client != this) {
                client.sendMessage(message);
            }
        }

        message.setData(toolongOmission(message.getData())); // 清除data，避免日志过大
        log.debug("广播消息[{}][{}]: {}", gridKey, username, message);
    }

    private String toolongOmission(String str) {
        if (str != null && str.length() > 1000) {
            return str.substring(0, 500) + " ... " + str.substring(str.length() - 500);
        }
        return str;
    }

    // 给单个 session 发消息
    private synchronized void sendMessage(Object message) {
        if (message == null) {
            return;
        }
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(JSON.toJSONString(message, JSONWriter.Feature.LargeObject));
            }
        } catch (IOException e) {
            log.error("[websocket] 发送消息异常:", e);
            closeSession(CloseReason.CloseCodes.CLOSED_ABNORMALLY, e.getMessage());
        }
    }

    private synchronized void sendMessage(String data) {
        sendMessage(buildMessage(data, 0));
    }
}