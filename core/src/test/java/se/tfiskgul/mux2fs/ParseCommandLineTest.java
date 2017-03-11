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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.beust.jcommander.ParameterException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import se.tfiskgul.mux2fs.CommandLineArguments.Strict;

@SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
public class ParseCommandLineTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void testNoParametersParseFails() {
		exception.expect(ParameterException.class);
		CommandLineArguments.parse(array());
	}

	@Test
	public void testParseMandatory() {
		Strict result = CommandLineArguments.parse(array("--target", "/tmp/mnt", "--source", "/mnt/source", "--tempdir", "/tmp/dir"));
		assertThat(result.getTarget()).isEqualTo(Paths.get("/tmp/mnt"));
		assertThat(result.getSource()).isEqualTo(Paths.get("/mnt/source"));
		assertThat(result.getTempDir()).isEqualTo(Paths.get("/tmp/dir"));
	}

	@Test
	public void testHelp() {
		Strict result = CommandLineArguments.parse(array("-h"));
		assertThat(result.isHelp()).isTrue();
		assertThat(result.getHelp()).isNotEmpty();
	}

	@Test
	public void testValidateDirectoriesDoesNotExists() {
		Strict result = CommandLineArguments.parse(array("--target", "/total/nonsense", "--source", "/total/nonsense", "--tempdir", "/total/nonsense"));
		exception.expect(IllegalArgumentException.class);
		result.validate();
	}

	@Test
	public void testValidateDirectoriesExists() {
		Strict result = CommandLineArguments.parse(array("--target", "/", "--source", "/", "--tempdir", "/"));
		result.validate();
	}

	@Test
	public void testParseOptions() {
		Strict result = CommandLineArguments.parse(array( //
				"--target", "/tmp/mnt", "--source", "/mnt/source", "--tempdir", "/tmp/dir", //
				"-o", "param1=one,param2=two,param3=long param,ro"));
		assertThat(result.getPassThroughOptions()).containsExactlyInAnyOrder("param1=one", "param2=two", "param3=long param", "ro");
	}

	@Test
	public void testParseMultipleOptions() {
		Strict result = CommandLineArguments.parse(array( //
				"--target", "/tmp/mnt", "--source", "/mnt/source", "--tempdir", "/tmp/dir", //
				"-o", "param1=one,param2=two", "-o", "param3=long param,ro"));
		assertThat(result.getPassThroughOptions()).containsExactlyInAnyOrder("param1=one", "param2=two", "param3=long param", "ro");
	}

	@Test
	public void testFstabStyleManyOptions() {
		Strict result = CommandLineArguments.parse(array("source", "target", "-o", "rw", "-o", "tempdir=sometempdirpath,one=1,two=2", "-o", "three=3"));
		assertThat(result.getSource()).isEqualTo(Paths.get("source"));
		assertThat(result.getTarget()).isEqualTo(Paths.get("target"));
		assertThat(result.getTempDir()).isEqualTo(Paths.get("sometempdirpath"));
		assertThat(result.getPassThroughOptions()).containsExactlyInAnyOrder("one=1", "two=2", "three=3");
		assertThat(result.getMux2fsOptions().isRw()).isTrue();
	}

	@Test
	public void testArgumentsInFstabStyle() {
		Strict result = CommandLineArguments.parse(array("source", "target", "-o", "tempdir=sometempdirpath"));
		assertThat(result.getSource()).isEqualTo(Paths.get("source"));
		assertThat(result.getTarget()).isEqualTo(Paths.get("target"));
		assertThat(result.getTempDir()).isEqualTo(Paths.get("sometempdirpath"));
		assertThat(result.getPassThroughOptions()).isEmpty();
	}

	@Test
	public void testGetUsage() {
		assertThat(CommandLineArguments.getUsage()).isNotEmpty();
	}

	@SuppressWarnings("unchecked")
	private <T> T[] array(T... array) {
		return array;
	}
}
