package flcat.gogiyo.service;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import flcat.gogiyo.dto.ExternalApiResponse;
import flcat.gogiyo.dto.ExternalApiResponse.DataContent;
import flcat.gogiyo.dto.ItemPriceInfo;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;


@Slf4j
@ExtendWith(MockitoExtension.class)
class LivestockProductPriceServiceTest {

    // 테스트용 상수
    private static final int MAX_RETRY_ATTEMPTS_FOR_TEST = 2;
    //  테스트 대상 클래스, @Mock 으로 선언된 객체들이 이 클래스에 주입됨
    @InjectMocks
    private LivestockProductPriceService livestockProductPriceService;

    // WebClient 는 외부 Api 통신을 하므로, 실제 호출 대신 Mock 객체로 대체
    @Mock
    private WebClient webClient;

    // WebClient.RequestHeadersUriSpec, WebClient.RequestHeadersSpec, WebClient.ResponseSpec 은
    // WebClient 의 fluent Api 를 모킹하기 위해 필요
    // WebClient 의 get(), uri(), retrieve() 등을 연쇄적으로 호출하는 것을 모킹해야 함
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    // @Value 로 주입되는 필드들은 Mock 객체 주입 방식으로는 테스트하기 어렵다 Why?
    // ReflectionTestUtils 를 사용하거나, 테스트용 생성자를 만들어 값을 직접 주입하는 방법이 있음
    private String apiKey = "test-api-key";
    private String apiId = "test-api-id";
    private String baseUrl = "http://localhost:8080/test-api";

    // WebClient 테스트 전략: 현재는 Mockito로 WebClient 자체를 모킹.
    // MockWebServer 사용도 고려했었음.
    //   - 장점: 실제 HTTP 요청/응답 시뮬레이션 가능, 좀 더 통합 테스트에 가까움.
    //   - 단점: 외부 라이브러리 의존성 추가, 테스트 설정/실행 시간 증가 가능성.
    // 현재 단계에서는 단위 테스트 본질(의존성 격리, 서비스 로직 집중)을 위해 Mockito 선택.

