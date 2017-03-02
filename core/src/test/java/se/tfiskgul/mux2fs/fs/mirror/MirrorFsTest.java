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
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.fs.decoupling.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.decoupling.FileHandleFiller;
import se.tfiskgul.mux2fs.fs.decoupling.StatFiller;

public class MirrorFsTest extends MirrorFsFixture {

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
		when(stat.stat(foo)).thenThrow(new NoSuchFileException(null));
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
		when(stat.stat(foo)).thenThrow(new AccessDeniedException(null));
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
		when(stat.stat(foo)).thenThrow(new IOException());
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
		negativeReadDir(-ErrorCodes.ENOTDIR(), new NotDirectoryException(null));
	}

	@Test
	public void testReadDirNoPerm()
			throws Exception {
		negativeReadDir(-ErrorCodes.EPERM(), new AccessDeniedException(null));
	}

	@Test
	public void testReadDirNoDirOrFile()
			throws Exception {
		negativeReadDir(-ErrorCodes.ENOENT(), new NoSuchFileException(null));
	}

	@Test
	public void testReadDirIoErr()
			throws Exception {
		negativeReadDir(-ErrorCodes.EIO(), new IOException());
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
		when(filler.add(eq("."), any())).thenThrow(new IOException());
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
		when(filler.add(eq(".."), any())).thenThrow(new IOException());
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
		when(filler.add("foo", foo)).thenThrow(new IOException());
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

	private void negativeReadDir(int expected, IOException exception)
			throws IOException {
		// Given
		when(fileSystem.provider().newDirectoryStream(eq(mirrorRoot), any())).thenThrow(exception);
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
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
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
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
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
		testOpenThrow(-ErrorCodes.EPERM(), new AccessDeniedException(null));
	}

	@Test
	public void testOpenNoSuchFile()
			throws Exception {
		testOpenThrow(-ErrorCodes.ENOENT(), new NoSuchFileException(null));
	}

	@Test
	public void testOpenIoError()
			throws Exception {
		testOpenThrow(-ErrorCodes.EIO(), new IOException());
	}

	private void testOpenThrow(int expected, IOException exception)
			throws IOException {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		when(fileSystem.provider().newFileChannel(any(), eq(set(StandardOpenOption.READ)))).thenThrow(exception);
		// When
		int result = fs.open("/", filler);
		// Then
		assertThat(result).isEqualTo(expected);
		verifyNoMoreInteractions(filler);
		verify(fileSystem.provider()).newFileChannel(any(), eq(set(StandardOpenOption.READ)));
		verifyNoMoreInteractions(fileSystem.provider());
	}

	@Test
	public void testReleaseNegativeFileHandle() {
		// Given
		// When
		int result = fs.release("foo.bar", -23);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EBADF());
	}

	@Test
	public void testReleaseNonOpenedFileHandle() {
		// Given
		// When
		int result = fs.release("foo.bar", 23);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EBADF());
	}

	@Test
	public void testReadBadFileDescriptor() {
		// Given
		// When
		int result = fs.read("foo.bar", empty(), 10, 1234, 567);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EBADF());
	}

	@Test
	public void testRead()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		FileChannel fileChannel = mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		fs.open("foo.bar", filler);
		Integer fileHandle = handleCaptor.getValue();
		ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
		when(fileChannel.read(bufferCaptor.capture(), eq(1234L))).thenReturn(10);
		// When
		int result = fs.read("foo.bar", (data) -> assertThat(data).hasSize(10), 10, 1234L, fileHandle);
		// Then
		assertThat(result).isEqualTo(10);
		verify(fileChannel).read(any(), eq(1234L));
		verifyNoMoreInteractions(fileChannel);
		assertThat(bufferCaptor.getValue().limit()).isEqualTo(10);
	}

	@Test
	public void testReadEndOfFile()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		FileChannel fileChannel = mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		fs.open("foo.bar", filler);
		Integer fileHandle = handleCaptor.getValue();
		ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
		when(fileChannel.read(bufferCaptor.capture(), eq(1234L))).thenReturn(0);
		// When
		int result = fs.read("foo.bar", (data) -> fail("No data should be read"), 10, 1234L, fileHandle);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileChannel).read(any(), eq(1234L));
		verifyNoMoreInteractions(fileChannel);
		assertThat(bufferCaptor.getValue().limit()).isEqualTo(10);
	}

	@Test
	public void testReadInputOutputException()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path fooBar = mockPath(mirrorRoot, "foo.bar");
		FileChannel fileChannel = mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(fooBar), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		fs.open("foo.bar", filler);
		Integer fileHandle = handleCaptor.getValue();
		ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
		when(fileChannel.read(bufferCaptor.capture(), eq(1234L))).thenThrow(new IOException());
		// When
		int result = fs.read("foo.bar", (data) -> fail("No data should be read"), 10, 1234L, fileHandle);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EIO());
		verify(fileChannel).read(any(), eq(1234L));
		verifyNoMoreInteractions(fileChannel);
		assertThat(bufferCaptor.getValue().limit()).isEqualTo(10);
	}
}
