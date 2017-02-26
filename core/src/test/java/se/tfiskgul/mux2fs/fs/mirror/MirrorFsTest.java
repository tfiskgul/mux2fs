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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Test;

import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.Fixture;
import se.tfiskgul.mux2fs.fs.decoupling.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.decoupling.StatFiller;

public class MirrorFsTest extends Fixture {

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
		assertThat(result).isEqualTo(0);
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
		assertThat(result).isEqualTo(0);
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
	public void testGetAttrFileNotFound()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		Path foo = mockPath(mirrorRoot, "/foo");
		when(stat.stat(foo)).thenThrow(FileNotFoundException.class);
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
		assertThat(result).isEqualTo(0);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).add("foo", foo);
		verify(filler).add("bar", bar);
		verifyNoMoreInteractions(filler);
	}
}
