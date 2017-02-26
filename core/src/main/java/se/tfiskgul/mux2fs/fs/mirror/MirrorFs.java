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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cyclops.control.Try;
import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.fs.decoupling.DecoupledFileSystem;
import se.tfiskgul.mux2fs.fs.decoupling.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.decoupling.StatFiller;

public class MirrorFs extends DecoupledFileSystem {

	private static final Logger logger = LoggerFactory.getLogger(MirrorFs.class);
	private final String mirroredRoot;
	private final FileSystem fileSystem;

	public MirrorFs(Path mirroredPath) {
		this.mirroredRoot = mirroredPath.toString();
		this.fileSystem = mirroredPath.getFileSystem();
	}

	@Override
	public String getFSName() {
		return "mirrorFs";
	}

	@Override
	public int getattr(String path, StatFiller stat) {
		logger.debug(path);
		int res = 0;
		try {
			stat.stat(real(path));
		} catch (NoSuchFileException | FileNotFoundException e) {
			res = -ErrorCodes.ENOENT();
		} catch (AccessDeniedException e) {
			res = -ErrorCodes.EPERM();
		} catch (IOException e) {
			logger.warn("", e);
			res = -ErrorCodes.EIO();
		}
		return res;
	}

	@Override
	public int readdir(String path, DirectoryFiller filler) {
		Path realPath = readdirInitial(path, filler);
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(realPath)) {
			for (Path entry : directoryStream) {
				if (!add(filler, entry)) {
					return 0;
				}
			}
		} catch (NoSuchFileException e) {
			return -ErrorCodes.ENOENT();
		} catch (IOException e) {
			logger.warn("", e);
			return -ErrorCodes.EIO();
		}
		return 0;
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

	private Optional<String> getFileName(Path entry) {
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
