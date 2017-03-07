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
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import cyclops.control.Try;
import ru.serce.jnrfuse.ErrorCodes;

public abstract class Fixture {

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
		when(subPath.getParent()).thenReturn(parent);
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
		return mockDirectoryStream(root, ImmutableList.copyOf(entries));
	}

	protected DirectoryStream<Path> mockShuffledDirectoryStream(Path root, Path... entries)
			throws IOException {
		ArrayList<Path> shuffled = new ArrayList<Path>(Arrays.asList(entries));
		Collections.shuffle(shuffled);
		return mockDirectoryStream(root, ImmutableList.copyOf(shuffled));
	}

	@SuppressWarnings("unchecked")
	protected DirectoryStream<Path> mockDirectoryStream(Path root, List<Path> entries)
			throws IOException {
		DirectoryStream<Path> directoryStream = mock(DirectoryStream.class);
		when(directoryStream.iterator()).thenAnswer((inv) -> entries.iterator());
		when(directoryStream.spliterator()).thenAnswer((inv) -> entries.spliterator());
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

	protected void testAllErrors(Try.CheckedConsumer<ExpectedResult, Exception> sut)
			throws Exception {
		List<ExpectedResult> list = list( //
				exp(new NoSuchFileException(null), -ErrorCodes.ENOENT()), //
				exp(new AccessDeniedException(null), -ErrorCodes.EPERM()), //
				exp(new NotDirectoryException(null), -ErrorCodes.ENOTDIR()), //
				exp(new NotLinkException(null), -ErrorCodes.EINVAL()), //
				exp(new UnsupportedOperationException(), -ErrorCodes.ENOSYS()), //
				exp(new IOException(), -ErrorCodes.EIO())); //
		list.forEach(expected -> Try.runWithCatch(() -> sut.accept(expected), Exception.class).get());
	}

	private static ExpectedResult exp(Exception exception, int value) {
		return ExpectedResult.create(exception, value);
	}

	@AutoValue
	public abstract static class ExpectedResult {
		static ExpectedResult create(Exception exception, int value) {
			return new AutoValue_Fixture_ExpectedResult(exception, value);
		}

		public abstract Exception exception();

		public abstract int value();
	}

	protected Map<String, Object> mockAttributes(int nonce, Instant base) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("dev", nonce * 3L);
		attributes.put("ino", nonce * 5L);
		attributes.put("nlink", nonce * 7);
		attributes.put("mode", nonce * 11);
		attributes.put("uid", nonce * 13);
		attributes.put("gid", nonce * 17);
		attributes.put("rdev", nonce * 19L);
		attributes.put("size", nonce * 23L);
		attributes.put("lastAccessTime", FileTime.from(base.minus(29, ChronoUnit.DAYS)));
		attributes.put("lastModifiedTime", FileTime.from(base.minus(31, ChronoUnit.DAYS)));
		attributes.put("ctime", FileTime.from(base.minus(37, ChronoUnit.DAYS)));
		return attributes;
	}

	protected void mockAttributes(Path mkv, int nonce)
			throws IOException {
		Map<String, Object> attributes = mockAttributes(nonce, Instant.now());
		when(mkv.getFileSystem().provider().readAttributes(eq(mkv), eq("unix:*"))).thenReturn(attributes);
	}
}
