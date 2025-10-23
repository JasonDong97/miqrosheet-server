package com.era.miqrosheet.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.era.miqrosheet.domain.model.WbSheetCelldata;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author dongjingxiang
 * @since 2025-09-29
 */
public interface WbSheetCelldataMapper extends BaseMapper<WbSheetCelldata> {

    List<WbSheetCelldata> selectBySheetIndex(@Param("gridKey") String gridKey, @Param("index") String index);
    
    void deleteBySheetIndexAndPosition(@Param("gridKey") String gridKey, @Param("index") String index, @Param("r") Integer r, @Param("c") Integer c);
    
    boolean insertOrUpdate(WbSheetCelldata celldata);
    
    void deleteRows(@Param("gridKey") String gridKey, @Param("index") String index, @Param("startRow") Integer startRow, @Param("len") Integer len);
    
    void deleteColumns(@Param("gridKey") String gridKey, @Param("index") String index, @Param("startCol") Integer startCol, @Param("len") Integer len);
    
    void updateRowNumbersAfterDelete(@Param("gridKey") String gridKey, @Param("index") String index, @Param("startRow") Integer startRow, @Param("len") Integer len);
    
    void updateColumnNumbersAfterDelete(@Param("gridKey") String gridKey, @Param("index") String index, @Param("startCol") Integer startCol, @Param("len") Integer len);
    
    void updateRowNumbersAfterInsert(@Param("gridKey") String gridKey, @Param("index") String index, @Param("startRow") Integer startRow, @Param("len") Integer len);
    
    void updateColumnNumbersAfterInsert(@Param("gridKey") String gridKey, @Param("index") String index, @Param("startCol") Integer startCol, @Param("len") Integer len);

    WbSheetCelldata selectByUniqueParam(@Param("gridKey") String gridKey, @Param("i") String i, @Param("r") Integer r, @Param("c") Integer c);
}
