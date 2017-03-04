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
package se.tfiskgul.mux2fs.fs.mux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.file.Path;

import org.junit.Before;
import org.junit.Test;

import se.tfiskgul.mux2fs.fs.base.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.base.StatFiller;
import se.tfiskgul.mux2fs.fs.mirror.MirrorFsTest;

public class MuxFsTest extends MirrorFsTest {

	@Before
	@Override
	public void before() {
		fileSystem = mockFileSystem();
		mirrorRoot = mockPath("/mirror/root/", fileSystem);
		fs = new MuxFs(mirrorRoot);
	}

	@Test
	public void testReadDirHidesMatchingSubtitles()
			throws Exception {
		// Given
		Path mkv = mockPath("file.mkv");
		Path srt = mockPath("file.srt");
		mockDirectoryStream(mirrorRoot, mkv, srt);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).addWithExtraSize("file.mkv", mkv, 0);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testReadDirDoesntHideNonMatchingSubtitles()
			throws Exception {
		// Given
		Path mkv = mockPath("file.mkv");
		Path srt = mockPath("unrelated.srt");
		mockDirectoryStream(mirrorRoot, mkv, srt);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).addWithExtraSize("file.mkv", mkv, 0);
		verify(filler).add("unrelated.srt", srt);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testReadDirSizeOfMatchingSrtIsAddedToMkv()
			throws Exception {
		// Given
		Path mkv = mockPath("file.mkv");
		Path srt = mockPath("file.srt", 2893756L);
		mockDirectoryStream(mirrorRoot, mkv, srt);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).addWithExtraSize("file.mkv", mkv, 2893756L);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testReadDirManySrtFilesForSameMkv()
			throws Exception {
		// Given
		Path mkv = mockPath("file.mkv");
		Path srt1 = mockPath("file.eng.srt", 2893756L);
		Path srt2 = mockPath("file.der.srt", 2345L);
		Path srt3 = mockPath("file.swe.srt", 78568L);
		mockDirectoryStream(mirrorRoot, srt1, mkv, srt3, srt2);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).addWithExtraSize("file.mkv", mkv, 2893756L + 2345L + 78568L);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testReadDirManyFiles()
			throws Exception {
		// Given
		Path mkv1 = mockPath("file1.mkv");
		Path mkv1txt1 = mockPath("file1.txt", 123456789L);
		Path mkv1srt1 = mockPath("file1.eng.srt", 2893756L);
		Path mkv1srt2 = mockPath("file1.der.srt", 2345L);
		Path mkv1srt3 = mockPath("file1.swe.srt", 78568L);
		Path mkv2 = mockPath("file2.mkv");
		Path mkv3srt1 = mockPath("file3.srt", 324685L);
		Path mkv4 = mockPath("file4.mkv");
		Path mkv4txt1 = mockPath("file4.txt", 4725L);
		Path mkv4srt1 = mockPath("file4.esp.srt", 235468L);
		Path mkv4srt2 = mockPath("file4.klingon.srt", 62369L);
		Path mkv4srt3 = mockPath("file4.elvish.srt", 468L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1txt1, mkv1srt1, mkv1, mkv1srt3, mkv1srt2, mkv2, mkv3srt1, mkv4, mkv4txt1, mkv4srt1, mkv4srt2, mkv4srt3);
		DirectoryFiller filler = mock(DirectoryFiller.class);
		// When
		int result = fs.readdir("/", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(fileSystem.provider()).newDirectoryStream(eq(mirrorRoot), any());
		verify(filler).add(".", mirrorRoot);
		verify(filler).add("..", mirrorRoot);
		verify(filler).addWithExtraSize("file1.mkv", mkv1, 2893756L + 2345L + 78568L);
		verify(filler).add("file1.txt", mkv1txt1);
		verify(filler).addWithExtraSize("file2.mkv", mkv2, 0L);
		verify(filler).add("file3.srt", mkv3srt1);
		verify(filler).addWithExtraSize("file4.mkv", mkv4, 235468L + 62369L + 468L);
		verify(filler).add("file4.txt", mkv4txt1);
		verifyNoMoreInteractions(filler);
	}

	@Test
	public void testGetAttrForMkvWithMatchingSrtHasExtraSize()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		Path mkv = mockPath("file.mkv");
		Path srt1 = mockPath("file.eng.srt", 2893756L);
		Path srt2 = mockPath("file.der.srt", 2345L);
		Path srt3 = mockPath("file.swe.srt", 78568L);
		mockDirectoryStream(mirrorRoot, srt1, mkv, srt3, srt2);
		// When
		int result = fs.getattr("file.mkv", stat);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(stat).statWithExtraSize(mkv, 2893756L + 2345L + 78568L);
		verifyNoMoreInteractions(stat);
	}

	@Test
	public void testGetAttrManyFiles()
			throws Exception {
		// Given
		StatFiller stat = mock(StatFiller.class);
		Path mkv1 = mockPath("file1.mkv");
		Path mkv1txt1 = mockPath("file1.txt", 123456789L);
		Path mkv1srt1 = mockPath("file1.eng.srt", 2893756L);
		Path mkv1srt2 = mockPath("file1.der.srt", 2345L);
		Path mkv1srt3 = mockPath("file1.swe.srt", 78568L);
		Path mkv2 = mockPath("file2.mkv");
		Path mkv3srt1 = mockPath("file3.srt", 324685L);
		Path mkv4 = mockPath("file4.mkv");
		Path mkv4txt1 = mockPath("file4.txt", 4725L);
		Path mkv4srt1 = mockPath("file4.esp.srt", 235468L);
		Path mkv4srt2 = mockPath("file4.klingon.srt", 62369L);
		Path mkv4srt3 = mockPath("file4.elvish.srt", 468L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1txt1, mkv1srt1, mkv1, mkv1srt3, mkv1srt2, mkv2, mkv3srt1, mkv4, mkv4txt1, mkv4srt1, mkv4srt2, mkv4srt3);
		// When
		int result = fs.getattr("file1.mkv", stat);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(stat).statWithExtraSize(mkv1, 2893756L + 2345L + 78568L);
		verifyNoMoreInteractions(stat);
	}
}
