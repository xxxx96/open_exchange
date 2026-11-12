package com.openexchange.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 游标分页结果：nextCursor 为上一页最后一条的 tradeId，hasMore 标识是否还有下一页。
 */
@Data
@Builder
@Schema(name = "TradePageVO", description = "成交记录游标分页")
public class TradePageVO {

    @Schema(description = "成交记录列表")
    private List<TradeVO> list;

    @Schema(description = "下一页游标（上一页最后一条 tradeId），无下一页为 null", example = "t_20261106_000088")
    private String nextCursor;

    @Schema(description = "是否还有更多数据", example = "true")
    private Boolean hasMore;
}
