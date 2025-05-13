package flcat.gogiyo.controller; // 실제 패키지 경로

import flcat.gogiyo.dto.ItemPriceInfo;
import flcat.gogiyo.service.LivestockProductPriceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections; // Collections.emptyList() 사용 위해
import java.util.List;

@Tag(name = "축산물 가격 정보 API", description = "외부 API 통해 전국 도매 축산물(소, 돼지, 닭고기) 가격 정보 조회.")
@RestController
@RequestMapping("/api/v1/livestock-prices") // 축산물 특화 경로
@RequiredArgsConstructor // Lombok: final 필드 생성자 주입
@Slf4j
public class LivestockPriceController {

    private final LivestockProductPriceService livestockPriceService;

    // --- 범용 축산물 조회 엔드포인트 ---
    // 축산물 카테고리 내 다양한 조건 조합 조회용.
    // itemCategoryCode는 "500"(축산물)으로 서비스 단에서 처리 가정.
    @Operation(summary = "축산물 가격 정보 조건 조회",
        description = "다양한 조건(구분, 지역, 날짜 등)으로 축산물 가격 정보 조회. 부류는 축산물 고정.",
        responses = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ItemPriceInfo.class))),
            @ApiResponse(responseCode = "204", description = "데이터 없음"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
        })
    @GetMapping("/query")
    public Mono<ResponseEntity<List<ItemPriceInfo>>> queryLivestockPrices(
        @Parameter(description = "구분 (01:소매, 02:도매). 기본: 도매(02).", example = "02")
        @RequestParam(required = false) String productClsCode,

        @Parameter(description = "조회 지역 코드 (예: 1101 서울). 기본: 전국.", example = "1101")
        @RequestParam(required = false) String countryCode,

        @Parameter(description = "조회 기준 날짜 (YYYY-MM-DD). 기본: 최근일.", example = "2025-05-13")
        @RequestParam(required = false) String regDay, // 날짜 형식 검증은 서비스 단에서.

        @Parameter(description = "kg단위 환산여부 (Y/N). 기본: N.", example = "N")
        @RequestParam(required = false) String convertKgYn) {

        // itemCategoryCode는 사용자 입력 X. 이 컨트롤러는 축산물 전용.
        // 서비스 호출 시 itemCategoryCode: null 전달 -> 서비스 기본값(500) 사용 유도.
        log.info("/query 요청: productClsCode={}, countryCode={}, regDay={}, convertKgYn={}",
            productClsCode, countryCode, regDay, convertKgYn);

        return livestockPriceService.getPriceInfo(
                productClsCode,
                null, // 서비스에서 축산물(500) 기본값 사용
                countryCode,
                regDay,
                convertKgYn
            )
            .map(priceList -> {
                if (priceList == null || priceList.isEmpty()) { // 방어적 null 체크 포함
                    log.info("/query 결과 없음. Params: productClsCode={}, countryCode={}, regDay={}, convertKgYn={}",
                        productClsCode, countryCode, regDay, convertKgYn);
                    return ResponseEntity.noContent().<List<ItemPriceInfo>>build();
                }
                log.info("/query 성공. {}건. Params: productClsCode={}, countryCode={}, regDay={}, convertKgYn={}",
                    priceList.size(), productClsCode, countryCode, regDay, convertKgYn);
                return ResponseEntity.ok(priceList); // 200 OK
            })
            .defaultIfEmpty(ResponseEntity.notFound().<List<ItemPriceInfo>>build())
            .onErrorResume(IllegalArgumentException.class, ex -> { // 서비스단 regDay 형식 오류 등
                log.warn("/query 잘못된 파라미터: {}", ex.getMessage());
                // TODO: 클라이언트에게 에러 원인 상세 전달 방안 고민. (현재는 400 + 빈 body)
                return Mono.just(ResponseEntity.badRequest().body(Collections.<ItemPriceInfo>emptyList()));
            })
            .onErrorResume(Exception.class, ex -> { // 그 외 서버 오류
                log.error("/query 예상치 못한 오류", ex);
                return Mono.just(ResponseEntity.internalServerError().<List<ItemPriceInfo>>build());
                // 또는: return Mono.just(ResponseEntity.internalServerError().body(Collections.<ItemPriceInfo>emptyList()));
            });
    }

    // --- 특정 축종별 전국 도매 가격 간편 조회 엔드포인트 ---
    @Operation(summary = "전국 도매 소고기 가격 조회", description = "지정 날짜(기본: 최근일) 전국 도매 소고기(국내산/수입산) 가격 조회.")
    @GetMapping("/beef")
    public Mono<ResponseEntity<List<ItemPriceInfo>>> getBeefPrices(
        @Parameter(description = "조회 기준 날짜 (YYYY-MM-DD). 기본: 최근일.", example = "2025-05-13")
        @RequestParam(required = false) String regDay) {
        log.info("/beef 요청. 날짜: {}", regDay == null ? "최근" : regDay);
        return livestockPriceService.getNationalWholesaleBeefPrice(regDay)
            .map(priceList -> priceList.isEmpty() ?
                ResponseEntity.noContent().<List<ItemPriceInfo>>build() :
                ResponseEntity.ok(priceList))
            .defaultIfEmpty(ResponseEntity.notFound().<List<ItemPriceInfo>>build())
            .onErrorResume(Exception.class, ex -> {
                log.error("/beef 조회 오류", ex);
                return Mono.just(ResponseEntity.internalServerError().<List<ItemPriceInfo>>build());
            });
    }

    @Operation(summary = "전국 도매 돼지고기 가격 조회", description = "지정 날짜(기본: 최근일) 전국 도매 돼지고기(국내산/수입산) 가격 조회.")
    @GetMapping("/pork")
    public Mono<ResponseEntity<List<ItemPriceInfo>>> getPorkPrices(
        @Parameter(description = "조회 기준 날짜 (YYYY-MM-DD). 기본: 최근일.", example = "2025-05-13")
        @RequestParam(required = false) String regDay) {
        log.info("/pork 요청. 날짜: {}", regDay == null ? "최근" : regDay);
        return livestockPriceService.getNationalWholesalePorkPrice(regDay)
            .map(priceList -> priceList.isEmpty() ?
                ResponseEntity.noContent().<List<ItemPriceInfo>>build() :
                ResponseEntity.ok(priceList))
            .defaultIfEmpty(ResponseEntity.notFound().<List<ItemPriceInfo>>build())
            .onErrorResume(Exception.class, ex -> {
                log.error("/pork 조회 오류", ex);
                return Mono.just(ResponseEntity.internalServerError().<List<ItemPriceInfo>>build());
            });
    }

    @Operation(summary = "전국 도매 닭고기 가격 조회", description = "지정 날짜(기본: 최근일) 전국 도매 닭고기 가격 조회.")
    @GetMapping("/chicken")
    public Mono<ResponseEntity<List<ItemPriceInfo>>> getChickenPrices(
        @Parameter(description = "조회 기준 날짜 (YYYY-MM-DD). 기본: 최근일.", example = "2025-05-13")
        @RequestParam(required = false) String regDay) {
        log.info("/chicken 요청. 날짜: {}", regDay == null ? "최근" : regDay);
        return livestockPriceService.getNationalWholesaleChickenPrice(regDay)
            .map(priceList -> priceList.isEmpty() ?
                ResponseEntity.noContent().<List<ItemPriceInfo>>build() :
                ResponseEntity.ok(priceList))
            .defaultIfEmpty(ResponseEntity.notFound().<List<ItemPriceInfo>>build())
            .onErrorResume(Exception.class, ex -> {
                log.error("/chicken 조회 오류", ex);
                return Mono.just(ResponseEntity.internalServerError().<List<ItemPriceInfo>>build());
            });

    }
}
