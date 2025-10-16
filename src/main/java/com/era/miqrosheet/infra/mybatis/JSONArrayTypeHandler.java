package com.era.miqrosheet.infra.mybatis;

import com.alibaba.fastjson2.JSONArray;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedJdbcTypes(JdbcType.VARCHAR)   // 默认映射 VARCHAR / TEXT
@MappedTypes(JSONArray.class)       // 映射到 Java 的 JSONObject
public class JSONArrayTypeHandler extends BaseTypeHandler<JSONArray> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JSONArray parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.toJSONString());
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return safeParse(json);
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return safeParse(json);
    }

    @Override
    public JSONArray getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return safeParse(json);
    }

    /**
     * 安全解析 JSON：
     * - 合法 JSON：正常返回 JSONArray
     * - 非 JSON 普通字符串：返回 {"value":"原始值"}
     * - null / 空串：返回 null
     */
    private JSONArray safeParse(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }

        String text = str.trim();
        try {
            if ((text.startsWith("[") && text.endsWith("]"))) {
                return JSONArray.parse(text);
            }
        } catch (Exception ignore) {
            // 忽略解析错误，进入兜底逻辑
        }

        // 兜底封装
        JSONArray obj = new JSONArray();
        obj.add(str);
        return obj;
    }
}