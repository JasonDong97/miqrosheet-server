package com.era.miqrosheet.app.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.era.miqrosheet.domain.service.IWbService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Api(tags = "在线表格相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class SheetController {

    private final IWbService wbService;

    @PostMapping("/load")
    public String loadSheets(HttpServletResponse response, String gridKey) throws IOException {
        JSONArray sheets = wbService.load(gridKey);
        return sheets.toJSONString();
    }

    @PostMapping("/loadsheet")
    public String loadSheetData(String gridKey, String[] index) {
        JSONObject jsonObject = wbService.loadSheets(gridKey, index);
        return jsonObject.toJSONString();
    }
}
