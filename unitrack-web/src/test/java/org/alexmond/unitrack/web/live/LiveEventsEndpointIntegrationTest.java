package org.alexmond.unitrack.web.live;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * The /api/v1/events stream opens as an async SSE response and registers a subscriber.
 */
@SpringBootTest
class LiveEventsEndpointIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private LiveEventService liveEvents;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void eventsEndpointStartsAsyncAndRegistersSubscriber() throws Exception {
		int before = this.liveEvents.subscriberCount();
		mvc().perform(get("/api/v1/events")).andExpect(request().asyncStarted());
		assertThat(this.liveEvents.subscriberCount()).isEqualTo(before + 1);
	}

}
