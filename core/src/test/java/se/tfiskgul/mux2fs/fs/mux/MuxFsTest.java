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
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.fs.base.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.base.FileHandleFiller;
import se.tfiskgul.mux2fs.fs.base.StatFiller;
import se.tfiskgul.mux2fs.fs.mirror.MirrorFsTest;
import se.tfiskgul.mux2fs.mux.Muxer;
import se.tfiskgul.mux2fs.mux.Muxer.MuxerFactory;
import se.tfiskgul.mux2fs.mux.Muxer.State;

public class MuxFsTest extends MirrorFsTest {

	private Path tempDir;
	private MuxerFactory muxerFactory;

	@Before
	@Override
	public void before() {
		fileSystem = mockFileSystem();
		mirrorRoot = mockPath("/mirror/root/", fileSystem);
		tempDir = mockPath("tmp");
		muxerFactory = mock(MuxerFactory.class);
		fs = new MuxFs(mirrorRoot, tempDir, muxerFactory);
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

	@Test
	public void testOpenMkvNoMatchingSubsShouldOpenNormally()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path mkv1 = mockPath("file1.mkv");
		Path mkv2 = mockPath("file2.mkv");
		Path mkv2txt1 = mockPath("file2.txt", 123456789L);
		Path mkv2srt1 = mockPath("file2.eng.srt", 2893756L);
		Path mkv2srt2 = mockPath("file2.der.srt", 2345L);
		Path mkv2srt3 = mockPath("file2.swe.srt", 78568L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1, mkv2, mkv2txt1, mkv2srt1, mkv2srt2, mkv2srt3);
		when(fileSystem.provider().newFileChannel(eq(mkv1), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
		// When
		int result = fs.open("file1.mkv", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
	}

	@Test
	public void testAllErrorsForGetInfoInOpenMkvMatchingSub()
			throws Exception {
		testAllErrors(this::testOpenMkvGetFileInfo);
	}

	private void testOpenMkvGetFileInfo(ExpectedResult expected)
			throws IOException {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path mkv = mockPath("file1.mkv");
		Path srt = mockPath("file1.srt", 78568L);
		mockShuffledDirectoryStream(mirrorRoot, mkv, srt);
		when(fileSystem.provider().readAttributes(eq(mkv), eq("unix:*"))).thenThrow(expected.exception());
		// When
		int result = fs.open("file1.mkv", filler);
		// Then
		assertThat(result).isEqualTo(expected.value());
	}

	@Test
	public void testIOExceptionInMuxerStartFallsBackToOriginal()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path mkv1 = mockPath("file1.mkv");
		Path mkv2 = mockPath("file2.mkv");
		Path mkv2txt1 = mockPath("file2.txt", 123456789L);
		Path mkv2srt1 = mockPath("file2.eng.srt", 2893756L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1, mkv2, mkv2txt1, mkv2srt1);
		Map<String, Object> attributes = mockAttributes(1, Instant.now());
		when(fileSystem.provider().readAttributes(eq(mkv2), eq("unix:*"))).thenReturn(attributes);
		Muxer muxer = mock(Muxer.class);
		when(muxerFactory.from(mkv2, mkv2srt1, tempDir)).thenReturn(muxer);
		doThrow(new IOException()).when(muxer).start();
		when(fileSystem.provider().newFileChannel(eq(mkv2), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
		// When
		int result = fs.open("file2.mkv", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(muxerFactory).from(mkv2, mkv2srt1, tempDir);
		verifyNoMoreInteractions(muxerFactory);
		verify(muxer).start();
		verifyNoMoreInteractions(muxer);
		verify(filler).setFileHandle(gt(1));
		verify(fileSystem.provider()).newFileChannel(eq(mkv2), eq(set(StandardOpenOption.READ)));
	}

	@Test
	public void testOpenMkvMatchingSubNoMuxerOutputFallsBackToOriginal()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path mkv1 = mockPath("file1.mkv");
		Path mkv2 = mockPath("file2.mkv");
		Path mkv2txt1 = mockPath("file2.txt", 123456789L);
		Path mkv2srt1 = mockPath("file2.eng.srt", 2893756L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1, mkv2, mkv2txt1, mkv2srt1);
		Map<String, Object> attributes = mockAttributes(1, Instant.now());
		when(fileSystem.provider().readAttributes(eq(mkv2), eq("unix:*"))).thenReturn(attributes);
		Muxer muxer = mock(Muxer.class);
		when(muxerFactory.from(mkv2, mkv2srt1, tempDir)).thenReturn(muxer);
		when(fileSystem.provider().newFileChannel(eq(mkv2), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
		when(muxer.getOutput()).thenReturn(Optional.empty());
		// When
		int result = fs.open("file2.mkv", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(muxerFactory).from(mkv2, mkv2srt1, tempDir);
		verifyNoMoreInteractions(muxerFactory);
		verify(muxer).start();
		verify(muxer).waitFor(anyLong(), any());
		verify(muxer).getOutput();
		verifyNoMoreInteractions(muxer);
		verify(filler).setFileHandle(gt(1));
		verify(fileSystem.provider()).newFileChannel(eq(mkv2), eq(set(StandardOpenOption.READ)));
	}

	@Test
	public void testOpenMkvMatchingSubShouldBeMuxed()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path mkv1 = mockPath("file1.mkv");
		Path mkv2 = mockPath("file2.mkv");
		Path mkv2txt1 = mockPath("file2.txt", 123456789L);
		Path mkv2srt1 = mockPath("file2.eng.srt", 2893756L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1, mkv2, mkv2txt1, mkv2srt1);
		Map<String, Object> attributes = mockAttributes(1, Instant.now());
		when(fileSystem.provider().readAttributes(eq(mkv2), eq("unix:*"))).thenReturn(attributes);
		Muxer muxer = mock(Muxer.class);
		when(muxerFactory.from(mkv2, mkv2srt1, tempDir)).thenReturn(muxer);
		Path muxedFile = mockPath(tempDir, "file1.mkv");
		when(muxer.getOutput()).thenReturn(Optional.of(muxedFile));
		when(fileSystem.provider().newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
		// When
		int result = fs.open("file2.mkv", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(muxerFactory).from(mkv2, mkv2srt1, tempDir);
		verifyNoMoreInteractions(muxerFactory);
		verify(muxer).start();
		verify(muxer).waitFor(anyLong(), any());
		verify(muxer).getOutput();
		verifyNoMoreInteractions(muxer);
		verify(filler).setFileHandle(gt(1));
		verify(fileSystem.provider()).newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)));
	}

	@Test
	public void testOpenMkvMatchingSubTwiceShouldBeMuxedOnce()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path mkv1 = mockPath("file1.mkv");
		Path mkv2 = mockPath("file2.mkv");
		Path mkv2txt1 = mockPath("file2.txt", 123456789L);
		Path mkv2srt1 = mockPath("file2.eng.srt", 2893756L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1, mkv2, mkv2txt1, mkv2srt1);
		Map<String, Object> attributes = mockAttributes(1, Instant.now());
		when(fileSystem.provider().readAttributes(eq(mkv2), eq("unix:*"))).thenReturn(attributes);
		Muxer muxer = mock(Muxer.class);
		when(muxerFactory.from(mkv2, mkv2srt1, tempDir)).thenReturn(muxer);
		Path muxedFile = mockPath(tempDir, "file1.mkv");
		when(muxer.getOutput()).thenReturn(Optional.of(muxedFile));
		when(fileSystem.provider().newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
		fs.open("file2.mkv", filler);
		Muxer muxer2 = mock(Muxer.class);
		when(muxerFactory.from(mkv2, mkv2srt1, tempDir)).thenReturn(muxer2);
		mockShuffledDirectoryStream(mirrorRoot, mkv1, mkv2, mkv2txt1, mkv2srt1);
		// When
		int result = fs.open("file2.mkv", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(muxerFactory, times(2)).from(mkv2, mkv2srt1, tempDir);
		verifyNoMoreInteractions(muxerFactory);
		verify(muxer, times(2)).start();
		verify(muxer, times(2)).waitFor(anyLong(), any());
		verify(muxer, times(2)).getOutput();
		verifyNoMoreInteractions(muxer);
		verifyNoMoreInteractions(muxer2);
		verify(filler, times(2)).setFileHandle(gt(1));
		verify(fileSystem.provider(), times(2)).newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)));
	}

	@Test
	@SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
	public void testOpenMkvMatchingSubOpenMuxedFileFailsFallsBackToOriginal()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		Path mkv1 = mockPath("file1.mkv");
		Path mkv2 = mockPath("file2.mkv");
		Path mkv2txt1 = mockPath("file2.txt", 123456789L);
		Path mkv2srt1 = mockPath("file2.eng.srt", 2893756L);
		mockShuffledDirectoryStream(mirrorRoot, mkv1, mkv2, mkv2txt1, mkv2srt1);
		Map<String, Object> attributes = mockAttributes(1, Instant.now());
		when(fileSystem.provider().readAttributes(eq(mkv2), eq("unix:*"))).thenReturn(attributes);
		Muxer muxer = mock(Muxer.class);
		when(muxerFactory.from(mkv2, mkv2srt1, tempDir)).thenReturn(muxer);
		Path muxedFile = mockPath(tempDir, "file1.mkv");
		when(muxer.getOutput()).thenReturn(Optional.of(muxedFile));
		when(fileSystem.provider().newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)))).thenThrow(new IOException());
		when(fileSystem.provider().newFileChannel(eq(mkv2), eq(set(StandardOpenOption.READ)))).thenReturn(mock(FileChannel.class));
		// When
		int result = fs.open("file2.mkv", filler);
		// Then
		assertThat(result).isEqualTo(SUCCESS);
		verify(muxerFactory).from(mkv2, mkv2srt1, tempDir);
		verifyNoMoreInteractions(muxerFactory);
		verify(muxer).start();
		verify(muxer).waitFor(anyLong(), any());
		verify(muxer).getOutput();
		verifyNoMoreInteractions(muxer);
		verify(muxedFile.toFile()).delete();
		verify(filler).setFileHandle(gt(1));
		verify(fileSystem.provider()).newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)));
		verify(fileSystem.provider()).newFileChannel(eq(mkv2), eq(set(StandardOpenOption.READ)));
	}

	@Test
	public void testReadFromSuccessfulMuxedFile()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path mkv = mockPath("file1.mkv");
		Path srt = mockPath("file1.eng.srt", 2893756L);
		mockShuffledDirectoryStream(mirrorRoot, mkv, srt);
		Map<String, Object> attributes = mockAttributes(1, Instant.now());
		when(fileSystem.provider().readAttributes(eq(mkv), eq("unix:*"))).thenReturn(attributes);
		Muxer muxer = mock(Muxer.class);
		when(muxerFactory.from(mkv, srt, tempDir)).thenReturn(muxer);
		Path muxedFile = mockPath(tempDir, "file1-muxed.mkv");
		when(muxer.getOutput()).thenReturn(Optional.of(muxedFile));
		FileChannel fileChannel = mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		fs.open("file1.mkv", filler);
		Integer fileHandle = handleCaptor.getValue();
		ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
		when(fileChannel.read(bufferCaptor.capture(), eq(64L))).thenReturn(128);
		when(muxer.state()).thenReturn(State.SUCCESSFUL);
		// When
		int bytesRead = fs.read("file1.mkv", (data) -> assertThat(data).hasSize(128), 128, 64, fileHandle);
		// Then
		assertThat(bytesRead).isEqualTo(128);
		verify(muxerFactory).from(mkv, srt, tempDir);
		verifyNoMoreInteractions(muxerFactory);
		verify(muxer).start();
		verify(muxer).waitFor(anyLong(), any());
		verify(muxer).getOutput();
		verify(muxer).state();
		verifyNoMoreInteractions(muxer);
		verify(filler).setFileHandle(gt(1));
		verify(fileSystem.provider()).newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)));
	}

	@Test
	public void testReadFromFailedMuxedFile()
			throws Exception {
		// Given
		FileHandleFiller filler = mock(FileHandleFiller.class);
		ArgumentCaptor<Integer> handleCaptor = ArgumentCaptor.forClass(Integer.class);
		doNothing().when(filler).setFileHandle(handleCaptor.capture());
		Path mkv = mockPath("file1.mkv");
		Path srt = mockPath("file1.eng.srt", 2893756L);
		mockShuffledDirectoryStream(mirrorRoot, mkv, srt);
		Map<String, Object> attributes = mockAttributes(1, Instant.now());
		when(fileSystem.provider().readAttributes(eq(mkv), eq("unix:*"))).thenReturn(attributes);
		Muxer muxer = mock(Muxer.class);
		when(muxerFactory.from(mkv, srt, tempDir)).thenReturn(muxer);
		Path muxedFile = mockPath(tempDir, "file1-muxed.mkv");
		when(muxer.getOutput()).thenReturn(Optional.of(muxedFile));
		FileChannel fileChannel = mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(muxedFile), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		fs.open("file1.mkv", filler);
		Integer fileHandle = handleCaptor.getValue();
		when(muxer.state()).thenReturn(State.FAILED);
		// When
		int result = fs.read("file1.mkv", (data) -> fail(), 128, 64, fileHandle);
		// Then
		assertThat(result).isEqualTo(-ErrorCodes.EIO());
		verify(muxerFactory).from(mkv, srt, tempDir);
		verifyNoMoreInteractions(muxerFactory);
		verify(muxer).start();
		verify(muxer).waitFor(anyLong(), any());
		verify(muxer).getOutput();
		verify(muxer).state();
		verifyNoMoreInteractions(muxer);
		verify(filler).setFileHandle(gt(1));
		verifyNoMoreInteractions(fileChannel);
	}
}
