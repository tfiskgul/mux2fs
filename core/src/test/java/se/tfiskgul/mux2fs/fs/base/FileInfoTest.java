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

import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.Test;

public class FileInfoTest {

	@Test
	public void testConstructor() {
		// Given
		long ino = 1234L;
		long size = 9876L;
		Instant modificationTime = Instant.now();
		Instant inodeTime = Instant.now().plusSeconds(5);
		// When
		FileInfo fileInfo = new FileInfo(ino, modificationTime, inodeTime, size);
		// Then
		assertThat(fileInfo.getInode()).isEqualTo(ino);
		assertThat(fileInfo.getSize()).isEqualTo(size);
		assertThat(fileInfo.getMtime()).isEqualTo(FileTime.from(modificationTime));
		assertThat(fileInfo.getCtime()).isEqualTo(FileTime.from(inodeTime));
	}

	@Test
	public void testEquals() {
		// Given
		long ino = 1234L;
		long size = 9876L;
		Instant modificationTime = Instant.now();
		Instant inodeTime = Instant.now().plusSeconds(5);
		// When
		FileInfo fileInfo = new FileInfo(ino, modificationTime, inodeTime, size);
		// Then
		assertThat(fileInfo).isEqualTo(fileInfo);
		assertThat(fileInfo).isNotEqualTo(null);
		assertThat(fileInfo).isNotEqualTo(inodeTime);
	}
}
