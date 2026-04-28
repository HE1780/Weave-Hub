package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;

public interface PromotionMaterializer {
    SourceType supportedSourceType();
    MaterializationResult materialize(PromotionRequest request);
}
