package com.example.recordshop.domain.inventory;

/**
 * Goldmine 準拠のコンディション等級。中古 Listing は盤面(Vinyl)とジャケット(Sleeve)を
 * それぞれ独立にこの等級で評価する。
 */
public enum GoldmineGrade {
    MINT,
    NEAR_MINT,
    VERY_GOOD_PLUS,
    VERY_GOOD,
    VERY_GOOD_MINUS,
    GOOD_PLUS,
    GOOD,
    FAIR,
    POOR
}
