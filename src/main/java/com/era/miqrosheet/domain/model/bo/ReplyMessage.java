package com.era.miqrosheet.domain.model.bo;

import lombok.Data;

import java.util.Date;

@Data
public class ReplyMessage {
    /**
     * session id
     */
    private String id;
    /**
     * 用户名
     */
    private String username;
    /**
     * 回复类型
     * 1 自己发送的消息, 进行确认, 失败后重新刷新页面
     * 2 多人协同操作,更新表格
     * 3 多人操作不同选区("t": "mv")（用不同颜色显示其他人所操作的选区）
     * 4 批量指令更新
     * 5 失败
     */
    private Integer type;
    /**
     * 原始消息数据
     */
    private String data;
    /**
     * 状态: 0 成功 1 失败 2 退出
     */
    private Integer status;
    /**
     * 消息
     */
    private String message;
    /**
     * 创建时间戳
     */
    private Date createTime;
}