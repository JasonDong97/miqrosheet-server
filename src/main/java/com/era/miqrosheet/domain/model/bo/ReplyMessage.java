package com.era.miqrosheet.domain.model.bo;

import lombok.Data;

@Data
public class ReplyMessage {
    /**
     * session id
     */
    private String id;
    /**
     * 回复类型
     * 1 成功或失败
     * 2 更新数据
     * 3 多人操作不同选区("t": "mv")（用不同颜色显示其他人所操作的选区）
     * 4 批量指令更新
     */
    private Integer type;
    /**
     * 用户名
     */
    private String username;
    /**
     * 状态: 0 成功 1 失败 2 退出
     */
    private Integer status;
    /**
     * 消息
     */
    private String message;
    /**
     * 返回消息
     */
    private String returnMessage;
    /**
     * 创建时间戳
     */
    private Long createTime;
    /**
     * 原始消息数据
     */
    private String data;

}