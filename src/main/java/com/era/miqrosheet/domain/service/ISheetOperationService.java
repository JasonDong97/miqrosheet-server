package com.era.miqrosheet.domain.service;

import com.alibaba.fastjson2.JSONObject;

/**
 * 表格操作服务接口
 * 
 * @author dongjingxiang
 * @since 2025-01-27
 */
public interface ISheetOperationService {

    /**
     * 处理表格操作
     * 
     * @param operation 操作数据
     * @param gridKey 表格标识
     * @return 处理结果
     */
    void processOperation(JSONObject operation, String gridKey);

    /**
     * 处理单元格值更新 (t: "v")
     */
    void processCellValueUpdate(JSONObject operation, String gridKey);

    /**
     * 处理范围单元格更新 (t: "rv")
     */
    void processRangeCellUpdate(JSONObject operation, String gridKey);

    /**
     * 处理配置更新 (t: "cg")
     */
    void processConfigUpdate(JSONObject operation, String gridKey);

    /**
     * 处理通用保存 (t: "all")
     */
    void processGeneralSave(JSONObject operation, String gridKey);

    /**
     * 处理函数链操作 (t: "fc")
     */
    void processFunctionChain(JSONObject operation, String gridKey);

    /**
     * 处理行列操作 (t: "drc", "arc")
     */
    void processRowColumnOperation(JSONObject operation, String gridKey);

    /**
     * 处理筛选操作 (t: "fsc", "fsr")
     */
    void processFilterOperation(JSONObject operation, String gridKey);

    /**
     * 处理Sheet操作 (t: "sha", "shc", "shd", "shre", "shr", "shs", "sh")
     */
    void processSheetOperation(JSONObject operation, String gridKey);

    /**
     * 处理表格信息更改 (t: "na")
     */
    void processWorkbookNameChange(JSONObject operation, String gridKey);

    /**
     * 处理图表操作 (t: "c")
     */
    void processChartOperation(JSONObject operation, String gridKey);
}
