package org.alexmond.unitrack.cli;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Top-level {@code unitrack} command; dispatches to its subcommands. */
@Component
@Command(name = "unitrack", mixinStandardHelpOptions = true,
		description = "Push test, coverage and performance results to a UniTrack server.",
		subcommands = { UploadCommand.class, GateCommand.class })
public class UnitrackCommand implements Callable<Integer> {

	@Override
	public Integer call() {
		// No subcommand given — show usage and signal a usage error.
		CommandLine.usage(this, System.out);
		return ExitCodes.USAGE;
	}

}
