package com.era.miqrosheet.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.era.miqrosheet.domain.model.WbSheet;
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
public interface WbSheetMapper extends BaseMapper<WbSheet> {

   List<String> selectByGridKey(@Param("gridKey") String gridKey);
   
   WbSheet selectByGridKeyAndIndex(@Param("gridKey") String gridKey, @Param("index") String index);
   
   void deleteByIndex(@Param("gridKey") String gridKey, @Param("index") String index);
   
   void restoreByIndex(@Param("gridKey") String gridKey, @Param("index") String index);
   
   void updateOrderByIndex(@Param("gridKey") String gridKey, @Param("index") String index, @Param("order") Integer order);
   
   void setAllInactive(@Param("gridKey") String gridKey);
   
   void setActiveByIndex(@Param("gridKey") String gridKey, @Param("index") String index);
   
   void setInactiveByIndex(@Param("gridKey") String gridKey, @Param("index") String index);
   
   void setHideByIndex(@Param("gridKey") String gridKey, @Param("index") String index, @Param("hide") Boolean hide);
   
   void setAllInactiveExcept(@Param("gridKey") String gridKey, @Param("index") String index);
   
   void updateJsonField(@Param("gridKey") String gridKey, @Param("index") String index, @Param("key") String key, @Param("value") String value);
   
   void updateConfigField(@Param("gridKey") String gridKey, @Param("index") String index, @Param("key") String key, @Param("value") String value);
   
   void addToCalcChain(@Param("gridKey") String gridKey, @Param("index") String index, @Param("value") String value);
   
   void updateCalcChainAt(@Param("gridKey") String gridKey, @Param("index") String index, @Param("pos") Integer pos, @Param("value") String value);
   
   void removeFromCalcChainAt(@Param("gridKey") String gridKey, @Param("index") String index, @Param("pos") Integer pos);
   
   void clearFilter(@Param("gridKey") String gridKey, @Param("index") String index);
   
   void restoreFilter(@Param("gridKey") String gridKey, @Param("index") String index, @Param("filter") String filter, @Param("filterSelect") String filterSelect);

   void copyByIndex(@Param("gridKey") String gridKey, @Param("copyIndex") String copyIndex, @Param("newName") String newName, @Param("index") String index);

}
