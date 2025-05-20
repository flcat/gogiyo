# 축산물 가격 정보 조회 API (GogiyoPriceAPI)

Spring Boot와 WebFlux를 사용해 외부 API에서 축산물 가격 정보를 가져오는 간단한 REST API입니다. 개인적으로 반응형 프로그래밍에 도전해보고 싶어서 시작한 프로젝트입니다.

## 🚀 개발 배경 및 목적

* **학습 목표:** Spring WebFlux, `WebClient`, Project Reactor (`Mono`, `Flux`)를 사용한 비동기/논블로킹 API 연동 실습. (기존에는 `RestTemplate`만 써봤는데, 이번에 큰맘 먹고 도전!)
* **외부 API 연동 경험:** 실제 공공데이터 API를 다뤄보면서 데이터 요청, 응답 처리, 예외 처리 등을 경험하고 싶었습니다.
* **포트폴리오:** 학습한 내용을 바탕으로 실제 동작하는 API를 만들어 포트폴리오로 활용.

## ✨ 주요 기능

* **축산물 가격 조회:** 전국 도매 기준 소고기, 돼지고기, 닭고기 가격 정보 제공.
    * 일반 조건 조회: 구분(도매/소매 - 현재는 도매 위주), 지역, 날짜 등 조합 가능.
    * 축종별 간편 조회: `/beef`, `/pork`, `/chicken` 엔드포인트로 쉽게 조회.
* **비동기 처리:** `WebClient`로 외부 API 비동기 호출 및 응답 처리.
* **에러 처리 및 재시도:** 외부 API 서버 불안정성에 대비해 5xx 에러 시 재시도 로직 구현. (이 부분에서 `retryWhen` 이해하느라 좀 헤맸습니다 😅)
* **API 문서화:** Swagger (SpringDoc OpenAPI)로 API 명세 자동 생성 및 UI 제공.

## 🛠️ 기술 스택

* **언어:** Java 17
* **프레임워크:** Spring Boot 3.x.x
    * Spring WebFlux Spring MVC (Tomcat 기반)*
    * Project Reactor (`Mono`, `Flux`)
* **HTTP 클라이언트:** `WebClient`
* **로깅:** SLF4J + Logback (Lombok `@Slf4j` 사용)
* **빌드 도구:** Gradle
* **API 문서화:** SpringDoc OpenAPI (Swagger UI)
* **테스트:**
    * JUnit 5
    * Mockito
    * Reactor-Test (`StepVerifier`)

## ⚙️ 프로젝트 실행 방법

1.  **프로젝트 클론:**
    ```bash
    git clone [이 저장소의 URL]
    cd [프로젝트 디렉토리]
    ```
2.  **외부 API 인증키 설정:**
    * `src/main/resources/application.properties` (또는 `application.yml`) 파일에 외부 API 키와 ID를 입력해야 합니다. (실제 키는 보안상 여기에 적지 않았습니다.)
        ```properties
        external.api.baseUrl=실제_API_BASE_URL # 예: [http://data.mafra.go.kr/](http://data.mafra.go.kr/)... (정확한 URL 확인 필요)
        external.api.key=YOUR_API_KEY
        external.api.id=YOUR_API_ID
        ```
    * **주의:** 실제 API 키를 Git에 올리면 안 됩니다! `.gitignore` 처리나 환경 변수 사용을 권장합니다. (이건 포트폴리오용이라 간단히...)

3.  **애플리케이션 실행:**
    ```bash
    ./gradlew bootRun
    ```
    (또는 IDE에서 메인 클래스 실행)

4.  **애플리케이션 접속:** 기본 포트 `http://localhost:8080`

## 📖 API 엔드포인트 명세

API 상세 명세 및 테스트는 Swagger UI에서 할 수 있습니다.

* **Swagger UI:** `http://localhost:8080/swagger-ui.html`

**주요 엔드포인트:**

* `GET /api/v1/livestock-prices/query`: 축산물 가격 조건 조회
    * Query Params: `productClsCode` (기본:02), `countryCode` (기본:전국), `regDay` (기본:최근일), `convertKgYn` (기본:N)
* `GET /api/v1/livestock-prices/beef`: 전국 도매 소고기 가격 조회
    * Query Params: `regDay` (기본:최근일)
* `GET /api/v1/livestock-prices/pork`: 전국 도매 돼지고기 가격 조회
    * Query Params: `regDay` (기본:최근일)
* `GET /api/v1/livestock-prices/chicken`: 전국 도매 닭고기 가격 조회
    * Query Params: `regDay` (기본:최근일)

## 🧪 테스트 실행 방법

단위 테스트 코드가 포함되어 있습니다.

```bash
./gradlew test

테스트 결과: build/reports/tests/test/index.html

🤔 개발 과정에서 고민하고 배운 점
WebClient와 반응형 프로그래밍 삽질기:

Mono랑 Flux가 대체 뭔지, map이랑 flatMap은 언제 써야 하는 건지 처음엔 정말 헷갈렸습니다. 특히 에러 상황에서 onErrorResume으로 대체 값을 반환하거나, retryWhen으로 재시도 로직을 만드는 부분이 어려웠습니다.  그래도 비동기 코드가 어떻게 흘러가는지 조금은 감을 잡은 것 같습니다.

외부 API 연동 테스트의 벽:

WebClient를 쓰는 서비스 로직을 단위 테스트하려니, Mockito로 WebClient의 그 긴 연쇄 호출을 다 모킹해야 해서 좀 당황했습니다. StepVerifier로 Mono 검증하는 것도 새로 배웠습니다. (MockWebServer라는 것도 있다는데, 일단 Mockito로 최대한 격리하는 연습부터 했습니다.)

API 설계와 문서화의 중요성:

엔드포인트 URL을 어떻게 설계해야 RESTful한 건지, @RequestParam은 언제 쓰고 @PathVariable은 언제 쓰는지 등등 고민이 많았습니다. Swagger 써보니 API 문서가 자동으로 나와서 편했고, 왜 문서화가 중요한지도 알게 되었습니다.

주석과 로그, 미래의 나를 위해:

"나중에 내가 이 코드를 다시 보면 이해할 수 있을까?" 라는 생각으로 주석을 좀 더 신경 써서 달려고 했습니다. 로그도 어떤 레벨로, 어떤 내용을 찍어야 디버깅할 때 편할지 고민했습니다. (아직 부족하지만 노력 중입니다!)

🚀 향후 개선하고 싶은 부분 (TODO 리스트)
regDay 파라미터 형식 상세 검증: 지금은 서비스에서 LocalDate.parse()로 간단히 하는데, 컨트롤러에서 @DateTimeFormat 써서 더 명확하게 검증/바인딩 처리하기.

품목 코드(itemCode) 관리: 서비스 로직에 하드코딩된 축종별 itemCode들, enum이나 설정 파일로 빼서 관리하기. (이건 진짜 빨리 고쳐야 할 듯)

에러 응답 표준화: 지금은 예외 터지면 간단한 HTTP 상태 코드랑 메시지만 나가는데, 좀 더 상세하고 일관된 에러 DTO 만들어서 @ControllerAdvice로 전역 처리해보기.

캐싱 적용: 자주 찾는 데이터나 잘 안 바뀌는 데이터는 캐싱하면 API 응답 속도도 빨라지고 외부 API 호출도 줄일 수 있을 것 같음. (Redis 같은 거 써보고 싶네요.)

인증/인가 (필요하다면): 실제 서비스라면 API 키 말고 더 제대로 된 인증/인가가 필요할 듯. 
