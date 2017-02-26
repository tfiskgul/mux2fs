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
package se.tfiskgul.mux2fs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;

import com.google.common.collect.ImmutableList;

public abstract class Fixture {

	protected Path mockPath(String name, FileSystem fileSystem) {
		Path path = mock(Path.class);
		when(path.getFileSystem()).thenReturn(fileSystem);
		when(path.toString()).thenReturn(name);
		return path;
	}

	protected FileSystem mockFileSystem() {
		FileSystem fileSystem = mock(FileSystem.class);
		FileSystemProvider fsProvider = mock(FileSystemProvider.class);
		when(fileSystem.provider()).thenReturn(fsProvider);
		return fileSystem;
	}

	protected Path mockPath(Path parent, String name) {
		FileSystem fileSystem = parent.getFileSystem();
		Path subPath = mock(Path.class);
		when(subPath.getFileSystem()).thenReturn(fileSystem);
		when(fileSystem.getPath(parent.toString(), name)).thenReturn(subPath);
		String appended = parent.toString() + "/" + name;
		when(subPath.toString()).thenReturn(appended);
		return subPath;
	}

	@SuppressWarnings("unchecked")
	protected <T> ImmutableList<T> list(T... elements) {
		return ImmutableList.<T> builder().add(elements).build();
	}
}
