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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import se.tfiskgul.mux2fs.fs.base.FileInfo;

public class MuxedFileTest {

	@Test
	public void testEquals() {
		FileInfo info = mock(FileInfo.class);
		Muxer muxer = mock(Muxer.class);
		MuxedFile muxedFile = new MuxedFile(info, muxer);
		MuxedFile muxedFile2 = new MuxedFile(info, muxer);
		assertThat(muxedFile).isEqualTo(muxedFile);
		assertThat(muxedFile).isEqualTo(muxedFile2);
		assertThat(muxedFile).isNotEqualTo(null);
		assertThat(muxedFile).isNotEqualTo(info);
	}

	@Test
	public void testHashCode() {
		FileInfo info = mock(FileInfo.class);
		Muxer muxer = mock(Muxer.class);
		MuxedFile muxedFile = new MuxedFile(info, muxer);
		MuxedFile muxedFile2 = new MuxedFile(info, muxer);
		assertThat(muxedFile.hashCode()).isEqualTo(muxedFile2.hashCode());
	}
}
