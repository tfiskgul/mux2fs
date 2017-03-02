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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cyclops.control.Try;
import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.fs.decoupling.DecoupledFileSystem;
import se.tfiskgul.mux2fs.fs.decoupling.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.decoupling.FileHandleFiller;
import se.tfiskgul.mux2fs.fs.decoupling.StatFiller;

public class MirrorFs extends DecoupledFileSystem {

	private static final Logger logger = LoggerFactory.getLogger(MirrorFs.class);
	private static final int THREAD_BUF_SIZE = 128 * 1024;
	private final ThreadLocal<ByteBuffer> threadBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(THREAD_BUF_SIZE));
	private final String mirroredRoot;
	private final FileSystem fileSystem;
	private final AtomicInteger fileHandleCounter = new AtomicInteger(32);
	private final ConcurrentMap<Integer, FileChannel> openFiles = new ConcurrentHashMap<>(10, 0.75f, 2);

	public MirrorFs(Path mirroredPath) {
		super();
		this.mirroredRoot = mirroredPath.toString();
		this.fileSystem = mirroredPath.getFileSystem();
	}

	private final Function<Try.CheckedSupplier<Integer, IOException>, Integer> tryCatch = (supplier) -> {
		return Try.withCatch(supplier, IOException.class) //
				.recoverFor(AccessDeniedException.class, e -> -ErrorCodes.EPERM()) //
				.recoverFor(NoSuchFileException.class, e -> -ErrorCodes.ENOENT()) //
				.recoverFor(NotDirectoryException.class, e -> -ErrorCodes.ENOTDIR()) //
				.recoverFor(IOException.class, e -> {
					logger.warn("", e); // Unmapped IOException, log warning
					return -ErrorCodes.EIO();
				}).orElse(-ErrorCodes.EIO());
	};

	private final Function<Try.CheckedRunnable<IOException>, Integer> tryCatchRunnable = (runnable) -> {
		return tryCatch.apply(() -> {
			runnable.run();
			return 0;
		});
	};

	@Override
	public String getFSName() {
		return "mirrorFs";
	}

	@Override
	public int getattr(String path, StatFiller stat) {
		logger.debug(path);
		return tryCatchRunnable.apply(() -> stat.stat(real(path)));
	}

	@Override
	public int readdir(String path, DirectoryFiller filler) {
		Path realPath = readdirInitial(path, filler);
		return tryCatch.apply(() -> {
			try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(realPath)) {
				for (Path entry : directoryStream) {
					if (!add(filler, entry)) {
						return 0;
					}
				}
			}
			return 0;
		});
	}

	@Override
	public int open(String path, FileHandleFiller filler) {
		logger.info(path);
		Path real = real(path);
		return tryCatchRunnable.apply(() -> {
			FileChannel channel = FileChannel.open(real, StandardOpenOption.READ);
			int fileHandle = fileHandleCounter.getAndIncrement();
			openFiles.put(fileHandle, channel);
			filler.setFileHandle(fileHandle);
		});
	}

	@Override
	public int read(String path, Consumer<byte[]> buf, int size, long offset, int fileHandle) {
		FileChannel fileChannel = openFiles.get(fileHandle);
		logger.trace("{} {} {}", path, size, offset);
		return readFromFileChannel(buf, offset, size, fileChannel);
	}

	protected int readFromFileChannel(Consumer<byte[]> buf, long offset, int size, FileChannel fileChannel) {
		if (fileChannel == null) {
			return -ErrorCodes.EBADF();
		}
		ByteBuffer byteBuffer = threadBuffer.get();
		byteBuffer.clear();
		byteBuffer.limit(size);
		try {
			int bytesRead = fileChannel.read(byteBuffer, offset); // Read into native memory
			if (bytesRead <= 0) { // EOF
				return 0;
			}
			byte[] intermediate = new byte[bytesRead]; // This copies native memory into JVM
			byteBuffer.rewind();
			byteBuffer.get(intermediate);
			buf.accept(intermediate); // And then back =(
			return bytesRead;
		} catch (Exception e) {
			logger.warn("", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int release(String path, int fileHandle) {
		logger.info("release({}, {})", fileHandle, path);
		FileChannel fileChannel = openFiles.remove(fileHandle);
		if (fileChannel == null) {
			return -ErrorCodes.EBADF();
		}
		safeClose(fileChannel);
		return 0;
	}

	@Override
	public void destroy() {
		logger.info("Cleaning up");
		openFiles.forEach((fh, channel) -> safeClose(channel));
		openFiles.clear();
	}

	protected void safeClose(FileChannel fileChannel) {
		Optional.ofNullable(fileChannel).map(fc -> Try.runWithCatch(() -> fileChannel.close(), IOException.class).onFail(e -> logger.trace("", e)));
	}

	/**
	 * Adds stats for specified entry into the directory enumeration.
	 *
	 * @param filler
	 *            The directory enumeration
	 * @param entry
	 *            The path to add
	 * @return false only on enumeration resource exhaustion. Any IOException is ignored, logged, and returns true.
	 */
	protected boolean add(DirectoryFiller filler, Path entry) {
		return getFileName(entry).flatMap(fileName -> Try.withCatch(() -> filler.add(fileName, entry) == 0, IOException.class, NoSuchFileException.class) //
				.onFail(e -> logger.trace("", e)).toOptional()).orElse(true); // Ignore, files might get deleted / renamed while iterating
	}

	protected Optional<String> getFileName(Path entry) {
		return Optional.ofNullable(entry).flatMap(notNull -> Optional.ofNullable(notNull.getFileName())).map(Object::toString);
	}

	protected Path readdirInitial(String path, DirectoryFiller filler) {
		Path realPath = real(path);
		try {
			filler.add(".", realPath);
		} catch (IOException e) {
			logger.trace("", e);
		}
		try {
			filler.add("..", real(path, ".."));
		} catch (IOException e) {
			logger.trace("", e);
		}
		return realPath;
	}

	protected Path real(String... virtual) {
		return fileSystem.getPath(mirroredRoot, virtual);
	}

}
