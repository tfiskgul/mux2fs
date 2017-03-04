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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public abstract class Fixture {

	public static final int SUCCESS = 0;

	protected Path mockPath(String name, FileSystem fileSystem) {
		Path path = mock(Path.class);
		when(path.getFileSystem()).thenReturn(fileSystem);
		when(path.toString()).thenReturn(name);
		when(fileSystem.getPath(path.toString(), "/")).thenReturn(path);
		when(fileSystem.getPath(path.toString(), "/", "..")).thenReturn(path); // Resolve ".." to the same as "."
		return path;
	}

	protected FileSystem mockFileSystem() {
		FileSystem fileSystem = mock(FileSystem.class);
		FileSystemProvider fsProvider = mock(FileSystemProvider.class);
		when(fileSystem.provider()).thenReturn(fsProvider);
		return fileSystem;
	}

	protected Path mockPath(Path parent, String name) {
		return mockPath(parent, name, 0L);
	}

	protected Path mockPath(Path parent, String name, long size) {
		FileSystem fileSystem = parent.getFileSystem();
		Path subPath = mock(Path.class);
		File subPathToFile = mock(File.class);
		when(subPath.toFile()).thenReturn(subPathToFile);
		when(subPathToFile.length()).thenReturn(size);
		when(subPath.getFileSystem()).thenReturn(fileSystem);
		when(subPath.resolve(anyString())).thenAnswer(invoke -> {
			String childName = (String) invoke.getArguments()[0];
			return mockPath(subPath, childName);
		});
		when(fileSystem.getPath(parent.toString(), name)).thenReturn(subPath);
		String fullPath = (parent.toString() + "/" + name).replace("//", "/");
		when(subPath.toString()).thenReturn(fullPath);
		Path subPathFileName = mock(Path.class);
		when(subPathFileName.toString()).thenReturn(name);
		when(subPath.getFileName()).thenReturn(subPathFileName);
		return subPath;
	}

	protected DirectoryStream<Path> mockDirectoryStream(Path root, Path... entries)
			throws IOException {
		return mockDirectoryStream(root, list(entries));
	}

	@SuppressWarnings("unchecked")
	protected DirectoryStream<Path> mockDirectoryStream(Path root, List<Path> entries)
			throws IOException {
		DirectoryStream<Path> directoryStream = mock(DirectoryStream.class);
		when(directoryStream.iterator()).thenReturn(entries.iterator());
		when(root.getFileSystem().provider().newDirectoryStream(eq(root), any())).thenReturn(directoryStream);
		return directoryStream;
	}

	@SuppressWarnings("unchecked")
	protected <T> ImmutableList<T> list(T... elements) {
		return ImmutableList.<T> builder().add(elements).build();
	}

	@SuppressWarnings("unchecked")
	protected <T> Stream<T> stream(T... elements) {
		return list(elements).stream();
	}

	@SuppressWarnings("unchecked")
	protected <T> Set<T> set(T... elements) {
		return ImmutableSet.<T> builder().add(elements).build();
	}

	protected <T> Consumer<T> empty() {
		return (t) -> {
		};
	}
}
