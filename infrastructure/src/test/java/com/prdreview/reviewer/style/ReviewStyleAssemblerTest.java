package com.prdreview.reviewer.style;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.style.assembler.ReviewStyleAssembler;
import com.prdreview.reviewer.style.model.ReviewStyle;
import com.prdreview.reviewer.style.model.StyleRule;
import com.prdreview.reviewer.style.po.ReviewStylePO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReviewStyleAssembler 单元测试：rules JSON 序列化 / 反序列化。
 */
@DisplayName("ReviewStyleAssembler 单元测试")
class ReviewStyleAssemblerTest {

    @Test
    @DisplayName("toPO + toDomain 往返保留所有字段")
    void roundTrip_preservesRules() {
        ReviewStyle origin = ReviewStyle.reconstruct(
            10L, "标准", "📋", "常规",
            List.of(new StyleRule("L1", "C1"), new StyleRule("L2", "C2"),
                    new StyleRule("L3", "C3"), new StyleRule("L4", "C4")),
            true, true, 5, 3, 0, null, null
        );
        ReviewStylePO po = ReviewStyleAssembler.toPO(origin);
        assertThat(po.getRules()).contains("\"label\":\"L1\"").contains("\"content\":\"C4\"");
        assertThat(po.getIsDefault()).isTrue();

        ReviewStyle back = ReviewStyleAssembler.toDomain(po);
        assertThat(back.getId()).isEqualTo(10L);
        assertThat(back.getRules()).hasSize(4);
        assertThat(back.getRules().get(0).label()).isEqualTo("L1");
        assertThat(back.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("toDomain — 非法 JSON 抛 STYLE_RULE_INVALID")
    void toDomain_invalidJson() {
        ReviewStylePO po = new ReviewStylePO();
        po.setId(1L);
        po.setName("X");
        po.setRules("not-a-json[[[");
        po.setEnabled(true);
        po.setIsDefault(false);
        po.setSortOrder(0);
        po.setVersion(1);
        po.setDeleted(0);

        assertThatThrownBy(() -> ReviewStyleAssembler.toDomain(po))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_RULE_INVALID);
    }

    @Test
    @DisplayName("toDomain — 空 PO 返回 null")
    void toDomain_null() {
        assertThat(ReviewStyleAssembler.toDomain(null)).isNull();
    }
}