    @BeforeEach
    void setUp() {

        log.debug("테스트 설정 시작: @Value 필드 값 주입 및 WebClient Mock 설정");

        // @Value 필드 값 설정. 테스트 대상 객체가 생성된 후(@InjectMocks)에 설정해야 함
        ReflectionTestUtils.setField(livestockProductPriceService, "apiKey", apiKey);
        ReflectionTestUtils.setField(livestockProductPriceService, "apiId", apiId);
        ReflectionTestUtils.setField(livestockProductPriceService, "baseUrl", baseUrl);

        // WebClient 의 fluent Api 모킹 설정
        // webClient.get() 호출 시 > requestHeadersUriSpec 반환하도록 설정
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        // requestHeadersUriSpec.uri(anyString()) 호출 시 > requestHeadersSpec 반환하도록 설정
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        // requestHeadersSpec.retrieve() 호출 시 > responseSpec 반환하도록 설정
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Nested
    @DisplayName("getPriceInfo 메소드 테스트")
    class GetPriceInfoTests {

        @Test
        @DisplayName("Api 성공 응답 및 데이터 정상 반환 시")
        void getPriceInfoSuccess() {
            // given : Api 가 성공적으로 응답하고, ItemPriceInfo 리스트를 포함하는 externalApiResponse 를 반환하는 상황 준비
            ItemPriceInfo item1 = new ItemPriceInfo(); // 테스트용 dto 객체 생성
            item1.setItemName("테스트 소고기");
            item1.setItemCode("4301");

            ExternalApiResponse.DataContent dataContent = new DataContent();
            dataContent.setItems(List.of(item1));
            dataContent.setMessage("Success");

            ExternalApiResponse mockApiResponse = new ExternalApiResponse();
            mockApiResponse.setCondition("000"); // 성공 코드
            mockApiResponse.setData(dataContent);

            // responseSpec.bodyToMono(ExternalApiResponse.class) 호출 시 > mockApiresponse 를 담은 Mono 반환 설정
            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(
                Mono.just(mockApiResponse));
            // 서비스 코드에서 HttpStatusCode::isError로 체크하므로, 정상 응답 시에는 onStatus의 에러 핸들러가 동작 안 함.

            // when : 테스트 대상 메소드 실행
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getPriceInfo("02",
                "500", null, null, "N");

            // then : 결과 검증 (StepVerifier 사용)
            StepVerifier.create(resultMono)
                .expectNextMatches(
                    list -> list.size() == 1 && list.get(0).getItemName().equals("테스트 소고기"))
                .verifyComplete(); // Mono가 정상적으로 완료되었는지 확인
        }

        @Test
        @DisplayName("Api 응답은 성공했으나 데이터가 없는 경우 (condition '001')")
        void getPriceInfoSuccessNodata() {
            // given : Api 가 성공적으로 응답했으나 data 필드 또는 items 리스트가 비어있는 경우
            ExternalApiResponse.DataContent dataContent = new ExternalApiResponse.DataContent();
            dataContent.setItems(Collections.emptyList());
            dataContent.setMessage("No data");

            ExternalApiResponse mockApiResponse = new ExternalApiResponse();
            mockApiResponse.setCondition("001"); // 데이터 없음 코드
            mockApiResponse.setData(dataContent);

            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(
                Mono.just(mockApiResponse));

            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getPriceInfo("02",
                "500", null, null, "N");

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(List::isEmpty) // 빈 리스트가 반환되는지 확인
                .verifyComplete();
        }

        @Test
        @DisplayName("Api 호출 시 Http 4xx 클라이언트 에러 발생 하는 경우")
        void getPriceInfoClientError() {

            WebClientResponseException mockException = new WebClientResponseException(
                "Mock 400 Error",
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                HttpHeaders.EMPTY, null, null);

            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(Mono.error(mockException));

            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getPriceInfo("02",
                "500", "잘못된지역코드", null, "N");

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
        }

        @Test
        @DisplayName("Api 호출 시 Http 5xx 서버 에러 발생 및 재시도 후 성공한 경우")
        void getPriceInfoServerErrorThenSuccessOnRetry() {

            // given : 첫번쨰 Api 호출은 500 서버 에러, 두번째 호출은 성공하는 경우
            WebClientResponseException serverException = new WebClientResponseException(
                "Mock 500 Error", HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                null, null, null);

            ItemPriceInfo item1 = new ItemPriceInfo();
            item1.setItemName("재시도 성공 소고기");
            ExternalApiResponse.DataContent successDataContent = new ExternalApiResponse.DataContent();
            successDataContent.setItems(List.of(item1));
            ExternalApiResponse successResponse = new ExternalApiResponse();
            successResponse.setCondition("000");
            successResponse.setData(successDataContent);

            // 첫번쨰 retrieve() 는 서버 에러 Mono 반환, 두번째 retrieve() 는 성공 Mono 반환
            when(responseSpec.bodyToMono(ExternalApiResponse.class))
                .thenReturn(Mono.error(serverException)) // 첫번째 호출 시 에러
                .thenReturn(Mono.just(successResponse)); // 두번째 호출 시 정상 responseSpec

            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getPriceInfo("02",
                "500", null,
                null, "N");

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(
                    list -> list.size() == 1 && list.get(0).getItemName().equals("재시도 성공 소고기"))
                .verifyComplete();

            // WebClient.get() 이 총 2번 호출되었는지 확인
            verify(webClient, times(2)).get();
        }

        @Test
        @DisplayName("Api 호출 시 Http 5xx 서버 에러 발생 및 모든 재시도 실패하는 경우")
        void getPriceInfoServerErrorAllRetriesFail() {
            // given : 모든 Api 호출이 500 서버 에러를 반한하는 경우
            WebClientResponseException serverException = new WebClientResponseException(
                "Mock 500 Error", HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                null, null, null);

            // retrieve() 가 호출될 때마다 항상 서버 에러 Mono 반환
            when(responseSpec.bodyToMono(ExternalApiResponse.class)
                .thenReturn(Mono.error(serverException)));

            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getPriceInfo("02",
                "500", null, null, "N");

            // then : 모든 재시도 실패 후 서비스는 빈리스트를 반환 함
            StepVerifier.create(resultMono)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

            verify(webClient,
                times(LivestockProductPriceServiceTest.MAX_RETRY_ATTEMPTS_FOR_TEST + 1)).get();
        }

        @Test
        @DisplayName("regDay 파라미터가 유효한 형식일 경우")
        void getPriceInfoWithValidRegDay() {
            // given
            ExternalApiResponse mockApiResponse = new ExternalApiResponse(); // 호출 경로 확인용
            mockApiResponse.setCondition("000");
            mockApiResponse.setData(new ExternalApiResponse.DataContent());

            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(
                Mono.just(mockApiResponse));

            String validRegDay = "2025-05-20";

            // when
            livestockProductPriceService.getPriceInfo("02", "500", null, validRegDay, "N")
                .subscribe();
            // then : UriComponentsBuilder 가 p_regday 파라미터를 포함하여 url 을 생성했는지 확인
            verify(requestHeadersUriSpec).uri(argThat((String uriString) -> uriString.contains("p_regday=" + validRegDay)));
        }

        @Test
        @DisplayName("regDay 파라미터가 잘못된 형식일 경우 Api 호출 시 regDay 파라미터 제외")
        void getPRiceInfoWithInvalidRegDayExcludesParam() {
            // given
            ExternalApiResponse mockApiResponse = new ExternalApiResponse();
            mockApiResponse.setCondition("000");
            mockApiResponse.setData(new ExternalApiResponse.DataContent());

            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(
                Mono.just(mockApiResponse));

            String invalidRegDay = "20250520"; //(잘못된 형식일 경우)

            // when
            livestockProductPriceService.getPriceInfo("02", "500", null, invalidRegDay, "N")
                .subscribe();

            // then : p_regday 파라미터가 uri 에서 제외되었는지 검증
            // argThat 으로 p_regday= 부재 확인
            verify(requestHeadersUriSpec).uri(argThat((String uriString) -> !uriString.contains("p_regday=")));
        }
    }

    @Nested
    @DisplayName("getNationalWholesaleLivestockPrice 메소드 테스트")
    class GetNationalWholesaleLivestockPriceTests {


        @BeforeEach
        void setUpForFilteringTests() {

            // getPriceInfo 가 특정 아이템 리스트를 반환하도록 모킹

            // 더미 데이터
            ItemPriceInfo beef1 = new ItemPriceInfo();
            beef1.setItemCode("4301");
            beef1.setItemName("한우등심");
            ItemPriceInfo beef2 = new ItemPriceInfo();
            beef2.setItemCode("4401");
            beef2.setItemName("수입소갈비");
            ItemPriceInfo pork1 = new ItemPriceInfo();
            pork1.setItemCode("4304");
            pork1.setItemName("돼지삼겹살");
            ItemPriceInfo pork2 = new ItemPriceInfo();
            pork2.setItemCode("4402");
            pork2.setItemName("수입돼지목살");
            ItemPriceInfo chicken1 = new ItemPriceInfo();
            chicken1.setItemCode("9901");
            chicken1.setItemName("닭고기");
            ItemPriceInfo otherItem1 = new ItemPriceInfo();
            otherItem1.setItemCode("1234");
            otherItem1.setItemName("기타품목");

            List<ItemPriceInfo> allItems = List.of(beef1, beef2, pork1, pork2, chicken1,
                otherItem1);

            ExternalApiResponse.DataContent dataContent = new DataContent();
            dataContent.setItems(allItems);
            ExternalApiResponse mockApiResponse = new ExternalApiResponse();
            mockApiResponse.setCondition("000");
            mockApiResponse.setData(dataContent);

            // getPriceInfo 내부의 WebClient 호출이 이 mockApiResponse 를 반환하도록 설정
            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(
                Mono.just(mockApiResponse));
        }

        @Test
        @DisplayName("소고기 필터링 성공 시")
        void filterBeefSuccess(){
            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getNationalWholesaleLivestockPrice(
                "beef", null);

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(list -> list.size() == 2 && list.stream().allMatch(
                    item -> item.getItemCode().equals("4301") || item.getItemCode()
                        .equals("4401")))
                .verifyComplete();
        }

        @Test
        @DisplayName("돼지고기 필터링 성공 시")
        void filterPorkSuccess(){
            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getNationalWholesaleLivestockPrice(
                "pork", null);

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(list -> list.size() == 2 && list.stream().allMatch(
                    item -> item.getItemCode().equals("4304") || item.getItemCode()
                        .equals("4402")))
                .verifyComplete();
        }

        @Test
        @DisplayName("닭고기 필터링 성공 시")
        void filterChickenSuccess(){
            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getNationalWholesaleLivestockPrice(
                "chicken", null);

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(list -> list.size() == 1 && list.stream().allMatch(
                    item -> item.getItemCode().equals("9901")))
                .verifyComplete();
        }

        @Test
        @DisplayName("지원하지 않는 코드 필터링 시 빈 리스트 반환")
        void filterUnknownTypeReturnsEmptyList(){
            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getNationalWholesaleLivestockPrice(
                "unknown", null);

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
        }

        @Test
        @DisplayName("getPriceInfo 가 빈리스트 반환 시 필터링 결과도 빈리스트 반환")
        void filterWhenApiReturnEmptyReturnEmptyList() {
            // given : getPriceInfo 가 빈리스트를 반환하도록 WebClient 모킹 재설정
            ExternalApiResponse.DataContent emptyDataContent = new ExternalApiResponse.DataContent();
            emptyDataContent.setItems(Collections.emptyList());

            ExternalApiResponse emptyApiResponse = new ExternalApiResponse();
            emptyApiResponse.setCondition("001"); // 데이터 없음
            emptyApiResponse.setData(emptyDataContent);

            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(
                Mono.just(emptyApiResponse));

            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getNationalWholesaleLivestockPrice(
                "beef", null);

            // then
            StepVerifier.create(resultMono)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
        }
    }
}
