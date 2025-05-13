package flcat.gogiyo.service;

import flcat.gogiyo.dto.ExternalApiResponse;
import flcat.gogiyo.dto.ItemPriceInfo;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

// 외부 API 연동: 축산물 가격 정보 서비스. WebClient, Reactor 사용.
@Slf4j
@Service
public class LivestockProductPriceService {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiId;
    private final String baseUrl;

    // API 요청 기본값. 변경 가능성 낮아 상수로.
    private static final String DEFAULT_PRODUCT_CLS_CODE = "02"; // 도매
    private static final String DEFAULT_ITEM_CATEGORY_CODE = "500"; // 축산물
    private static final String DEFAULT_CONVERT_KG_YN = "N";
    private static final String DEFAULT_RETURN_TYPE = "json";

    // API 재시도 설정. 외부 API 불안정성 대비.
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_DELAY_SECONDS = 1;


    public LivestockProductPriceService(
        @Qualifier("defaultApiWebClient") WebClient webClient, // Config Bean 명시적 주입
        @Value("${external.api.key}") String apiKey,
        @Value("${external.api.id}") String apiId,
        @Value("${external.api.baseUrl}") String baseUrl) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.apiId = apiId;
        this.baseUrl = baseUrl;

        // 설정값 누락 시 경고. 앱 실행은 막지 않음 (개발 편의). 운영 시 정책 재고려.
        if (!StringUtils.hasText(this.apiKey) || !StringUtils.hasText(this.apiId)
            || !StringUtils.hasText(this.baseUrl)) {
            log.warn("필수 API 설정(key/id/baseUrl) 누락. application.properties 확인 필요");
        }
    }

    /**
     * 외부 API 호출하여 가격 정보 조회 (축산물만)
     * - 비동기 처리, Reactor 사용.
     * - 실패 시 빈 리스트 Mono 반환으로 서비스 안정성 고려.
     */
    public Mono<List<ItemPriceInfo>> getPriceInfo(
        String productClsCode, String itemCategoryCode, String countryCode,
        String regDay, String convertKgYn) {

        // 파라미터 기본값 설정. 서비스 계층 자체 방어. (컨트롤러 외 호출 가능성)
        String actualProductClsCode = StringUtils.hasText(productClsCode) ? productClsCode : DEFAULT_PRODUCT_CLS_CODE;
        String actualItemCategoryCode = StringUtils.hasText(itemCategoryCode) ? itemCategoryCode : DEFAULT_ITEM_CATEGORY_CODE;
        String actualConvertKgYn = StringUtils.hasText(convertKgYn) ? convertKgYn : DEFAULT_CONVERT_KG_YN;

        // URL 생성. UriComponentsBuilder가 인코딩 자동 처리. fromUriString 사용 (fromHttpUrl deprecated).
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(this.baseUrl)
            .queryParam("p_cert_keystring", this.apiKey)
            .queryParam("p_cert_id", this.apiId)
            .queryParam("p_returntype", DEFAULT_RETURN_TYPE)
            .queryParam("p_product_cls_code", actualProductClsCode)
            .queryParam("p_item_category_code", actualItemCategoryCode)
            .queryParam("p_convert_kg_yn", actualConvertKgYn);

        // 선택적 파라미터는 있을 때만 추가.
        if (StringUtils.hasText(countryCode)) {
            uriBuilder.queryParam("p_country_code", countryCode);
        }
        if (StringUtils.hasText(regDay)) {
            // TODO: regDay 형식(YYYY-MM-DD) 검증 로직 추가 필요. 현재는 Api 서버 에러에 의존. 불필요한 Api 호출 가능성 있음
            uriBuilder.queryParam("p_regday", regDay);
        }
        String requestUrl = uriBuilder.toUriString();
        log.info("API 호출 > URL: {}", requestUrl);

        return this.webClient.get()
            .uri(requestUrl)
            .retrieve()
            // HTTP 4xx, 5xx 에러 처리. API 스펙 에러코드(200, 900)는 HTTP 상태로 먼저 감지될 것.
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .defaultIfEmpty("오류 응답 본문이 없음.") // 본문 없을 경우 대비
                    .flatMap(errorBody -> {
                        log.error("API 호출 실패. Status: {}, Body: '{}', URL: {}",
                            clientResponse.statusCode(), errorBody, requestUrl);
                        // 커스텀 예외 고려했으나, 일단 WebClientResponseException 사용. @ControllerAdvice 처리 가능.
                        return Mono.error(new WebClientResponseException(
                            "외부 API 요청 오류",
                            clientResponse.statusCode().value(),
                            clientResponse.statusCode().toString(),
                            clientResponse.headers().asHttpHeaders(),
                            errorBody.getBytes(),
                            null
                        ));
                    }))
            // 응답 본문 DTO 변환.
            .bodyToMono(ExternalApiResponse.class)
            .map(apiResponse -> {
                // API 응답 객체 null 체크. (거의 없겠지만 방어용)
                if (apiResponse == null) {
                    log.warn("API 응답 객체 null. URL: {}", requestUrl);
                    return Collections.<ItemPriceInfo>emptyList();
                }

                // API 자체 응답 코드(condition) 로깅. "000" 성공, "001" 데이터 없음 등.
                log.debug("API 응답 condition: {}, URL: {}", apiResponse.getCondition(), requestUrl);

                ExternalApiResponse.DataContent dataContent = apiResponse.getData();
                // 실제 데이터(items) 존재 여부 꼼꼼히 확인.
                if (dataContent != null && dataContent.getItems() != null && !dataContent.getItems().isEmpty()) {
                    log.info("API 응답 성공. 항목 수: {}, URL: {}", dataContent.getItems().size(), requestUrl);
                    return dataContent.getItems();
                } else {
                    // API 응답은 200 OK이나, 실제 데이터가 없거나(condition "001") 다른 메시지 온 경우.
                    // 이건 시스템 오류는 아님.
                    String message = (dataContent != null && StringUtils.hasText(dataContent.getMessage()))
                        ? dataContent.getMessage() : "항목 없음 또는 데이터 구조 불일치";
                    String errorCode = (dataContent != null && StringUtils.hasText(dataContent.getErrorCode()))
                        ? dataContent.getErrorCode() : "N/A";
                    // "데이터 없음(001)"은 INFO 레벨 로깅도 고려. 현재는 WARN.
                    log.warn("API 응답: 항목 없음 또는 메시지 수신. Message: '{}', ErrorCode: '{}', URL: {}",
                        message, errorCode, requestUrl);
                    return Collections.<ItemPriceInfo>emptyList();
                }
            })
            // 재시도 로직 (5xx 서버 에러 시). 일시적 문제 대응.
            // Exponential Backoff 등 더 정교한 전략도 있지만, 우선 간단히 구현.
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(RETRY_DELAY_SECONDS))
                .filter(throwable -> throwable instanceof WebClientResponseException webEx
                    && webEx.getStatusCode().is5xxServerError())
                .doBeforeRetry(retrySignal ->
                    log.warn("API 재시도 (서버 오류). 시도: {}/{}, URL: {}, 원인: {}",
                        retrySignal.totalRetries() + 1, MAX_RETRY_ATTEMPTS, requestUrl,
                        retrySignal.failure().getMessage()))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("API 재시도 모두 실패. URL: {}, 최종 오류: {}", requestUrl,
                        retrySignal.failure().getMessage());
                    return retrySignal.failure(); // 마지막 에러 전파
                }))
            // 최종 예외 처리. 서비스 중단 방지 위해 빈 리스트 반환.
            .onErrorResume(WebClientResponseException.class, ex -> { // HTTP 관련 예외
                log.error("WebClientResponseException (재시도 후 가능성 있음). URL: {}, Status: {}, Body: '{}'",
                    requestUrl, ex.getStatusCode(), ex.getResponseBodyAsString());
                return Mono.just(Collections.emptyList());
            })
            .onErrorResume(Exception.class, ex -> { // 그 외 모든 예외 (네트워크, 파싱 등)
                log.error("API 호출 중 예기치 않은 오류. URL: {}, 원인: {}", requestUrl, ex.getMessage(), ex);
                return Mono.just(Collections.emptyList());
            });
    }

    /**
     * 전국 도매 축산물(소 / 돼지 / 닭) 가격 정보 조회 및 필터링.
     * getPriceInfo 호출 후 itemCode로 필터링.
     */
    public Mono<List<ItemPriceInfo>> getNationalWholesaleLivestockPrice(String livestockType, String regDay) {
        log.debug("전국 도매가 조회 요청. 축종: {}, 날짜: {}", livestockType, regDay);

        // 축산물 전체 조회 후 필터링.
        return getPriceInfo(DEFAULT_PRODUCT_CLS_CODE, DEFAULT_ITEM_CATEGORY_CODE, null, regDay, DEFAULT_CONVERT_KG_YN)
            .map(allItems -> {
                if (allItems.isEmpty()) {
                    log.info("필터링 대상 데이터 없음 (API 결과 비어있음). 요청 축종: {}", livestockType);
                    return Collections.<ItemPriceInfo>emptyList();
                }

                log.debug("필터링 시작. 전체 항목 수: {}, 대상 축종: {}", allItems.size(), livestockType);

                List<ItemPriceInfo> filteredList;
                String typeToFilter = livestockType.toLowerCase(); // 대소문자 무시

                // TODO: 코드값 하드코딩 개선 필요 (enum 또는 설정 파일 관리).
                if ("beef".equals(typeToFilter)) {
                    filteredList = allItems.stream()
                        .filter(item -> item.getItemCode() != null &&
                            ("4301".equals(item.getItemCode()) || "4401".equals(item.getItemCode()))) // 국내산/수입산 소
                        .collect(Collectors.toList());
                } else if ("pork".equals(typeToFilter)) {
                    filteredList = allItems.stream()
                        .filter(item -> item.getItemCode() != null &&
                            ("4304".equals(item.getItemCode()) || "4402".equals(item.getItemCode()))) // 국내산/수입산 돼지
                        .collect(Collectors.toList());
                } else if ("chicken".equals(typeToFilter)) {
                    filteredList = allItems.stream()
                        .filter(item -> "9901".equals(item.getItemCode())) // 닭
                        .collect(Collectors.toList());
                } else {
                    // 정의되지 않은 축종 타입.
                    log.warn("지원하지 않는 축종 타입으로 필터링 요청. 입력값: {}", livestockType);
                    filteredList = Collections.emptyList();
                }

                log.info("필터링 완료. 결과 항목 수: {}, 요청 축종: {}, 전체 항목 수 원본: {}",
                    filteredList.size(), livestockType, allItems.size());
                return filteredList;
            });
    }

    // --- 편의 메소드: 각 축종별로 쉽게 호출 ---

    // 전국 도매 소고기 가격 (국내/수입)
    public Mono<List<ItemPriceInfo>> getNationalWholesaleBeefPrice(String regDay) {
        return getNationalWholesaleLivestockPrice("beef", regDay);
    }

    // 전국 도매 돼지고기 가격 (국내/수입)
    public Mono<List<ItemPriceInfo>> getNationalWholesalePorkPrice(String regDay) {
        return getNationalWholesaleLivestockPrice("pork", regDay);
    }

    // 전국 도매 닭고기 가격
    public Mono<List<ItemPriceInfo>> getNationalWholesaleChickenPrice(String regDay) {
        return getNationalWholesaleLivestockPrice("chicken", regDay);
    }
}