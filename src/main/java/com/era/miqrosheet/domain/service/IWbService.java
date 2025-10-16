package com.era.miqrosheet.domain.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.era.miqrosheet.domain.model.Wb;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author dongjingxiang
 * @since 2025-09-29
 */
public interface IWbService extends IService<Wb> {

    JSONArray load(String gridKey);

    Wb load2(String gridKey);

    JSONObject loadSheets(String gridKey, String[] index);
}
