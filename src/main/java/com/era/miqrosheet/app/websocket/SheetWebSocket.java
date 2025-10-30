package com.era.miqrosheet.app.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.era.miqrosheet.domain.model.MsgType;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

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
 */
@Slf4j
@Component
@ServerEndpoint(value = "/websocket")
public class SheetWebSocket {

    // 用线程安全集合存储所有连接
    private static final Map<String, List<SheetWebSocket>> SOCKET_MAP = new ConcurrentHashMap<>();
    private static final String PARTIAL_MESSAGE_KEY = "partialMessage";
    // 所有最后移动操作的操作的 map, {key: gridKey, value:{key: 用户名, value: 回复消息}}
    private static final Map<String, Map<String, ReplyMessage>> MV_OP_MAP = new ConcurrentHashMap<>();
    // 相同 gridKey 用同一把锁
    private static final ConcurrentHashMap<String, ReentrantLock> LOCK_MAP = new ConcurrentHashMap<>();
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
        this.username = getUserName(CollUtil.get(parameters.get("t"), 0));

        if (StrUtil.isBlank(gridKey)) {
            log_info("连接参数错误，gridKey不能为空");
            send(buildMsg(5, "连接参数错误，gridKey不能为空"));
            close(CloseReason.CloseCodes.CANNOT_ACCEPT, "gridKey不能为空");
            return;
        }

        // 添加客户端
        add();
        Map<String, ReplyMessage> mvMap = MV_OP_MAP.get(gridKey);
        if (CollUtil.isNotEmpty(mvMap)) {
            mvMap.values().forEach(this::send);
        }
    }

    /**
     * 发送消息
     *
     * @param msg 回复消息
     */
    public void send(ReplyMessage msg) {
        if (msg == null) {
            return;
        }
        if (session == null || !session.isOpen()) {
            return;
        }
        ReentrantLock lock = LOCK_MAP.computeIfAbsent(gridKey, k -> new ReentrantLock());
        lock.lock();
        try {
            session.getBasicRemote().sendText(JSON.toJSONString(msg, JSONWriter.Feature.LargeObject));
        } catch (Exception e) {
            log_error("发送消息异常:{}", ExceptionUtil.getMessage(e));
        } finally {
            lock.unlock();
        }
    }

    // 收到消息
    @OnMessage
    public void onMessage(String message, boolean last) {
        String data = preProcess(message, last);
        if (data == null) {
            return;
        }

        // 处理操作
        try {
            JSONObject op = JSON.parseObject(data);
            sheetOperationService.processOperation(op, gridKey);
            MsgType msgType = MsgType.UPDATE;
            String t = op.getString("t");
            if ("mv".equals(t)) {
                msgType = MsgType.MV;
                Map<String, ReplyMessage> replyMap = MV_OP_MAP.get(gridKey);
                if (replyMap == null) {
                    replyMap = new ConcurrentHashMap<>();
                }
                replyMap.put(username, buildMsg(MsgType.MV.getType(), data));
                MV_OP_MAP.put(gridKey, replyMap);
            }
            sendAll(msgType, data);
        } catch (Exception e) {
            log_error("处理异常:{}", data);
            log.error("", e);
            send(buildMsg(5, e.getMessage()));
        }
    }

    /**
     * 发送给所有会话
     *
     * @param msgType 消息类型
     * @param data    消息数据
     */
    private void sendAll(MsgType msgType, String data) {
        List<SheetWebSocket> sockets = SOCKET_MAP.get(gridKey);
        if (CollUtil.isNotEmpty(sockets)) {
            for (SheetWebSocket socket : sockets) {
                ReplyMessage msg = buildMsg(socket == this ? 1 : msgType.getType(), data);
                socket.send(msg);
            }
        }
    }

    public ReplyMessage buildMsg(Integer type, String data) {
        ReplyMessage msg = new ReplyMessage();
        msg.setId(session.getId());
        msg.setUsername(username);
        msg.setType(type);
        msg.setData(data);
        msg.setCreateTime(new Date());
        return msg;
    }
    // 连接关闭

    /**
     * 预处理
     *
     * @param message 原始消息
     * @param last    是否是最后消息
     * @return 预处理完成的消息数据
     */
    private String preProcess(String message, boolean last) {
        if ("rub".equalsIgnoreCase(message)) {
            return null;
        }

        // 处理部分消息
        byte[] bytes = appendMessage(message, last);
        if (bytes == null) {
            return null;
        }

        String d = URLUtil.decode(GzipUtil.uncompress(bytes));
        if (d == null) {
            return null;
        }

        if (lastReply != null && lastReply.getData().equals(d)) {
            log.debug("重复消息，忽略不处理[{}][{}]:{}", gridKey, username, d);
            return null;
        }
        return d;
    }

    @OnClose
    public void onClose() {
        remove();
        removeMv();
    }


    @OnError
    public void onError(Throwable t) {
        log.error("[websocket] 连接异常：{}", t.getMessage());
        // close(CloseReason.CloseCodes.UNEXPECTED_CONDITION, t.getMessage());
    }

    private void close(CloseReason.CloseCodes code, String reason) {
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(code, reason));
            }
        } catch (IOException e) {
            log.error("[websocket] 关闭异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理部分消息拼接
     *
     * @param message 消息
     * @return 拼接后的消息
     */
    private byte[] appendMessage(String message, boolean last) {
        byte[] part = message.getBytes(StandardCharsets.ISO_8859_1);
        Map<String, Object> userProperties = session.getUserProperties();
        log.debug("收到部分消息，等待接收完成[{}][{}]", gridKey, username);
        byte[] parts = (byte[]) userProperties.get(PARTIAL_MESSAGE_KEY);
        if (parts != null) {
            byte[] newParts = new byte[parts.length + part.length];
            System.arraycopy(parts, 0, newParts, 0, parts.length);
            System.arraycopy(part, 0, newParts, parts.length, part.length);
            parts = newParts;
        } else {
            parts = part;
        }
        userProperties.put(PARTIAL_MESSAGE_KEY, parts);
        if (last) {
            session.getUserProperties().remove(PARTIAL_MESSAGE_KEY);
            return parts;
        }
        return null;
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
                            return name + "-" + session.getId();
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

    private void add() {
        List<SheetWebSocket> clients = SOCKET_MAP.get(gridKey);
        if (clients == null) {
            clients = new CopyOnWriteArrayList<>();
        }
        if (!clients.contains(this)) {
            clients.add(this);
        }
        SOCKET_MAP.put(gridKey, clients);
        log_info("新连接当前表格协同 {} 人。", clients.size());
    }

    private void remove() {
        if (gridKey == null) {
            return;
        }
        List<SheetWebSocket> sockets = SOCKET_MAP.get(gridKey);
        if (sockets == null) {
            sockets = new CopyOnWriteArrayList<>();
        }
        sockets.remove(this);
        log_info("断开连接, 剩余协同编辑人数：{}", sockets.size());
    }

    private void removeMv() {
        if (gridKey == null) {
            return;
        }
        Map<String, ReplyMessage> map = MV_OP_MAP.get(gridKey);
        if (map == null) {
            map = new ConcurrentHashMap<>();
        }
        map.remove(username);
    }

    private void log_info(String str, Object... args) {
        log.info("[{}][{}] => {}", gridKey, username, StrUtil.format(str, args));
    }

    private void log_error(String str, Object... args) {
        log.error("[{}][{}] => {}", gridKey, username, StrUtil.format(str, args));
    }


}