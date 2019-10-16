package org.springframework.cloud.rsocket.sample.ping;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.rsocket.client.BrokerClient;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class PingService implements ApplicationListener<PayloadApplicationEvent<RSocketRequester>> {

  private final BrokerClient gatewayClient;
  private final PingProperties pingProperties;
  private final AtomicInteger pongsReceived = new AtomicInteger();

  public PingService(BrokerClient client, PingProperties properties) {
    this.gatewayClient = client;
    this.pingProperties = properties;
  }

  @Override
  public void onApplicationEvent(PayloadApplicationEvent<RSocketRequester> event) {
    log.info("Starting Ping for Route ID: {} - Request type: {}", gatewayClient.getProperties().getRouteId(), pingProperties.getRequestType());
    RSocketRequester requester = event.getPayload();

    switch (pingProperties.getRequestType()) {
      case REQUEST_CHANNEL:
        Flux<String> pingFlux = getPingFlux();
        requestPongFlux(requester, pingFlux);
        break;
        
      case REQUEST_RESPONSE:
        Flux.interval(Duration.ofSeconds(1))
            .flatMap(tick -> {
               String ping = getPing(tick);
               return requestPong(requester, ping);
            })
            .subscribe();
        break;
  
      case ACTUATOR:
        requester.route("hello").metadata(gatewayClient.forwarding(fwd -> fwd.serviceName("gateway").disableProxy()))
            .data("ping").retrieveMono(String.class).doOnNext(s -> log.info("received from actuator: " + s)).then()
            .block();
        break;
    }
  }

  private Mono<String> requestPong(RSocketRequester requester, String ping) {
    return requester.route("pong-rr")
              .metadata(gatewayClient.forwarding("pong"))
              .data(ping)
              .retrieveMono(String.class)
              .doOnNext(this::logPongs);
  }

  private void requestPongFlux(RSocketRequester requester, Flux<String> pingFlux) {
    requester.route("pong-rc")
        .data(pingFlux)
        .retrieveFlux(String.class) // expect back a continuous stream of pongs.
        .doOnNext(this::logPongs)   // log the pongs as they come in.
        .doOnError((exception) -> { // log any error that may occur.
          log.error("Received an error from server.", exception);
          log.error("Retrying");
        })
        .retry() // retry, i.e. re-subscribe indefinitely in case of errors.
        .subscribe();
  }
  
  private Flux<String> getPingFlux() {
           // Forever, with a period of 1 second...
    return Flux.interval(Duration.ofSeconds(1))  
               // ... repeatedly send the Ping payload ...
               .map(this::getPing)           
               // ... and if the server pushes back, drop the ping.
               .onBackpressureDrop(payload -> log.info("Backpressure applied, dropping payload " + payload))
               // ... log any error that might occur.
               .doOnError((exception) -> {
                 log.info("Error received in ping flux.", exception);
               });
  }

  private String getPing(long i) {
    return "ping" + i;
  }

  private void logPongs(String payload) {
    int received = pongsReceived.incrementAndGet();
    log.info("received {}({}) in Ping for route ID {}", payload, received, gatewayClient.getProperties().getRouteId());
  }
}
