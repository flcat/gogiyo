# 축산물 가격 정보 조회 API (GogiyoPriceAPI)

Spring Boot와 WebFlux(또는 MVC)를 사용해 외부 API에서 축산물 가격 정보를 가져오는 간단한 REST API입니다. 개인적으로 반응형 프로그래밍에 도전해보고 싶어서 시작한 프로젝트입니다.

## 🚀 개발 배경 및 목적

* **학습 목표:** Spring WebFlux, `WebClient`, Project Reactor (`Mono`, `Flux`)를 사용한 비동기/논블로킹 API 연동 실습. (기존에는 `RestTemplate`만 써봤는데, 이번에 큰맘 먹고 도전!)
* **외부 API 연동 경험:** 실제 공공데이터 API를 다뤄보면서 데이터 요청, 응답 처리, 예외 처리 등을 경험하고 싶었습니다.
* **포트폴리오:** 학습한 내용을 바탕으로 실제 동작하는 API를 만들어 포트폴리오로 활용.

## ✨ 주요 기능

* **축산물 가격 정보 조회:** 전국 도매 기준 소고기, 돼지고기, 닭고기 가격 정보 제공.
    * 일반 조건 조회: 구분(도매/소매 - 현재는 도매 위주), 지역, 날짜 등 조합 가능.
    * 축종별 간편 조회: `/beef`, `/pork`, `/chicken` 엔드포인트로 쉽게 조회.
* **비동기 처리:** `WebClient`로 외부 API 비동기 호출 및 응답 처리.
* **에러 처리 및 재시도:** 외부 API 서버 불안정성에 대비해 5xx 에러 시 재시도 로직 구현. (이 부분에서 `retryWhen` 이해하느라 좀 헤맸습니다 😅)
* **API 문서화:** Swagger (SpringDoc OpenAPI)로 API 명세 자동 생성 및 UI 제공.

## 🛠️ 기술 스택

* **언어:** Java 17
* **프레임워크:** Spring Boot 3.x.x
    * Spring WebFlux (Netty 기반) - *실제 프로젝트가 MVC 기반이라면 "Spring MVC (Tomcat 기반)"으로 수정해주세요.*
    * Project Reactor (`Mono`, `Flux` 사용)
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
    * `src/main/resources/application.properties` (또는 `application.yml`) 파일을 열고 아래 항목에 실제 외부 API에서 발급받은 키와 ID를 입력해주세요.
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

## 🤔 개발 과정에서 고민하고 배운 점

* **`WebClient`와 반응형 프로그래밍 삽질기:**
    * `Mono`랑 `Flux`가 대체 뭔지, `map`이랑 `flatMap`은 언제 써야 하는 건지 처음엔 정말 헷갈렸습니다. 특히 에러 상황에서 `onErrorResume`으로 대체 값을 반환하거나, `retryWhen`으로 재시도 로직을 만드는 부분이 어려웠습니다. (스택오버플로우랑 공식 문서 엄청 뒤졌네요.) 그래도 비동기 코드가 어떻게 흘러가는지 조금은 감을 잡은 것 같습니다.
* **외부 API 연동 테스트의 벽:**
    * `WebClient`를 쓰는 서비스 로직을 단위 테스트하려니, Mockito로 `WebClient`의 그 긴 연쇄 호출을 다 모킹해야 해서 좀 당황했습니다. `StepVerifier`로 `Mono` 검증하는 것도 새로 배웠습니다. (`MockWebServer`라는 것도 있다는데, 일단 Mockito로 최대한 격리하는 연습부터 했습니다.)
* **API 설계와 문서화의 중요성:**
    * 엔드포인트 URL을 어떻게 설계해야 RESTful한 건지, `@RequestParam`은 언제 쓰고 `@PathVariable`은 언제 쓰는지 등등 고민이 많았습니다. Swagger 써보니 API 문서가 자동으로 나와서 편했고, 왜 문서화가 중요한지도 알게 되었습니다.
* **주석과 로그, 미래의 나를 위해:**
    * "나중에 내가 이 코드를 다시 보면 이해할 수 있을까?" 라는 생각으로 주석을 좀 더 신경 써서 달려고 했습니다. 로그도 어떤 레벨로, 어떤 내용을 찍어야 디버깅할 때 편할지 고민했습니다. (아직 부족하지만 노력 중입니다!)

아직 부족한 점도 많고, "향후 개선하고 싶은 부분"에 적어둔 것처럼 더 발전시켜야 할 부분도 많지만, 이번 "고기요" API 프로젝트를 통해 정말 많은 것을 배우고 경험할 수 있었습니다. 특히 **비동기 프로그래밍과 테스트 코드 작성에 대한 두려움을 조금이나마 극복할 수 있었던 것이 가장 큰 수확인 것 같습니다.** 앞으로도 꾸준히 학습하고 발전하는 개발자가 되겠습니다! 😊

## 🚀 향후 개선하고 싶은 부분 (TODO 리스트)

* `regDay` **파라미터 형식 상세 검증:** 지금은 서비스에서 `LocalDate.parse()`로 간단히 하는데, 컨트롤러에서 `@DateTimeFormat` 써서 더 명확하게 검증/바인딩 처리하기.
* **품목 코드(itemCode) 관리:** 서비스 로직에 하드코딩된 축종별 `itemCode`들, `enum`이나 설정 파일로 빼서 관리하기. (이건 진짜 빨리 고쳐야 할 듯)
* **에러 응답 표준화:** 지금은 예외 터지면 간단한 HTTP 상태 코드랑 메시지만 나가는데, 좀 더 상세하고 일관된 에러 DTO 만들어서 `@ControllerAdvice`로 전역 처리해보기.
* **캐싱 적용:** 자주 찾는 데이터나 잘 안 바뀌는 데이터는 캐싱하면 API 응답 속도도 빨라지고 외부 API 호출도 줄일 수 있을 것 같음. (Redis 같은 거 써보고 싶네요.)
* **모니터링 환경 구성 고려:**
    * Spring Boot Actuator를 통해 애플리케이션의 기본적인 상태(health, metrics 등)를 노출하고,
    * Micrometer를 사용하여 주요 API 호출 횟수, 응답 시간, 외부 API 호출 성공/실패율 같은 커스텀 메트릭을 수집하는 방법을 학습하고 적용해보고 싶습니다.
    * 수집된 메트릭을 Prometheus로 저장하고 Grafana로 시각화하는 대시보드를 구성해보는 것도 좋은 경험이 될 것 같습니다. (아직은 막연하지만, 운영 환경에서는 필수라고 들어서 관심이 많습니다.)
* **인증/인가 (필요하다면):** 실제 서비스라면 API 키 말고 더 제대로 된 인증/인가가 필요할 듯. (OAuth2 같은 거?)

---

이 README가 프로젝트 이해하는 데 도움이 되길! 피드백은 언제든 환영. 😊
