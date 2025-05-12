package flcat.gogiyo.service;

import flcat.gogiyo.dto.ExternalApiResponse;
import flcat.gogiyo.dto.ItemPriceInfo;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
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

@Slf4j
@Service
public class LivestockProductPriceService {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiId;
    private final String baseUrl;

    // Api 요청 시 사용할 파라미터 기본값들
    private static final String DEFAULT_PRODUCT_CLS_CODE = "02"; // 도매
    private static final String DEFAULT_ITEM_CATEGORY_CODE = "500"; // 농수축산물 다양하게 있으나 여기서는 축산물만 다룸
    private static final String DEFAULT_CONVERT_KG_YN = "N";
    private static final String DEFAULT_RETURN_TYPE = "json"; // return 타입 JSON

    // 재시도 설정 (외부 Api가 불안한 상황이 생길 수 있으므로)
    private static final int MAX_RETRY_ATTEMPTS = 2; // 최대 2번 재시도
    private static final long RETRY_DELAY_SECONDS = 1; // 1초 간격


    public LivestockProductPriceService(
        @Qualifier("defaultApiWebClient") WebClient webClient,
        @Value("${external.api.key}") String apiKey,
        @Value("${external.api.id}") String apiId,
        @Value("${external.api.baseUrl}") String baseUrl) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.apiId = apiId;
        this.baseUrl = baseUrl;

