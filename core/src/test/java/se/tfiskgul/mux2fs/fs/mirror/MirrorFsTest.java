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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.junit.Test;

import se.tfiskgul.mux2fs.Fixture;
import se.tfiskgul.mux2fs.fs.decoupling.StatFiller;

public class MirrorFsTest extends Fixture {

	@Test
	public void testGetAttr()
			throws Exception {
		// Given
		FileSystem fileSystem = mockFileSystem();
		Path mirrorRoot = mockPath(fileSystem);
		when(mirrorRoot.toString()).thenReturn("/mirror/root/");
		MirrorFs fs = new MirrorFs(mirrorRoot);
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
		FileSystem fileSystem = mockFileSystem();
		Path mirrorRoot = mockPath(fileSystem);
		when(mirrorRoot.toString()).thenReturn("/mirror/root/");
		MirrorFs fs = new MirrorFs(mirrorRoot);
		Path foo = mockPath(fileSystem);
		StatFiller stat = mock(StatFiller.class);
		when(fileSystem.getPath(mirrorRoot.toString(), "/foo")).thenReturn(foo);
		// When
		int result = fs.getattr("/foo", stat);
		// Then
		assertThat(result).isEqualTo(0);
		verify(stat).stat(foo);
	}
}
