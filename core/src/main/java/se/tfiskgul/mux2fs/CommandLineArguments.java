/*
MIT License

Copyright (c) 2017 Carl-Frederik Hallberg

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package se.tfiskgul.mux2fs;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;

public abstract class CommandLineArguments {

	private static final String USAGE = "" //
			+ "Usage: mux2fs source mountpoint -o tempdir=<tempdir>,[options]\n" //
			+ "Usage: mux2fs --source source --target mountpoint --tempdir tempdir [options]\n" //
			+ "Try `mux2fs -h' or `mux2fs --help' for more information.";

	private CommandLineArguments() {
	}

	public static String getUsage() {
		return USAGE;
	}

	private static class Shared {

		@Parameter(names = "--source", description = "Source directory to mirror", required = true)
		private Path source;
		@Parameter(names = "--target", description = "Mount point", required = true)
		private Path target;
		@Parameter(names = { "-h", "--help" }, description = "Help", help = true)
		private boolean help = false;
		@Parameter(names = { "-v", "--version" }, description = "Version", help = true)
		private boolean version = false;

		public Path getSource() {
			return source;
		}

		public Path getTarget() {
			return target;
		}

		public boolean isHelp() {
			return help;
		}

		public String getHelp() {
			return USAGE; // TODO: Help chapter
		}

		public boolean isVersion() {
			return version;
		}

		public String getVersion() {
			return getManifestAttribute("Implementation-Version").orElse("unknown");
		}

		private Optional<String> getManifestAttribute(String attribute) {
			try {
				URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
				Manifest manifest = new Manifest(new URL("jar:" + location.toString() + "!/META-INF/MANIFEST.MF").openStream());
				Attributes attributes = manifest.getMainAttributes();
				return Optional.ofNullable(attributes.getValue(attribute));
			} catch (RuntimeException | IOException e) {
				return Optional.empty();
			}
		}
	}

	public static class Strict extends Shared {

		@Parameter(names = "--tempdir", description = "Temporary directory under which to mux files", required = true)
		private Path tempDir;
		@Parameter(names = "-o", description = "Options", required = false)
		private List<String> options;
		private Options mux2fsOptions;
		private List<String> passThroughOptions;

		public Path getTempDir() {
			return tempDir;
		}

		public void validate() {
			validateDirectoryExists(getSource());
			validateDirectoryExists(getTarget());
			validateDirectoryExists(getTempDir());
		}

		public Options getMux2fsOptions() {
			return mux2fsOptions;
		}

		public List<String> getPassThroughOptions() {
			return passThroughOptions;
		}
	}

	private static class Lax extends Shared {
		@Parameter(names = "--tempdir", description = "Temporary directory under which to mux files", required = false)
		private Path tempDir;
		@Parameter(names = "-o", description = "Options", required = true)
		private List<String> options;
	}

	@Parameters(separators = "=")
	public static class Options {

		@Parameter(names = "-rw")
		private boolean rw;
		@Parameter(names = "-tempdir")
		private String tempdir;

		public boolean isRw() {
			return rw;
		}
	}

	public static Strict parse(String[] argsArray) {
		List<String> args = Arrays.asList(argsArray);
		Strict strict = CommandLineArguments.tryStrictParse(args).orElseGet(() -> parseFromLax(args));
		if (strict.options != null) {
			Options options = new Options();
			JCommander parseIgnoreUnknown = parseIgnoreUnknown(options, strict.options.stream().map(o -> "-" + o).collect(toList()));
			strict.mux2fsOptions = options;
			strict.passThroughOptions = parseIgnoreUnknown.getUnknownOptions().stream().map(o -> o.substring(1, o.length())).collect(toList());
		}
		return strict;
	}

	private static void validateDirectoryExists(Path directory) {
		if (!directory.toFile().isDirectory()) {
			throw new IllegalArgumentException(directory + " doesn't exist, or is not a directory!");
		}
	}

	private static Optional<CommandLineArguments.Strict> tryStrictParse(List<String> args) {
		Strict strict = new Strict();
		try {
			parse(strict, args);
			return Optional.of(strict);
		} catch (ParameterException e) {
			return Optional.empty();
		}
	}

	private static Strict parseFromLax(List<String> args) {
		if (args.size() < 3) {
			throw new ParameterException("Must supply at least 3 parameters!");
		}
		List<String> withSourceAndTarget = ImmutableList.<String> builder().add("--source").add(args.get(0)).add("--target").add(args.get(1))
				.addAll(args.stream().skip(2).iterator()).build();
		Lax lax = new Lax();
		parse(lax, withSourceAndTarget);
		Options options = new Options();
		parseIgnoreUnknown(options, lax.options.stream().map(o -> "-" + o).collect(toList()));
		List<String> withTempDir = ImmutableList.<String> builder().addAll(withSourceAndTarget).add("--tempdir").add(options.tempdir).build();
		Strict strict = new Strict();
		parse(strict, withTempDir);
		return strict;
	}

	private static JCommander parse(Object obj, List<String> args) {
		return new JCommander(obj, args.toArray(new String[args.size()]));
	}

	private static JCommander parseIgnoreUnknown(Object obj, List<String> args) {
		JCommander jCommander = new JCommander();
		jCommander.setAcceptUnknownOptions(true);
		jCommander.addObject(obj);
		jCommander.parse(args.toArray(new String[args.size()]));
		return jCommander;
	}
}
