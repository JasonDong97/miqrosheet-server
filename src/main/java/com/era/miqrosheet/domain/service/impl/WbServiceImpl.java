package com.era.miqrosheet.domain.service.impl;

import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.era.miqrosheet.domain.mapper.WbMapper;
import com.era.miqrosheet.domain.mapper.WbSheetCelldataMapper;
import com.era.miqrosheet.domain.mapper.WbSheetMapper;
import com.era.miqrosheet.domain.model.Wb;
import com.era.miqrosheet.domain.model.WbSheetCelldata;
import com.era.miqrosheet.domain.service.IWbService;
import com.era.miqrosheet.infra.helper.RedisHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dongjingxiang
 * @since 2025-09-29
 */
@RequiredArgsConstructor
@Service
public class WbServiceImpl extends ServiceImpl<WbMapper, Wb> implements IWbService {
    private final WbSheetMapper wbSheetMapper;
    private final WbSheetCelldataMapper celldataMapper;
    private final RedisHelper<String, String> redisHelper;

    @Override
    public JSONArray load(String gridKey) {
        var sheets = wbSheetMapper.selectByGridKey(gridKey);
        JSONArray arr = new JSONArray();
        sheets.forEach(sheet -> {
            JSONObject sheetJson = JSON.parseObject(sheet);
            Integer status = sheetJson.getInteger("status");
            if (status != null && status == 1) {
                sheetJson.put("celldata", loadCellData(gridKey, sheetJson.getString("index")));
            }
            arr.add(sheetJson);
        });
        return arr;
    }

    private List<JSONObject> loadCellData(String gridKey, String sheetIndex) {
        List<WbSheetCelldata> celldatas = celldataMapper.selectBySheetIndex(gridKey, sheetIndex);
        return celldatas.stream().map(celldata -> {
            JSONObject cell = new JSONObject();
            cell.put("r", celldata.getR());
            cell.put("c", celldata.getC());
            cell.put("v", JSON.parseObject(celldata.getV()));
            return cell;
        }).collect(Collectors.toList());
    }

    @Override
    public JSONObject loadSheets(String gridKey, String[] index) {
        if (index == null || index.length == 0) {
            return JSONObject.of();
        }
        JSONObject sheetData = new JSONObject();
        Assert.notNull(gridKey, "gridKey not be null!");
        for (String i : index) {
            List<JSONObject> cellData = loadCellData(gridKey, i);
            sheetData.put(i, cellData);
        }
        return sheetData;
    }
}
