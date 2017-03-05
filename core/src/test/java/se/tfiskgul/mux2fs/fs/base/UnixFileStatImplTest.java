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
package se.tfiskgul.mux2fs.fs.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.Test;

import se.tfiskgul.mux2fs.Fixture;

public class UnixFileStatImplTest extends Fixture {

	@Test
	public void testStat()
			throws Exception {
		// Given
		int nonce = 123;
		FileSystem fileSystem = mockFileSystem();
		Path path = mockPath("/", fileSystem);
		Instant base = Instant.now();
		Map<String, Object> attributes = mockAttributes(nonce, base);
		when(fileSystem.provider().readAttributes(eq(path), eq("unix:*"), any())).thenReturn(attributes);
		UnixFileStatImpl stat = new UnixFileStatImpl();
		// When
		UnixFileStat result = stat.stat(path);
		// Then
		assertThat(result).isSameAs(stat);
		assertThat(stat.getDev()).isEqualTo(nonce * 3L);
		assertThat(stat.getIno()).isEqualTo(nonce * 5L);
		assertThat(stat.getLinks()).isEqualTo(nonce * 7);
		assertThat(stat.getMode()).isEqualTo(nonce * 11);
		assertThat(stat.getUid()).isEqualTo(nonce * 13);
		assertThat(stat.getGid()).isEqualTo(nonce * 17);
		assertThat(stat.getRdev()).isEqualTo(nonce * 19L);
		assertThat(stat.getSize()).isEqualTo(nonce * 23L);
		assertThat(stat.getBlocks()).isEqualTo(stat.getSize() / 512 + 1);
		assertThat(stat.getAccessTime()).isEqualTo(FileTime.from(base.minus(29, ChronoUnit.DAYS)).toInstant());
		assertThat(stat.getModificationTime()).isEqualTo(FileTime.from(base.minus(31, ChronoUnit.DAYS)).toInstant());
		assertThat(stat.getInodeTime()).isEqualTo(FileTime.from(base.minus(37, ChronoUnit.DAYS)).toInstant());
	}
}
