package com.openexchange.spot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 对外单号日序列表 Mapper：订单号 o_yyyyMMdd_序号、成交号 t_yyyyMMdd_序号。
 */
@Mapper
public interface SpotSerialMapper {

    /**
     * 原子取号：当日该业务类型序号 +1（upsert，无需先查后插）。
     */
    long nextSerial(@Param("bizDate") LocalDate bizDate, @Param("bizType") String bizType);
}
