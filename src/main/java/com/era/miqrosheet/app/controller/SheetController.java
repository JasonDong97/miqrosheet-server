package com.era.miqrosheet.app.controller;

import com.alibaba.fastjson2.JSONObject;
import com.era.miqrosheet.domain.model.Wb;
import com.era.miqrosheet.domain.model.vo.R;
import com.era.miqrosheet.domain.service.IWbService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@Api(tags = "在线表格相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class SheetController {

    private final IWbService wbService;

    @PostMapping("/load")
    public R<Wb> loadSheets(String gridKey) throws IOException {
        log.info("load gridKey: {}", gridKey);
        return R.ok(wbService.load2(gridKey));
    }

    @PostMapping("/loadsheet")
    public String loadSheetData(String gridKey, String[] index) {
        log.info("loadsheet gridKey: {}, index: {}", gridKey, index);
        JSONObject jsonObject = wbService.loadSheets(gridKey, index);
        return jsonObject.toJSONString();
    }
}
