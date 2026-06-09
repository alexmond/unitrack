package org.alexmond.unitrack.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GateCommandTest {

	private final UploadClient client = mock(UploadClient.class);

	private GateCommand command() {
		GateCommand c = new GateCommand(this.client);
		c.url = "http://unitrack.test";
		c.project = "demo";
		c.commit = "abc123";
		return c;
	}

	@Test
	void passesWhenGatePassed() {
		given(this.client.gate(any(), any(), any(), any(), any(), any()))
			.willReturn(new GateResponse(true, true, "PASSED"));
		assertThat(command().call()).isEqualTo(ExitCodes.OK);
	}

	@Test
	void failsWhenGateFailed() {
		given(this.client.gate(any(), any(), any(), any(), any(), any()))
			.willReturn(new GateResponse(true, false, "FAILED"));
		assertThat(command().call()).isEqualTo(ExitCodes.GATE_FAILED);
	}

	@Test
	void usageErrorWhenNoRunFound() {
		given(this.client.gate(any(), any(), any(), any(), any(), any()))
			.willReturn(new GateResponse(false, false, null));
		assertThat(command().call()).isEqualTo(ExitCodes.USAGE);
	}

	@Test
	void propagatesTransportFailure() {
		given(this.client.gate(any(), any(), any(), any(), any(), any()))
			.willThrow(new UploadException(ExitCodes.TRANSPORT, "down"));
		assertThat(command().call()).isEqualTo(ExitCodes.TRANSPORT);
	}

}
