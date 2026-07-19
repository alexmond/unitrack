package org.alexmond.unitrack.web.scm;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ingest path fans out over every {@link ScmPublisher} bean, so a provider is wired
 * in purely by existing. Guards that contract: a provider dropping off the list would
 * silently stop publishing rather than fail anything.
 */
@SpringBootTest
class ScmPublisherWiringTest {

	@Autowired
	private List<ScmPublisher> publishers;

	@Test
	void everyProviderIsRegisteredInOrder() {
		assertThat(this.publishers).extracting(ScmPublisher::providerName).containsExactly("GitHub", "GitLab");
	}

}
