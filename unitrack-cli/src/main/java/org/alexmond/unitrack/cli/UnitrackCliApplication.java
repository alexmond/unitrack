package org.alexmond.unitrack.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Entry point for the UniTrack CI uploader CLI. A Spring Boot application (so it ships as
 * an executable jar and a buildpacks OCI image, same as the server) that dispatches to
 * picocli commands wired through the Spring context, then exits with the command's status
 * code.
 */
@SpringBootApplication
public class UnitrackCliApplication implements CommandLineRunner, ExitCodeGenerator {

	private final UnitrackCommand root;

	private final IFactory factory;

	private int exitCode;

	public UnitrackCliApplication(UnitrackCommand root, ApplicationContext context) {
		this.root = root;
		// Resolve picocli command objects as Spring beans (so they get their
		// dependencies),
		// falling back to picocli's default factory for everything else.
		this.factory = new IFactory() {
			@Override
			public <K> K create(Class<K> cls) throws Exception {
				try {
					return context.getBean(cls);
				}
				catch (RuntimeException ex) {
					return CommandLine.defaultFactory().create(cls);
				}
			}
		};
	}

	@Override
	public void run(String... args) {
		this.exitCode = new CommandLine(this.root, this.factory).setCaseInsensitiveEnumValuesAllowed(true)
			.execute(args);
	}

	@Override
	public int getExitCode() {
		return this.exitCode;
	}

	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(UnitrackCliApplication.class, args)));
	}

}
