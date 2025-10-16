package com.era.miqrosheet.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author dongjingxiang
 * @since 2025-09-29
 */
@Getter
@Setter
@ToString
@TableName("wb_sheet")
@ApiModel(value = "WbSheet对象", description = "")
public class WbSheet implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Integer id;

    private String gridKey;

    private String name;

    private String index;

    private Boolean status;

    private Integer order;

    private String jsonData;

    private String color;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
