package flcat.gogiyo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

// 외부 APi의 공통 응답 형식을 표현하는 dto
@Data
@NoArgsConstructor
public class ExternalApiResponse {

    private String condition;

    // 실제 데이터가 담기는 부분
    // API 명세서에 따라 이 필드의 내부 구조가 다양할 수 있음을 인지하고 설계
    // 예를 들어, 데이터가 없을 경우 이 'data' 필드가 null로 올 수도 있고,
    // 내부의 'items' 필드가 null이거나 빈 리스트일 수도 있음.

    @JsonProperty("data")
    private DataContent data;

    // 'data' 필드 내부의 상세 구조를 나타내는 중첩 클레스
    // 이 또한 API 명세서에 따라 유연하게 수정
    @Data
    @NoArgsConstructor
    public static class DataContent {

        @JsonProperty("item")
        private List<ItemPriceInfo> items;

        // 데이터 조회 실패시 또는 에러 메세지 등이 있을 경우 사용될 수 있는 필드
        private String message;
        private String errorCode;
    }
}
