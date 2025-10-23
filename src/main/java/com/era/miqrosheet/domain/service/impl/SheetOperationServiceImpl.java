package com.era.miqrosheet.domain.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.era.miqrosheet.domain.mapper.WbMapper;
import com.era.miqrosheet.domain.mapper.WbSheetCelldataMapper;
import com.era.miqrosheet.domain.mapper.WbSheetMapper;
import com.era.miqrosheet.domain.model.Wb;
import com.era.miqrosheet.domain.model.WbSheet;
import com.era.miqrosheet.domain.model.WbSheetCelldata;
import com.era.miqrosheet.domain.service.ISheetOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 表格操作服务实现
 * <p>
 * 该服务负责处理所有在线表格的协同编辑操作，包括：
 * 1. 单元格操作：单个单元格更新、范围单元格更新
 * 2. 配置操作：边框、行高、列宽、隐藏等配置更新
 * 3. 通用保存：冻结、筛选、图表、函数链等高级功能
 * 4. 行列操作：增加、删除行列
 * 5. Sheet操作：新建、复制、删除、切换、隐藏Sheet
 * 6. 表格信息：工作簿名称修改
 * <p>
 * 所有操作都通过WebSocket实时同步给其他用户，确保协同编辑的一致性。
 * 使用MySQL JSON函数优化性能，减少网络传输和内存使用。
 *
 * @author dongjingxiang
 * @since 2025-01-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SheetOperationServiceImpl implements ISheetOperationService {

    private final WbMapper wbMapper;
    private final WbSheetMapper wbSheetMapper;
    private final WbSheetCelldataMapper celldataMapper;

    /**
     * 处理表格操作的主入口方法
     * <p>
     * 根据操作类型(t字段)分发到对应的处理方法：
     * - v: 单个单元格值更新
     * - rv/rv_end: 范围单元格更新
     * - cg: 配置更新(边框、行高、列宽等)
     * - all: 通用保存(冻结、筛选、图表等)
     * - fc: 函数链操作
     * - drc/arc: 行列操作(删除/增加)
     * - fsc/fsr: 筛选操作(清除/恢复)
     * - sha/shc/shd/shre/shr/shs/sh: Sheet操作
     * - na: 工作簿名称修改
     * - c: 图表操作
     *
     * @param op      操作数据，包含操作类型和具体参数
     * @param gridKey 表格唯一标识
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processOperation(JSONObject op, String gridKey) {
        String opt = op.getString("t");
        if ("mv".equals(opt)) {
            // 选区移动操作不涉及数据变更，直接返回成功
            return;
        }

        switch (opt) {
            case "v":
                processCellValueUpdate(op, gridKey);
                break;
            case "rv":
            case "rv_end":
                processRangeCellUpdate(op, gridKey);
                break;
            case "cg":
                processConfigUpdate(op, gridKey);
                break;
            case "all":
                processGeneralSave(op, gridKey);
                break;
            case "fc":
                processFunctionChain(op, gridKey);
                break;
            case "drc":
            case "arc":
                processRowColumnOperation(op, gridKey);
                break;
            case "fsc":
            case "fsr":
                processFilterOperation(op, gridKey);
                break;
            case "sha":
            case "shc":
            case "shd":
            case "shre":
            case "shr":
            case "shs":
            case "sh":
                processSheetOperation(op, gridKey);
                break;
            case "na":
                processWorkbookNameChange(op, gridKey);
                break;
            case "c":
                processChartOperation(op, gridKey);
                break;
            default:
                log.warn("未知操作类型, {}: {}", opt, op);
        }
    }

    /**
     * 处理单个单元格值更新操作 (t: "v")
     * <p>
     * 更新指定位置的单元格值，如果值为null则删除该单元格。
     * 操作数据格式：
     * {
     * "t": "v",
     * "i": "Sheet_xxx",  // Sheet索引
     * "r": 0,            // 行号
     * "c": 1,            // 列号
     * "v": {             // 单元格值对象
     * "v": 233,
     * "ct": {"fa": "General", "t": "n"},
     * "m": "233"
     * }
     * }
     *
     * @param operation 操作数据
     * @param gridKey   表格唯一标识
     */
    @Override
    public void processCellValueUpdate(JSONObject operation, String gridKey) {
        String sheetIndex = operation.getString("i");
        Integer r = operation.getInteger("r");
        Integer c = operation.getInteger("c");
        WbSheetCelldata celldata = celldataMapper.selectByUniqueParam(gridKey, sheetIndex, r, c);
        if (celldata == null) {
            celldata = new WbSheetCelldata();
        }

        Object v = operation.get("v");
        if (v == null && celldata.getId() != null) {
            celldataMapper.deleteById(celldata.getId());
            return;
        }

        celldata.setGridKey(gridKey);
        celldata.setSheetIndex(sheetIndex);
        celldata.setR(r);
        celldata.setC(c);
        celldata.setV(JSON.toJSONString(v));
        if (celldata.getId() == null) {
            celldataMapper.insert(celldata);
        } else {
            celldataMapper.updateById(celldata);
        }
    }

    /**
     * 处理范围单元格更新操作 (t: "rv")
     * <p>
     * 批量更新指定范围内的单元格值，用于处理大量单元格同时更新的场景。
     * 操作数据格式：
     * {
     * "t": "rv",
     * "i": "Sheet_xxx",  // Sheet索引
     * "v": [             // 二维数组，单元格数据
     * [{"v": 3, "ct": {...}, "m": "3"}],
     * [{"v": 4, "ct": {...}, "m": "4"}]
     * ],
     * "range": {         // 范围信息
     * "row": [1, 2],   // 行范围
     * "column": [1, 1] // 列范围
     * }
     * }
     *
     * @param operation 操作数据
     * @param gridKey   表格唯一标识
     */
    @Override
    public void processRangeCellUpdate(JSONObject operation, String gridKey) {
        String sheetIndex = operation.getString("i");
        JSONArray v = operation.getJSONArray("v");
        JSONObject range = operation.getJSONObject("range");

        if (v == null || range == null) {
            return;
        }

        JSONArray rowRange = range.getJSONArray("row");
        JSONArray columnRange = range.getJSONArray("column");

        int startRow = rowRange.getInteger(0);
        int startCol = columnRange.getInteger(0);

        // 批量更新单元格数据
        for (int i = 0; i < v.size(); i++) {
            JSONArray rowData = v.getJSONArray(i);
            if (rowData != null) {
                for (int j = 0; j < rowData.size(); j++) {
                    Object cellValue = rowData.get(j);
                    int r = startRow + i;
                    int c = startCol + j;
                    WbSheetCelldata celldata = celldataMapper.selectByUniqueParam(gridKey, sheetIndex, r, c);
                    if (celldata == null) {
                        celldata = new WbSheetCelldata();
                    }
                    celldata.setSheetIndex(sheetIndex);
                    celldata.setGridKey(gridKey);
                    celldata.setR(r);
                    celldata.setC(c);
                    if (cellValue == null && celldata.getId() != null) {
                        celldataMapper.deleteById(celldata.getId());
                    } else {
                        celldata.setV(JSON.toJSONString(cellValue));
                        if (celldata.getId() == null) {
                            celldataMapper.insert(celldata);
                        } else {
                            celldataMapper.updateById(celldata);
                        }
                    }

                }
            }
        }
    }

    /**
     * 处理配置更新操作 (t: "cg")
     * <p>
     * 更新Sheet的配置信息，如边框、行高、列宽、隐藏等设置。
     * 使用MySQL JSON函数直接更新config字段，提高性能。
     * <p>
     * 支持的配置类型(k字段)：
     * - borderInfo: 边框设置
     * - rowhidden: 行隐藏设置
     * - colhidden: 列隐藏设置
     * - rowlen: 行高设置
     * - columnlen: 列宽设置
     * <p>
     * 操作数据格式：
     * {
     * "t": "cg",
     * "i": "Sheet_xxx",  // Sheet索引
     * "k": "borderInfo", // 配置类型
     * "v": [...]         // 配置值
     * }
     *
     * @param operation 操作数据
     * @param gridKey   表格唯一标识
     */
    @Override
    public void processConfigUpdate(JSONObject operation, String gridKey) {
        String sheetIndex = operation.getString("i");
        Object v = operation.get("v");
        String k = operation.getString("k");

        // 使用MySQL JSON函数直接更新config字段
        wbSheetMapper.updateConfigField(gridKey, sheetIndex, k, JSON.toJSONString(v));
    }

    @Override
    public void processGeneralSave(JSONObject operation, String gridKey) {
        String sheetIndex = operation.getString("i");
        Object v = operation.get("v");
        String k = operation.getString("k");

        // 使用MySQL JSON函数直接更新json_data字段
        wbSheetMapper.updateJsonField(gridKey, sheetIndex, k, JSON.toJSONString(v));
    }

    @Override
    public void processFunctionChain(JSONObject operation, String gridKey) {
        String sheetIndex = operation.getString("i");
        String v = operation.getString("v");
        String op = operation.getString("op");
        Integer pos = operation.getInteger("pos");

        // 使用MySQL JSON函数直接操作calcChain数组
        switch (op) {
            case "add":
                wbSheetMapper.addToCalcChain(gridKey, sheetIndex, v);
                break;
            case "update":
                if (pos != null) {
                    wbSheetMapper.updateCalcChainAt(gridKey, sheetIndex, pos, v);
                }
                break;
            case "del":
                if (pos != null) {
                    wbSheetMapper.removeFromCalcChainAt(gridKey, sheetIndex, pos);
                }
                break;
        }
    }

    @Override
    public void processRowColumnOperation(JSONObject operation, String gridKey) {
        String operationType = operation.getString("t");
        String sheetIndex = operation.getString("i");
        JSONObject v = operation.getJSONObject("v");
        String rc = operation.getString("rc");

        if ("drc".equals(operationType)) {
            // 删除行或列
            Integer index = v.getInteger("index");
            Integer len = v.getInteger("len");

            if ("r".equals(rc)) {
                // 删除行
                celldataMapper.deleteRows(gridKey, sheetIndex, index, len);
                // 更新其他行的行号
                celldataMapper.updateRowNumbersAfterDelete(gridKey, sheetIndex, index, len);
            } else if ("c".equals(rc)) {
                // 删除列
                celldataMapper.deleteColumns(gridKey, sheetIndex, index, len);
                // 更新其他列的列号
                celldataMapper.updateColumnNumbersAfterDelete(gridKey, sheetIndex, index, len);
            }
        } else if ("arc".equals(operationType)) {
            // 增加行或列
            Integer index = v.getInteger("index");
            Integer len = v.getInteger("len");
            JSONArray data = v.getJSONArray("data");

            if ("r".equals(rc)) {
                // 增加行
                celldataMapper.updateRowNumbersAfterInsert(gridKey, sheetIndex, index, len);
                if (data != null && !data.isEmpty()) {
                    // 插入新行数据
                    for (int i = 0; i < data.size(); i++) {
                        JSONArray rowData = data.getJSONArray(i);
                        if (rowData != null) {
                            for (int j = 0; j < rowData.size(); j++) {
                                Object cellValue = rowData.get(j);
                                if (cellValue != null) {
                                    WbSheetCelldata celldata = new WbSheetCelldata();
                                    celldata.setSheetIndex(sheetIndex);
                                    celldata.setR(index + i);
                                    celldata.setC(j);
                                    celldata.setV(JSON.toJSONString(cellValue));
                                    celldataMapper.insert(celldata);
                                }
                            }
                        }
                    }
                }
            } else if ("c".equals(rc)) {
                // 增加列
                celldataMapper.updateColumnNumbersAfterInsert(gridKey, sheetIndex, index, len);
                if (data != null && !data.isEmpty()) {
                    // 插入新列数据
                    for (int i = 0; i < data.size(); i++) {
                        JSONArray rowData = data.getJSONArray(i);
                        if (rowData != null) {
                            for (int j = 0; j < rowData.size(); j++) {
                                Object cellValue = rowData.get(j);
                                if (cellValue != null) {
                                    WbSheetCelldata celldata = new WbSheetCelldata();
                                    celldata.setSheetIndex(sheetIndex);
                                    celldata.setR(i);
                                    celldata.setC(index + j);
                                    celldata.setV(JSON.toJSONString(cellValue));
                                    celldataMapper.insert(celldata);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void processFilterOperation(JSONObject operation, String gridKey) {
        String operationType = operation.getString("t");
        String sheetIndex = operation.getString("i");

        if ("fsc".equals(operationType)) {
            // 清除筛选
            wbSheetMapper.clearFilter(gridKey, sheetIndex);
        } else if ("fsr".equals(operationType)) {
            // 恢复筛选
            JSONObject v = operation.getJSONObject("v");
            if (v != null) {
                wbSheetMapper.restoreFilter(gridKey, sheetIndex,
                        JSON.toJSONString(v.get("filter")),
                        JSON.toJSONString(v.get("filter_select")));
            }
        }
    }

    @Override
    public void processSheetOperation(JSONObject operation, String gridKey) {
        String operationType = operation.getString("t");

        switch (operationType) {
            case "sha":
                // 新建sheet
                processNewSheet(operation, gridKey);
                break;
            case "shc":
                // 复制sheet
                processCopySheet(operation, gridKey);
                break;
            case "shd":
                // 删除sheet
                processDeleteSheet(operation, gridKey);
                break;
            case "shre":
                // 恢复sheet
                processRestoreSheet(operation, gridKey);
                break;
            case "shr":
                // 调整sheet位置
                processReorderSheets(operation, gridKey);
                break;
            case "shs":
                // 切换sheet
                processSwitchSheet(operation, gridKey);
                break;
            case "sh":
                // 隐藏/显示sheet
                processHideShowSheet(operation, gridKey);
                break;
        }
    }

    @Override
    public void processWorkbookNameChange(JSONObject operation, String gridKey) {
        String name = operation.getString("v");

        Wb wb = wbMapper.selectByGridKey(gridKey);
        if (wb != null) {
            wb.setName(name);
            wbMapper.updateById(wb);
        }
    }

    @Override
    public void processChartOperation(JSONObject operation, String gridKey) {
        String sheetIndex = operation.getString("i");
        String op = operation.getString("op");
        JSONObject v = operation.getJSONObject("v");

        // 获取当前sheet的json_data
        WbSheet sheet = wbSheetMapper.selectByGridKeyAndIndex(gridKey, sheetIndex);
        if (sheet == null) {
            log.warn("Sheet不存在: gridKey={}, sheetIndex={}", gridKey, sheetIndex);
            return;
        }

        JSONObject sheetData = JSON.parseObject(sheet.getJsonData());
        if (sheetData == null) {
            sheetData = new JSONObject();
        }

        JSONArray chart = sheetData.getJSONArray("chart");
        if (chart == null) {
            chart = new JSONArray();
        }

        switch (op) {
            case "add":
                chart.add(v);
                break;
            case "xy":
                // 移动图表位置
                updateChartPosition(chart, v);
                break;
            case "wh":
                // 缩放图表
                updateChartSize(chart, v);
                break;
            case "update":
                // 修改图表配置
                updateChartConfig(chart, v);
                break;
        }

        sheetData.put("chart", chart);

        // 更新数据库
        sheet.setJsonData(sheetData.toJSONString());
        wbSheetMapper.updateById(sheet);
    }

    // 私有方法实现具体的Sheet操作
    private void processNewSheet(JSONObject operation, String gridKey) {
        JSONObject v = operation.getJSONObject("v");
        if (v == null) return;

        WbSheet sheet = new WbSheet();
        sheet.setGridKey(gridKey);
        sheet.setJsonData(v.toJSONString());
        wbSheetMapper.insert(sheet);
    }

    private void processCopySheet(JSONObject operation, String gridKey) {
        String copyIndex = operation.getJSONObject("v").getString("copyindex");
        String newName = operation.getJSONObject("v").getString("name");

        // 获取要复制的sheet
        WbSheet sourceSheet = wbSheetMapper.selectByGridKeyAndIndex(gridKey, copyIndex);
        if (sourceSheet != null) {
            WbSheet newSheet = new WbSheet();
            newSheet.setGridKey(gridKey);
            newSheet.setJsonData(sourceSheet.getJsonData());
            wbSheetMapper.insert(newSheet);
        }
    }

    private void processDeleteSheet(JSONObject operation, String gridKey) {
        Integer deleteIndex = operation.getJSONObject("v").getInteger("deleIndex");
        wbSheetMapper.deleteByOrder(gridKey, deleteIndex);
    }

    private void processRestoreSheet(JSONObject operation, String gridKey) {
        String restoreIndex = operation.getJSONObject("v").getString("reIndex");
        wbSheetMapper.restoreByIndex(gridKey, restoreIndex);
    }

    private void processReorderSheets(JSONObject operation, String gridKey) {
        JSONObject v = operation.getJSONObject("v");
        for (Map.Entry<String, Object> entry : v.entrySet()) {
            String sheetIndex = entry.getKey();
            Integer order = (Integer) entry.getValue();
            wbSheetMapper.updateOrderByIndex(gridKey, sheetIndex, order);
        }
    }

    private void processSwitchSheet(JSONObject operation, String gridKey) {
        Integer targetIndex = operation.getInteger("v");
        // 先设置所有sheet为未激活
        wbSheetMapper.setAllInactive(gridKey);
        // 再设置目标sheet为激活
        wbSheetMapper.setActiveByOrder(gridKey, targetIndex);
    }

    private void processHideShowSheet(JSONObject operation, String gridKey) {
        String sheetIndex = operation.getString("i");
        Integer v = operation.getInteger("v");
        String op = operation.getString("op");
        Integer cur = operation.getInteger("cur");

        if ("hide".equals(op)) {
            // 隐藏sheet
            wbSheetMapper.setHideByIndex(gridKey, sheetIndex, v == 1);
            if (v == 1) {
                wbSheetMapper.setInactiveByIndex(gridKey, sheetIndex);
                if (cur != null) {
                    wbSheetMapper.setActiveByOrder(gridKey, cur);
                }
            }
        } else if ("show".equals(op)) {
            // 显示sheet
            wbSheetMapper.setHideByIndex(gridKey, sheetIndex, false);
            wbSheetMapper.setActiveByIndex(gridKey, sheetIndex);
            // 设置其他sheet为未激活
            wbSheetMapper.setAllInactiveExcept(gridKey, sheetIndex);
        }
    }

    private void updateChartPosition(JSONArray chart, JSONObject v) {
        String chartId = v.getString("chart_id");
        for (int i = 0; i < chart.size(); i++) {
            JSONObject chartItem = chart.getJSONObject(i);
            if (chartId.equals(chartItem.getString("chart_id"))) {
                chartItem.put("left", v.get("left"));
                chartItem.put("top", v.get("top"));
                break;
            }
        }
    }

    private void updateChartSize(JSONArray chart, JSONObject v) {
        String chartId = v.getString("chart_id");
        for (int i = 0; i < chart.size(); i++) {
            JSONObject chartItem = chart.getJSONObject(i);
            if (chartId.equals(chartItem.getString("chart_id"))) {
                chartItem.put("left", v.get("left"));
                chartItem.put("top", v.get("top"));
                chartItem.put("width", v.get("width"));
                chartItem.put("height", v.get("height"));
                break;
            }
        }
    }

    private void updateChartConfig(JSONArray chart, JSONObject v) {
        String chartId = v.getString("chart_id");
        for (int i = 0; i < chart.size(); i++) {
            JSONObject chartItem = chart.getJSONObject(i);
            if (chartId.equals(chartItem.getString("chart_id"))) {
                chart.set(i, v);
                break;
            }
        }
    }
}
