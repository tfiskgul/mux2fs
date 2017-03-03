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
package se.tfiskgul.mux2fs.fs.mirror;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.fs.base.FileHandleFiller;

@PowerMockIgnore({ "javax.management.*", "org.jacoco.agent.rt.*" })
@RunWith(PowerMockRunner.class)
@PrepareForTest({ FileChannel.class, MirrorFs.class })
@PowerMockRunnerDelegate(BlockJUnit4ClassRunner.class)
public class MirrorFsPmTests extends MirrorFsFixture {

	@Test
	public void testReleaseClosesOpenFileChannel()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		FileChannel fileChannel = PowerMockito.mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		fs.open("foo.bar", filler);
		// When
		int result = fs.release("foo.bar", handleCaptor.getValue());
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileChannel).close();
		verifyNoMoreInteractions(fileChannel);
	}

	@Test
	public void testRelease()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(PowerMockito.mock(FileChannel.class));
		fs.open("foo.bar", filler);
		// When
		int result = fs.release("foo.bar", handleCaptor.getValue());
		// Then
		assertThat(result).isEqualTo(SUCCESS);
	}

	@Test
	public void testReleaseTwice()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(PowerMockito.mock(FileChannel.class));
		fs.open("foo.bar", filler);
		fs.release("foo.bar", handleCaptor.getValue());
		// When
		int result = fs.release("foo.bar", handleCaptor.getValue());
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EBADF());
	}

	@Test
	public void testDestroyClosesFileChannels()
			throws Exception {
		// Given
		FileChannel foo = mockAndOpen("foo");
		FileChannel bar = mockAndOpen("bar");
		// When
		fs.destroy();
		// Then
		verify(foo).close();
		verifyNoMoreInteractions(foo);
		verify(bar).close();
		verifyNoMoreInteractions(bar);
	}

	private FileChannel mockAndOpen(String name)
			throws IOException {
		Path path = mockPath(mirrorRoot, name);
		FileChannel fileChannel = PowerMockito.mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(path), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		int result = fs.open(name, mock(FileHandleFiller.class));
		assertThat(result).isEqualTo(SUCCESS);
		return fileChannel;
	}
}
