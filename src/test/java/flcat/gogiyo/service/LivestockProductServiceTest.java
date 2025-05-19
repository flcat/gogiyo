package flcat.gogiyo.service;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import flcat.gogiyo.dto.ExternalApiResponse;
import flcat.gogiyo.dto.ExternalApiResponse.DataContent;
import flcat.gogiyo.dto.ItemPriceInfo;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
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

@ExtendWith(MockitoExtension.class)
class LivestockProductServiceTest {

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

    // MockServer: 실제 Http 서버를 띄우지 않고 Http 요청/응답을 시뮬레이션함.
    // WebClient 테스트 시 유용하지만, 여기서는 WebClient 자체를 모킹하는 방식으로 진행함. Why?
    // 만약 MockWebServer 사용 시
    // public static MockWebServer mockBackEnd;
    // @BeforeAll static void setUp() throws IOException { mockBackEnd = new MockWebServer(); mockBackEnd.start(); }
    // @AfterAll static void tearDown() throws IOException { mockBackEnd.shutDown(); }
    // @BeforeEach void initialize() { baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort()); }

    @BeforeEach
    void setUp() {

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
            // onStatus 는 에러가 아닐 때는 호출되지 않도록, 혹은 정상 응답을 그대로 통과시키도록 설정
            // 좀 더 정확하려면 onStatus 도 모킹 필요함
            // when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

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

            when(responseSpec.onStatus(any(), any())).thenReturn(
                (ResponseSpec) Mono.error(mockException));

            // when
            Mono<List<ItemPriceInfo>> resultMono = livestockProductPriceService.getPriceInfo("02",
                "500", "잘못된지역코드", null, "N");

            StepVerifier.create(resultMono)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

            // 이런 간단한 방법도 있음.
            // when(requestHeadersSpec.retrieve()).thenReturn(Mono.error(mockException));
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
            when(requestHeadersSpec.retrieve())
                .thenReturn((ResponseSpec) Mono.error(serverException)) // 첫번째 호출 시 에러
                .thenReturn(responseSpec); // 두번째 호출 시 정상 responseSpec

            // 두번째 호출 시 responseSpec 은 성공 응답을 반환하도록 추가 설정
            when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(
                Mono.just(successResponse));
            // 재시도 시 onStatus 는 에러를 발생시키지 않아야 함
            // when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec); // 명시적으로

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

        }

        @Test
        @DisplayName("regDay 파라미터가 유효한 형식일 경우")
        void getPriceInfoWithValidRegDay() {

        }

        @Test
        @DisplayName("regDay 파라미터가 잘못된 형식일 경우 Api 호출 시 regDay 파라미터 제외")
        void getPRiceInfoWithInvalidRegDayExcludesParam() {

        }
    }
}
