package flcat.gogiyo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

// 외부 API에서 받아올 농산물 가격 상세 정보를 담는 DTO
@Data
@NoArgsConstructor // Jackson JSON 역직렬화를 위해 기본 생성자 필요
public class ItemPriceInfo {
    @JsonProperty("item_name")
    private String itemName;
    @JsonProperty("itemcode")
    private String itemCode;
    @JsonProperty("kind_name")
    private String kindName;
    @JsonProperty("kindcode")
    private String kindCode;
    private String rank;
    private String unit;
    private String day1;
    private String dpr1; // 조회일자 가격
    private String day2;
    private String dpr2; // 1일 전 가격
    private String day3;
    private String dpr3; // 1주일 전 가격
    private String day4;
    private String dpr4; // 2주일 전 가격
    private String day5;
    private String dpr5; // 1개월 전 가격
    private String day6;
    private String dpr6; // 1년 전 가격
    private String day7;
    private String dpr7; // 평년 가격
}