package com.cherrywork.worknet.aspect;

import java.time.LocalTime;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;

@RestController
@Slf4j
public class SSEController {
	Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

	@GetMapping("/send/{userName}")
	public void publishEventToSSE(@PathVariable(required = false) String userName) {
		EmitResult result = sink.tryEmitNext("Published Event for " + userName);

		if (result.isFailure()) {
			log.debug("Failed");
		}
	}

	@RequestMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> sse() {
		return sink.asFlux().map(e -> ServerSentEvent.builder(e).id("SSE - " + LocalTime.now().toString())
				.event("periodic-event").data(e).comment("event").build());
	}

}
