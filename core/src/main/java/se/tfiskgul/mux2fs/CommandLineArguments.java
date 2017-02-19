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

import java.nio.file.Path;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CommandLineArguments {

	private static final String USAGE = "Usage: mux2fs source mountpoint [options] \nTry `mux2fs -h' or `mux2fs --help' for more information.";
	//
	@Parameter(names = "--source", description = "Source directory to mirror", required = true)
	private Path source;
	@Parameter(names = "--mountpoint", description = "Mount point", required = true)
	private Path mountPoint;
	@Parameter(names = "--tempdir", description = "Temporary directory under which to mux files", required = true)
	private Path tempDir;
	@Parameter(names = { "-h", "--help" }, description = "Help", help = true)
	private boolean help = false;

	public Path getTempDir() {
		return tempDir;
	}

	public Path getSource() {
		return source;
	}

	public Path getMountPoint() {
		return mountPoint;
	}

	public String getUsage() {
		return USAGE;
	}

	public boolean isHelp() {
		return help;
	}

	public String getHelp() {
		return "FIXME"; // FIXME: Halp!
	}

	public void validate() {
		validateDirectoryExists(source);
		validateDirectoryExists(mountPoint);
		validateDirectoryExists(tempDir);
	}

	private void validateDirectoryExists(Path directory) {
		if (!directory.toFile().isDirectory()) {
			throw new IllegalArgumentException(directory + " doesn't exist, or is not a directory!");
		}
	}

	static CommandLineArguments parse(String[] args) {
		CommandLineArguments arguments = new CommandLineArguments();
		try {
			new JCommander(arguments, args);
		} catch (ParameterException e) {
			System.err.println(arguments.getUsage());
			throw e;
		}
		return arguments;
	}
}