        // 주요 설정값 누락 시 로그 출력
        if (!StringUtils.hasText(this.apiKey) || !StringUtils.hasText(this.apiId)
            || !StringUtils.hasText(this.baseUrl)) {
            log.warn("CRITICAL : 외부 Api configuration (key, id, baseUrl) 가 없거나 찾지 못했습니다. "
            + "어플리케이션 프로퍼티를 확인해보세요");
        }
    }

    /**
     * 외부 API를 호출하여 축산물 가격 정보를 조회합니다.
     * @param productClsCode   구분 (01:소매, 02:도매)
     * @param itemCategoryCode 부류코드 (500:축산물 등)
     * @param countryCode      지역코드 (선택)
     * @param regDay           조회일자 YYYY-MM-DD (선택)
     * @param convertKgYn      kg단위 환산여부 Y/N (선택)
     * @return 조회된 가격 정보 리스트를 담은 Mono, 실패 시 빈 리스트를 담은 Mono.
     */
    public Mono<List<ItemPriceInfo>> getPriceInfo(
        String productClsCode, String itemCategoryCode, String countryCode,
        String regDay, String convertKgYn) {

        // 파라미터 유효성 검사, 기본값 설정
        /**
         * 서비스는 애플리케이션의 핵심 비즈니스 로직을 담고 있으며, 컨트롤러뿐만 아니라 다른 서비스,
         * 배치 작업, 테스트코드 등 여러경로로 호출될 수 있다.
         * 서비스 자체로도 입력값에 대한 방어처리를 해주는 것이 중요.
         */
        String actualProductClsCode = StringUtils.hasText(productClsCode) ? productClsCode : DEFAULT_PRODUCT_CLS_CODE;
        String actualItemCategoryCode = StringUtils.hasText(itemCategoryCode) ? itemCategoryCode : DEFAULT_ITEM_CATEGORY_CODE;
        String actualConvertKgYn = StringUtils.hasText(convertKgYn) ? convertKgYn : DEFAULT_CONVERT_KG_YN;

        // UriComponentsBuilder를 사용하면 Url 인코딩 등을 자동으로 처리해줌
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(this.baseUrl)
            .queryParam("p_cert_keystring", this.apiKey)
            .queryParam("p_cert_id", this.apiId)
            .queryParam("p_returntype", DEFAULT_RETURN_TYPE) // 이 서비스는 JSON 응답만 처리
            .queryParam("p_product_cls_code", actualProductClsCode)
            .queryParam("p_item_category_code", actualItemCategoryCode)
            .queryParam("p_convert_kg_yn", actualConvertKgYn);

        if (StringUtils.hasText(countryCode)) {
            uriBuilder.queryParam("p_country_code", countryCode);
        }
        if (StringUtils.hasText(regDay)) {
            uriBuilder.queryParam("p_regday", regDay);
        }
        String requestUrl = uriBuilder.toUriString();
        log.info("외부 api 호출 시도: Url='{}'",requestUrl);

        return this.webClient.get()
            .uri(requestUrl)
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .defaultIfEmpty("No error response body")
                    .flatMap(errorBody -> {
                        log.error(
                            "외부 api 호출 실패: Http status={}, ResponseBody='{}', RequestURL='{}'",
                            clientResponse.statusCode(), errorBody, requestUrl);
                        return Mono.error(new WebClientResponseException(
                            "외부 Api 요청 실패",
                            clientResponse.statusCode().value(),
                            clientResponse.statusCode().toString(),
                            clientResponse.headers().asHttpHeaders(),
                            errorBody.getBytes(), // 또는 null, 인코딩 정보가 있다면 같이 제공
                            null // Charset 알수 없다면 null
                        ));
                    }))
            .bodyToMono(ExternalApiResponse.class) // 성공 시 응답 본문을 DTO로 변환
            .map(apiResponse -> {
                // 응답 객체가 null일 경우
                if (apiResponse == null) {
                    log.warn("null API 응답 객체를 수신했습니다. : {}", requestUrl);
                    return Collections.<ItemPriceInfo>emptyList(); // 안전하게 빈리스트 반환
                }

                log.debug("api 응답 상태: '{}' Url: {}", apiResponse.getCondition(), requestUrl);

                ExternalApiResponse.DataContent dataContent = apiResponse.getData();
                if (dataContent != null && dataContent.getItems() != null && !dataContent.getItems()
                    .isEmpty()) {
                    log.info("요청에 대한 {} 항목을 성공적으로 가져왔습니다: URL='{}'", dataContent.getItems().size(),
                        requestUrl);
                    return dataContent.getItems();
                } else {
                    // 데이터가 없거나, Api가 다른 메세지를 준 경우
                    String message =
                        (dataContent != null && StringUtils.hasText(dataContent.getMessage()))
                            ? dataContent.getMessage() : "항목을 찾을 수 없거나 데이터 구조가 일치하지 않습니다";
                    String errorCode =
                        (dataContent != null && StringUtils.hasText(dataContent.getErrorCode()))
                            ? dataContent.getErrorCode() : "N/A";

                    log.warn(
                        "가격 항목을 찾을 수 없거나 API가 메시지를 반환했습니다. Message='{}', ErrorCode='{}', URL='{}'",
                        message, errorCode, requestUrl);
                    return Collections.<ItemPriceInfo>emptyList();
                }
            })
            // 재시도 로직: 외부 Api가 일시적으로 불안정할 경우
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(RETRY_DELAY_SECONDS))
                .filter(throwable -> throwable instanceof WebClientResponseException webEx
                    && webEx.getStatusCode().is5xxServerError())
                .doBeforeRetry(retrySignal ->
                    log.warn("서버 오류로 인해 API 호출을 다시 시도하는 중입니다({} 시도): URL='{}', Error='{}'",
                        retrySignal.totalRetries() + 1, requestUrl,
                        retrySignal.failure().getMessage()))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("URL에 대한 API 호출 재시도가 종료되었습니다: '{}'. 최종 오류입니다: {}", requestUrl,
                        retrySignal.failure().getMessage());
                    return retrySignal.failure();
                }))
            .onErrorResume(WebClientResponseException.class, ex -> {
                // onStatus 에서 WebClientResponseException이 여기까지 도달할 수 있음 (retry 이후 특히 더)
                // 혹은 onStatus에서 처리하지 않은 다른 WebClient 관련 예외일 수 있음
                log.error(
                    "URL='{}'에 대해 (재시도 후) WebClientResponseException이 발생했습니다: Status={}, Body='{}'",
                    requestUrl, ex.getStatusCode(), ex.getResponseBodyAsString());

                // 컨트롤러에 빈 리스트를 반환하여 서비스 중단을 방지함!!
                return Mono.just(Collections.emptyList());
            })
            .onErrorResume(Exception.class, ex -> {
                // 예상치 못한 종류의 예외처리
                log.error("URL='{}'에 대한 외부 API 호출 중에 예기치 않은 오류가 발생했습니다: {}", requestUrl,
                    ex.getMessage(), ex);
                return Mono.just(Collections.emptyList());
            });
    }
}
