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
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.Fixture;
import se.tfiskgul.mux2fs.fs.decoupling.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.decoupling.FileHandleFiller;
import se.tfiskgul.mux2fs.fs.decoupling.StatFiller;

public class MirrorFsTest extends Fixture {

	private static final int SUCCESS = 0;
	private FileSystem fileSystem;
	private Path mirrorRoot;
	private MirrorFs fs;

	@Before
	public void before() {
		fileSystem = mockFileSystem();
		mirrorRoot = mockPath("/mirror/root/", fileSystem);
		fs = new MirrorFs(mirrorRoot);
	}

	@Test
	public void testGetAttr()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		when(fileSystem.getPath(mirrorRoot.toString(), "/")).thenReturn(mirrorRoot);
		// When
		int result = fs.getattr("/", stat);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(stat).stat(mirrorRoot);
	}

	@Test
	public void testGetAttrSubDir()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		Path foo = mockPath(mirrorRoot, "/foo");
		// When
		int result = fs.getattr("/foo", stat);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(stat).stat(foo);
	}

	@Test
	public void testGetAttrNoSuchFile()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		Path foo = mockPath(mirrorRoot, "/foo");
		when(stat.stat(foo)).thenThrow(NoSuchFileException.class);
		// When
		int result = fs.getattr("/foo", stat);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.ENOENT());
	}

	@Test
	public void testGetAttrNoPerm()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		Path foo = mockPath(mirrorRoot, "/foo");
		when(stat.stat(foo)).thenThrow(AccessDeniedException.class);
		// When
		int result = fs.getattr("/foo", stat);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EPERM());
	}

	@Test
	public void testGetAttrIOException()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		Path foo = mockPath(mirrorRoot, "/foo");
		when(stat.stat(foo)).thenThrow(IOException.class);
		// When
		int result = fs.getattr("/foo", stat);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EIO());
	}

	@Test
	public void testReadDir()
			throws Exception {
		// Given
		Path foo = mockPath(mirrorRoot, "foo");
		Path bar = mockPath(mirrorRoot, "bar");
		mockDirectoryStream(mirrorRoot, foo, bar);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).add("foo", foo);
		verify(filler).add("bar", bar);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testReadDirOnFile()
			throws Exception {
		negativeReadDir(-ErrorCodes.ENOTDIR(), NotDirectoryException.class);
	}

	@Test
	public void testReadDirNoPerm()
			throws Exception {
		negativeReadDir(-ErrorCodes.EPERM(), AccessDeniedException.class);
	}

	@Test
	public void testReadDirNoDirOrFile()
			throws Exception {
		negativeReadDir(-ErrorCodes.ENOENT(), NoSuchFileException.class);
	}

	@Test
	public void testReadDirIoErr()
			throws Exception {
		negativeReadDir(-ErrorCodes.EIO(), IOException.class);
	}

	@Test
	public void testReadDirStopsEnumerationOnResourceExhaustion()
			throws Exception {
		// Given
		Path foo = mockPath(mirrorRoot, "foo");
		Path bar = mockPath(mirrorRoot, "bar");
		mockDirectoryStream(mirrorRoot, foo, bar);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		when(filler.add("foo", foo)).thenReturn(1); // Signify out of buffer memory, stop enumeration please
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).add("foo", foo);
		verifyNoMoreInteractions(filler); // No bar is added, enumeration stopped
	}

	@Test
	public void testReadDirErrorOnFillDot()
			throws Exception {
		// Given
		mockDirectoryStream(mirrorRoot);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		when(filler.add(eq("."), any())).thenThrow(IOException.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testReadDirErrorOnFillDotDot()
			throws Exception {
		// Given
		mockDirectoryStream(mirrorRoot);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		when(filler.add(eq(".."), any())).thenThrow(IOException.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testGetFileNameOnNullPathIsEmpty()
			throws Exception {
		assertThat(fs.getFileName(null)).isEmpty();
	}

	@Test
	public void testGetFileNameOnNullFileNameIsEmpty()
			throws Exception {
		assertThat(fs.getFileName(mock(Path.class))).isEmpty();
	}

	@Test
	public void testReadDirContinuesEnumerationOnError()
			throws Exception {
		// Given
		Path foo = mockPath(mirrorRoot, "foo");
		Path bar = mockPath(mirrorRoot, "bar");
		mockDirectoryStream(mirrorRoot, foo, bar);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		when(filler.add("foo", foo)).thenThrow(IOException.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).add("foo", foo);
		verify(filler).add("bar", bar);
		verifyNoMoreInteractions(filler);
	}

	private void negativeReadDir(int expected, Class<? extends IOException> exceptionType)
			throws IOException {
		// Given
		when(fileSystem.provider().newDirectoryStream(eq(mirrorRoot), any())).thenThrow(exceptionType);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(expected);
	}

	@Test
	public void testOpen()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		// When
		int result = fs.open("foo.bar", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(filler).setFileHandle(gt(0));
		verifyNoMoreInteractions(filler);
		verify(fileSystem.provider()).newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)));
		verifyNoMoreInteractions(fileSystem.provider());
	}

	@Test
	public void testOpenFileHandleIsUnique()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		// When
		int result = fs.open("foo.bar", filler);
		result += fs.open("foo.bar", filler);
		result += fs.open("foo.bar", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(filler, times(3)).setFileHandle(gt(0));
		verifyNoMoreInteractions(filler);
		verify(fileSystem.provider(), times(3)).newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)));
		verifyNoMoreInteractions(fileSystem.provider());
		assertThat(handleCaptor.getAllValues()).hasSize(3).doesNotHaveDuplicates();
	}

	@Test
	public void testOpenNoPerm()
			throws Exception {
		testOpenThrow(-ErrorCodes.EPERM(), AccessDeniedException.class);
	}

	@Test
	public void testOpenNoSuchFile()
			throws Exception {
		testOpenThrow(-ErrorCodes.ENOENT(), NoSuchFileException.class);
	}

	@Test
	public void testOpenIoError()
			throws Exception {
		testOpenThrow(-ErrorCodes.EIO(), IOException.class);
	}

	private void testOpenThrow(int expected, Class<? extends IOException> exceptionType)
			throws IOException {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		when(fileSystem.provider().newFileChannel(any(), eq(set(StandardOpenOption.READ)))).thenThrow(exceptionType);
		// When
		int result = fs.open("/", filler);
		// Then
		assertThat(result).isEqualTo(expected);
		verifyNoMoreInteractions(filler);
		verify(fileSystem.provider()).newFileChannel(any(), eq(set(StandardOpenOption.READ)));
		verifyNoMoreInteractions(fileSystem.provider());
	}
}
