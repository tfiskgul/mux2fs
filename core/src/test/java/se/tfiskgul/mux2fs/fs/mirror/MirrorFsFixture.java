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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.tfiskgul.mux2fs.Constants.SUCCESS;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Before;
import org.powermock.api.mockito.PowerMockito;

import se.tfiskgul.mux2fs.Fixture;
import se.tfiskgul.mux2fs.fs.base.FileChannelCloser;
import se.tfiskgul.mux2fs.fs.base.FileHandleFiller;

public class MirrorFsFixture extends Fixture {

	protected FileSystem fileSystem;
	protected Path mirrorRoot;
	protected se.tfiskgul.mux2fs.fs.base.FileSystem fs;
	protected FileChannelCloser fileChannelCloser;

	@Before
	public void before() {
		fileSystem = mockFileSystem();
		mirrorRoot = mockPath("/mirror/root/", fileSystem);
		fileChannelCloser = mock(FileChannelCloser.class);
		fs = new MirrorFs(mirrorRoot, fileChannelCloser);
	}

	protected Path mockPath(String name) {
		return mockPath(mirrorRoot, name);
	}

	protected Path mockPath(String name, long size) {
		return mockPath(mirrorRoot, name, size);
	}

	protected FileChannel mockAndOpen(String name)
			throws IOException {
		Path path = mockPath(mirrorRoot, name);
		FileChannel fileChannel = PowerMockito.mock(FileChannel.class);
		when(fileSystem.provider().newFileChannel(eq(path), eq(set(StandardOpenOption.READ)))).thenReturn(fileChannel);
		int result = fs.open(name, mock(FileHandleFiller.class));
		assertThat(result).isEqualTo(SUCCESS);
		return fileChannel;
	}
}
