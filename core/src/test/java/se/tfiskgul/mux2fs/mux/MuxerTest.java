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
package se.tfiskgul.mux2fs.mux;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import se.tfiskgul.mux2fs.Fixture;
import se.tfiskgul.mux2fs.mux.Muxer.ProcessBuilderFactory;
import se.tfiskgul.mux2fs.mux.Muxer.State;

@PowerMockIgnore({ "org.powermock.*", "org.mockito.*", "javax.management.*", "org.jacoco.agent.rt.*" })
@RunWith(PowerMockRunner.class)
@PrepareForTest({ ProcessBuilder.class, Muxer.class })
@PowerMockRunnerDelegate(BlockJUnit4ClassRunner.class)
public class MuxerTest extends Fixture {

	@Rule
	public final ExpectedException exception = ExpectedException.none();
	private FileSystem fileSystem;
	private FileSystemProvider provider;
	private Path root;
	private Path mkv;
	private Path srt;
	private Path tempDir;
	private ProcessBuilderFactory factory;
	private ProcessBuilder builder;
	private Process process;

	@Before
	public void beforeTest()
			throws Exception {
		fileSystem = mockFileSystem();
		provider = fileSystem.provider();
		root = mockPath("", fileSystem);
		mkv = mockPath(root, "mkv.mkv");
		srt = mockPath(root, "srt.srt");
		tempDir = mockPath(root, "tmp");
		factory = mock(ProcessBuilderFactory.class);
		builder = PowerMockito.mock(ProcessBuilder.class);
		when(factory.from(Matchers.<String> anyVararg())).thenReturn(builder);
		when(builder.directory(any())).thenReturn(builder);
		process = mock(Process.class);
	}

	@Test
	public void testConstructor()
			throws Exception {
		// Given
		// When
		Muxer muxer = Muxer.of(mkv, srt, tempDir);
		// Then
		assertThat(muxer).isNotNull();
	}

	@Test
	public void testMkvMustBeReadable()
			throws Exception {
		doThrow(new NoSuchFileException(null)).when(provider).checkAccess(mkv, AccessMode.READ);
		exception.expect(NoSuchFileException.class);
		Muxer.of(mkv, srt, tempDir).start();
	}

	@Test
	public void testSrtMustBeReadable()
			throws Exception {
		doThrow(new NoSuchFileException(null)).when(provider).checkAccess(srt, AccessMode.READ);
		exception.expect(NoSuchFileException.class);
		Muxer.of(mkv, srt, tempDir).start();
	}

	@Test
	public void testempDirMustBeWriteable()
			throws Exception {
		doThrow(new NoSuchFileException(null)).when(provider).checkAccess(tempDir, AccessMode.WRITE);
		exception.expect(NoSuchFileException.class);
		Muxer.of(mkv, srt, tempDir).start();
	}

	@Test
	public void testStart()
			throws Exception {
		// Given
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		// When
		muxer.start();
		// Then
		verify(factory).from("mkvmerge", "-o", muxer.getOutput().get().toString(), mkv.toString(), srt.toString());
		// TODO: These are broken because of a bug in Mockito / Powermock
		// verify(builder).directory(tempDir.toFile());
		// verify(builder).start();
	}

	@Test
	@SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
	public void testStartIoExceptionGivesFailedState()
			throws Exception {
		// Given
		when(builder.start()).thenThrow(new IOException());
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		// When
		try {
			muxer.start();
			fail("This must throw IOException");
		} catch (IOException e) { // Ignored
		}
		// Then
		verify(factory).from("mkvmerge", "-o", muxer.getOutputForTest().toString(), mkv.toString(), srt.toString());
		assertThat(muxer.state()).isEqualTo(State.FAILED);
		verify(muxer.getOutputForTest().toFile()).delete();
		// TODO: These are broken because of a bug in Mockito / Powermock
		// verify(builder).directory(tempDir.toFile());
		// verify(builder).start();
	}

	@Test
	public void testStartStateChangeSuccess()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(false);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		State state = muxer.state();
		// Then
		assertThat(state).isEqualTo(State.SUCCESSFUL);
		assertThat(muxer.getOutput()).isNotEmpty();
	}

	@Test
	@SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
	public void testStartStateChangeFailed()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(false);
		when(process.exitValue()).thenReturn(-1);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		State state = muxer.state();
		// Then
		assertThat(state).isEqualTo(State.FAILED);
		assertThat(muxer.getOutput()).isEmpty();
		verify(muxer.getOutputForTest().toFile()).delete();
	}

	@Test
	public void testWaitForNonRunningFailed()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(false);
		when(process.exitValue()).thenReturn(-33);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		int exitCode = muxer.waitFor();
		// Then
		assertThat(exitCode).isEqualTo(-33);
		verify(process, times(2)).exitValue();
		verify(process).isAlive();
		verifyNoMoreInteractions(process);
	}

	@Test
	public void testWaitForRunningFailed()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(true);
		when(process.waitFor()).thenReturn(-33);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		int exitCode = muxer.waitFor();
		// Then
		assertThat(exitCode).isEqualTo(-33);
		verify(process).waitFor();
		verify(process).isAlive();
		verifyNoMoreInteractions(process);
	}

	@Test
	public void testWaitForRunningSuccessful()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(true);
		when(process.waitFor()).thenReturn(SUCCESS);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		int exitCode = muxer.waitFor();
		// Then
		assertThat(exitCode).isEqualTo(SUCCESS);
		verify(process).waitFor();
		verify(process).isAlive();
		verifyNoMoreInteractions(process);
	}

	@Test
	public void testWaitForNonRunningSuccessful()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(false);
		when(process.waitFor()).thenReturn(SUCCESS);
		when(process.exitValue()).thenReturn(SUCCESS);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		int exitCode = muxer.waitFor();
		// Then
		assertThat(exitCode).isEqualTo(SUCCESS);
		verify(process).isAlive();
		verify(process).exitValue();
		verify(process).waitFor();
		verifyNoMoreInteractions(process);
	}

	@Test
	public void testWaitForTimeoutNonRunningSuccessful()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(false);
		when(process.exitValue()).thenReturn(SUCCESS);
		when(process.waitFor(anyLong(), any())).thenReturn(true);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		boolean result = muxer.waitFor(1, NANOSECONDS);
		// Then
		assertThat(result).isTrue();
		verify(process).isAlive();
		verify(process).exitValue();
		verify(process).waitFor(1, NANOSECONDS);
		verifyNoMoreInteractions(process);
	}

	@Test
	public void testWaitForTimeoutRunningSuccessful()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(true);
		when(process.waitFor(anyLong(), any())).thenReturn(true);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		boolean result = muxer.waitFor(1, NANOSECONDS);
		// Then
		assertThat(result).isTrue();
		verify(process).isAlive();
		verify(process).waitFor(1, NANOSECONDS);
		verifyNoMoreInteractions(process);
	}

	@Test
	public void testWaitForTimeoutNonRunningFailed()
			throws Exception {
		// Given
		when(process.isAlive()).thenReturn(false);
		when(process.exitValue()).thenReturn(-3425);
		when(process.waitFor(anyLong(), any())).thenReturn(true);
		when(builder.start()).thenReturn(process);
		Muxer muxer = Muxer.of(mkv, srt, tempDir, factory);
		muxer.start();
		// When
		boolean result = muxer.waitFor(1, NANOSECONDS);
		// Then
		assertThat(result).isTrue();
		verify(process).isAlive();
		verify(process).exitValue();
		verifyNoMoreInteractions(process);
	}
}
