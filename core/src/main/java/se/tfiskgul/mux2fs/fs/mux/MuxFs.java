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

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import cyclops.control.Try;
import se.tfiskgul.mux2fs.fs.base.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.base.FileHandleFiller;
import se.tfiskgul.mux2fs.fs.base.FileInfo;
import se.tfiskgul.mux2fs.fs.base.StatFiller;
import se.tfiskgul.mux2fs.fs.mirror.MirrorFs;
import se.tfiskgul.mux2fs.mux.Muxer;
import se.tfiskgul.mux2fs.mux.Muxer.MuxerFactory;

public class MuxFs extends MirrorFs {

	private static final Logger logger = LoggerFactory.getLogger(MuxFs.class);
	private final Path tempDir;
	private final ConcurrentMap<FileInfo, Muxer> muxFiles = new ConcurrentHashMap<>(10, 0.75f, 2);
	private final MuxerFactory muxerFactory;

	public MuxFs(Path mirroredPath, Path tempDir) {
		super(mirroredPath);
		this.tempDir = tempDir;
		this.muxerFactory = MuxerFactory.defaultFactory();
	}

	@VisibleForTesting
	MuxFs(Path mirroredPath, Path tempDir, MuxerFactory muxerFactory) {
		super(mirroredPath);
		this.tempDir = tempDir;
		this.muxerFactory = muxerFactory;
	}

	@Override
	public String getFSName() {
		return "mux2fs";
	}

	@Override
	public int readdir(String path, DirectoryFiller filler) {
		logger.debug(path);
		Path real = readdirInitial(path, filler);

		return tryCatch.apply(() -> {
			List<Path> muxFiles = null;
			List<Path> subFiles = null;

			// Read the directory, saving all .mkv and .srt files for further matching
			try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(real)) {
				for (Path entry : directoryStream) {
					String fileName = entry.getFileName().toString();
					if (fileName.endsWith(".mkv")) {
						if (muxFiles == null) {
							muxFiles = new ArrayList<>();
						}
						muxFiles.add(entry);
						continue;
					}
					if (fileName.endsWith(".srt")) {
						if (subFiles == null) {
							subFiles = new ArrayList<>();
						}
						subFiles.add(entry);
						continue;
					}
					if (!add(filler, entry)) {
						return 0;
					}
				}
			}

			// Hide matching .srt files from listing
			if (muxFiles != null) {
				for (Path muxFile : muxFiles) {
					long extraSize = 0;
					String muxFileNameLower = muxFile.getFileName().toString().toLowerCase();
					muxFileNameLower = muxFileNameLower.substring(0, muxFileNameLower.length() - 4);
					if (subFiles != null) {
						Iterator<Path> subIterator = subFiles.iterator();
						while (subIterator.hasNext()) {
							Path subFile = subIterator.next();
							String subFileNameLower = subFile.getFileName().toString().toLowerCase();
							if (subFileNameLower.startsWith(muxFileNameLower)) {
								subIterator.remove();
								extraSize += subFile.toFile().length();
								logger.debug("Hiding {} due to match with {}", subFile, muxFile);
							}
						}
					}
					if (!addWithExtraSize(filler, muxFile, extraSize)) {
						return 0;
					}
				}
			}

			// List the non-matching .srt files
			if (subFiles != null) {
				for (Path subFile : subFiles) {
					if (!add(filler, subFile)) {
						return 0;
					}
				}
			}
			return 0;
		});
	}

	@Override
	public int getattr(String path, StatFiller stat) {
		logger.debug(path);
		if (!path.endsWith(".mkv")) {
			return super.getattr(path, stat);
		}
		Path muxFile = real(path);
		return tryCatchRunnable.apply(() -> stat.statWithExtraSize(muxFile, getExtraSizeOf(muxFile)));
	}

	@Override
	// TODO: Multiple SRT file support
	public int open(String path, FileHandleFiller filler) {
		if (!path.endsWith(".mkv")) {
			return super.open(path, filler);
		}
		logger.info(path);
		Path muxFile = real(path);
		List<Path> subFiles = getMatchingSubtitles(muxFile);
		if (subFiles.isEmpty()) { // It doesn't need to be muxed, open normally
			logger.debug("{} doesn't need muxing", path);
			return super.open(path, filler);
		}
		return Try.withCatch(() -> FileInfo.of(muxFile), Exception.class).map(info -> {
			// TODO: closedMuxFiles.invalidate(info);
			Muxer muxer = muxerFactory.from(muxFile, subFiles.get(0), tempDir);
			Muxer previous = muxFiles.putIfAbsent(info, muxer); // Others might be racing the same file
			if (previous != null) { // They won the race
				muxer = previous;
			}
			try {
				muxer.start();
				muxer.waitFor(50, TimeUnit.MILLISECONDS); // Short wait to catch errors earlier than read()
			} catch (IOException | InterruptedException e) {
				// Something dun goofed. Second best thing is to open the original file then.
				logger.warn("Muxing failed, falling back to unmuxed file {}", muxFile, e);
				// Invalidate the broken muxer. This means the next open will try again, which might not be a good strategy.
				muxFiles.remove(info, muxer);
				return super.open(path, filler);
			}
			Optional<Path> output = muxer.getOutput();
			if (!output.isPresent()) {
				logger.warn("Muxing failed! muxer.getOutput().isPresent() == false, falling back to unmuxed file {}", muxFile);
				// Invalidate the broken muxer. This means the next open will try again, which might not be a good strategy.
				muxFiles.remove(info, muxer);
				return super.open(path, filler); // Fall back to original if no result
			}
			Path muxedPath = output.get();
			int result = super.openReal(muxedPath, filler);
			if (result == 0) {
				// TODO: openMuxFiles.put(fi.fh.get(), new MuxedFile(info, muxer));
			} else {
				logger.warn("Failed to open muxed file {}, falling back to unmuxed file {}", muxedPath, muxFile);
				muxFiles.remove(info, muxer);
				safeDelete(muxedPath);
				result = super.openReal(muxFile, filler);
			}
			return result;
		}).recover(this::translateOrThrow).get();
	}

	private boolean safeDelete(Path path) {
		if (path != null) {
			try {
				return path.toFile().delete();
			} catch (Exception e) {
				logger.warn("Failed to delete {} ", path, e);
			}
		}
		return false;
	}

	private List<Path> getMatchingSubtitles(Path muxFile) {
		return matchingSubFiles(muxFile).collect(toList());
	}

	private long getExtraSizeOf(Path muxFile) {
		return matchingSubFiles(muxFile).reduce(0L, (buf, path) -> buf + path.toFile().length(), (a, b) -> a + b);
	}

	private Stream<Path> matchingSubFiles(Path muxFile) {
		return getFileName(muxFile).map(name -> matchingSubFiles(muxFile.getParent(), name)).orElse(Stream.empty());
	}

	private Stream<Path> matchingSubFiles(Path parent, String muxName) {
		String muxFileNameLower = muxName.toLowerCase().substring(0, muxName.length() - 4);
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(parent)) {
			return StreamSupport.stream(directoryStream.spliterator(), false).filter(entry -> {
				String entryName = entry.getFileName().toString();
				return entryName.endsWith(".srt") && entryName.toLowerCase().startsWith(muxFileNameLower);
			});
		} catch (IOException e) { // Ignored, non-critical
			logger.trace("", e);
			return Stream.empty();
		}
	}

	private boolean addWithExtraSize(DirectoryFiller filler, Path entry, long extraSize) {
		return add(entry, (fileName) -> filler.addWithExtraSize(fileName, entry, extraSize));
	}
}