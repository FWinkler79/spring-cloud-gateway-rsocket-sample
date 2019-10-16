package org.springframework.cloud.rsocket.sample.pong;

import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PongService {

	private final Environment env;

	public PongService(Environment env) {
		this.env = env;
	}

	@EventListener
	public void onRSocketRequester(RSocketRequester requester) {
		Boolean isClient = env.getProperty("pong.client", Boolean.class, true);
		log.info("Starting Pong isClient: {}", isClient);
	}
}

