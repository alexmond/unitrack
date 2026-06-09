package org.alexmond.unitrack.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitrackCommandTest {

	@Test
	void withoutASubcommandReturnsUsage() {
		assertThat(new UnitrackCommand().call()).isEqualTo(ExitCodes.USAGE);
	}

}
