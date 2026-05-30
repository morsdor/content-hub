package com.contenthub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

	// 3 total attempts (1 original + 2 retries), 1 s apart.
	// Poison messages that still fail are sent to <topic>.DLT — prevents consumer
	// stall.
	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
		var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
		return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2L));
	}
}
