package flcat.gogiyo.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
public class WebClientConfig {

    // HttpClient 타임아웃 설정값
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 10;
    private static final int READ_WRITE_TIMEOUT_SECONDS = 0;

    // 만약 여러 외부 Api를 호출한다면, 각각의 WebClient Bean을 설정하거나
    // baseUrl을 주입받아 동적으로 생성하는 방식을 고려할 수 있음
    // 여기서는 하나의 WebClient Bean을 공통으로 사용함.
    @Bean("defaultApiWebClient") //다른 WebClient Bean의 추가를 고려해 이름 지정
    public WebClient customWebClient(WebClient.Builder webClientBuilder) { //WebClient.Builder 주입받아 사용
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
            .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
            .doOnConnected(connect -> {
                log.debug("HttpClient connected. Setting read/write timeouts.");
                connect.addHandlerLast(
                        new ReadTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .addHandlerLast(
                        new WriteTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            });

        // Api 응답이 매우 클 경우(용량)에 대비한 버퍼 사이즈 조정.
        // 보통 Json Api에서는 256kb(기본값)으로 충분할듯??
        // 2MB로 조금 늘려봄
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

        log.info("Custom WebClient Bean created with connectTimeout={}ms, responseTimeout={}s",
            CONNECT_TIMEOUT_MS, RESPONSE_TIMEOUT_SECONDS);

        return webClientBuilder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(exchangeStrategies)
            .defaultHeader(HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE) // 기본 요청 헤더 설정
            .defaultHeader(HttpHeaders.USER_AGENT, "AppClient/1.0") //어떤 클라이언트가 요청했는지 서버에 알림
            .build();

    }
}
