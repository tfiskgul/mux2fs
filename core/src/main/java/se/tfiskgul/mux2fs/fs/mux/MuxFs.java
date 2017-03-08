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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static se.tfiskgul.mux2fs.Constants.BUG;
import static se.tfiskgul.mux2fs.Constants.KILOBYTE;
import static se.tfiskgul.mux2fs.Constants.MEGABYTE;
import static se.tfiskgul.mux2fs.Constants.MUX_WAIT_LOOP_MS;
import static se.tfiskgul.mux2fs.Constants.SUCCESS;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import cyclops.control.Try;
import ru.serce.jnrfuse.ErrorCodes;
import se.tfiskgul.mux2fs.fs.base.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.base.FileChannelCloser;
import se.tfiskgul.mux2fs.fs.base.FileHandleFiller;
import se.tfiskgul.mux2fs.fs.base.FileHandleFiller.Recorder;
import se.tfiskgul.mux2fs.fs.base.FileInfo;
import se.tfiskgul.mux2fs.fs.base.Sleeper;
import se.tfiskgul.mux2fs.fs.base.StatFiller;
import se.tfiskgul.mux2fs.fs.mirror.MirrorFs;
import se.tfiskgul.mux2fs.mux.MuxedFile;
import se.tfiskgul.mux2fs.mux.Muxer;
import se.tfiskgul.mux2fs.mux.Muxer.MuxerFactory;
import se.tfiskgul.mux2fs.mux.Muxer.State;

public class MuxFs extends MirrorFs {

	private static final Logger logger = LoggerFactory.getLogger(MuxFs.class);
	private final Path tempDir;
	private final MuxerFactory muxerFactory;
	private final Sleeper sleeper;
	private final ConcurrentMap<FileInfo, Muxer> muxFiles = new ConcurrentHashMap<>(10, 0.75f, 2);
	private final ConcurrentMap<Integer, MuxedFile> openMuxFiles = new ConcurrentHashMap<>(10, 0.75f, 2);
	private final RemovalListener<FileInfo, MuxedFile> closedMuxlistener = new RemovalListener<FileInfo, MuxedFile>() {

		@Override
		public void onRemoval(RemovalNotification<FileInfo, MuxedFile> notification) {
			if (notification.getCause() != RemovalCause.EXPLICIT) {
				MuxedFile muxedFile = notification.getValue();
				// This is racy, at worst we will re-trigger muxing for unlucky files being re-opened
				if (!openMuxFiles.containsValue(muxedFile)) {
					muxFiles.remove(muxedFile.getInfo(), muxedFile.getMuxer());
					logger.info("Expired {}: {} deleted = {}", notification.getCause(), muxedFile, safeDelete(muxedFile));
				} else {
					logger.warn("BUG: Expired {}: {}, but is still open!", notification.getCause(), muxedFile);
				}
			}
		}
	};

	private final Cache<FileInfo, MuxedFile> closedMuxFiles = CacheBuilder.newBuilder() //
			.maximumWeight(50 * KILOBYTE) // FIXME: Mount parameter
			.removalListener(closedMuxlistener) //
			.weigher((info, path) -> (int) (info.getSize() / MEGABYTE)) // Weigher can only take integer, so divide down into MBs
			.expireAfterWrite(20, MINUTES) // FIXME: Mount parameter
			.build();

	public MuxFs(Path mirroredPath, Path tempDir) {
		super(mirroredPath);
		this.tempDir = tempDir;
		this.muxerFactory = MuxerFactory.defaultFactory();
		this.sleeper = (millis) -> Thread.sleep(millis);
	}

	@VisibleForTesting
	MuxFs(Path mirroredPath, Path tempDir, MuxerFactory muxerFactory, Sleeper sleeper, FileChannelCloser fileChannelCloser) {
		super(mirroredPath, fileChannelCloser);
		this.tempDir = tempDir;
		this.muxerFactory = muxerFactory;
		this.sleeper = sleeper;
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
						return SUCCESS;
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
						return SUCCESS;
					}
				}
			}

			// List the non-matching .srt files
			if (subFiles != null) {
				for (Path subFile : subFiles) {
					if (!add(filler, subFile)) {
						return SUCCESS;
					}
				}
			}
			return SUCCESS;
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
		List<Path> subFiles = getMatchingSubFiles(muxFile);
		if (subFiles.isEmpty()) { // It doesn't need to be muxed, open normally
			logger.debug("{} doesn't need muxing", path);
			return super.open(path, filler);
		}
		return Try.withCatch(() -> FileInfo.of(muxFile), Exception.class).map(info -> {
			closedMuxFiles.invalidate(info);
			Muxer muxer = muxerFactory.from(muxFile, subFiles.get(0), tempDir);
			Muxer previous = muxFiles.putIfAbsent(info, muxer); // Others might be racing the same file
			if (previous != null) { // They won the race
				muxer = previous;
			}
			try {
				muxer.start();
				muxer.waitFor(50, MILLISECONDS); // Short wait to catch errors earlier than read()
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
			Recorder recorder = FileHandleFiller.Recorder.wrap(filler);
			int result = super.openReal(muxedPath, recorder);
			if (result == SUCCESS) {
				openMuxFiles.put(recorder.getFileHandle(), new MuxedFile(info, muxer));
			} else {
				logger.warn("Failed to open muxed file {}, falling back to unmuxed file {}", muxedPath, muxFile);
				muxFiles.remove(info, muxer);
				safeDelete(muxedPath);
				result = super.openReal(muxFile, filler);
			}
			return result;
		}).recover(this::translateOrThrow).get();
	}

	@Override
	public int read(String path, Consumer<byte[]> buf, int size, long offset, int fileHandle) {
		MuxedFile muxedFile = openMuxFiles.get(fileHandle);
		if (muxedFile == null) { // Not a muxed file
			return super.read(path, buf, size, offset, fileHandle);
		}
		Muxer muxer = muxedFile.getMuxer();
		State state = muxer.state();
		switch (state) {
			case SUCCESSFUL:
				return super.read(path, buf, size, offset, fileHandle);
			case FAILED:
				return muxingFailed(fileHandle, muxedFile, muxer);
			case RUNNING:
				return readRunningMuxer(path, buf, size, offset, fileHandle, muxedFile, muxer);
			default:
				logger.error("BUG: Unhandled state {} in muxer {}", state, muxer);
				return BUG;
		}
	}

	@Override
	public int release(String path, int fileHandle) {
		logger.info("release({}, {})", fileHandle, path);
		MuxedFile muxed = openMuxFiles.remove(fileHandle);
		if (muxed != null && !openMuxFiles.containsValue(muxed)) {
			// Muxed file is no longer open, save it in cache for quick re-open
			closedMuxFiles.put(muxed.getInfo(), muxed);
		}
		return super.release(path, fileHandle);
	}

	@Override
	public void destroy() {
		super.destroy();
		logger.info("Cleaning up");
		closedMuxFiles.asMap().forEach((info, muxed) -> muxed.getMuxer().getOutput().map(this::safeDelete));
		closedMuxFiles.invalidateAll();
		closedMuxFiles.cleanUp();
		openMuxFiles.forEach((fh, muxed) -> safeDelete(muxed));
		openMuxFiles.clear();
		muxFiles.forEach((fi, muxer) -> muxer.getOutput().map(this::safeDelete));
		muxFiles.clear();
	}

	private boolean safeDelete(MuxedFile file) {
		if (file != null) {
			return file.getMuxer().getOutput().map(this::safeDelete).orElse(false);
		}
		return false;
	}

	private int readRunningMuxer(String path, Consumer<byte[]> buf, int size, long offset, int fileHandle, MuxedFile muxedFile, Muxer muxer) {
		long maxPosition = offset + size; // This could overflow for really big files / sizes, close to 8388608 TB.
		FileChannel channelFor = getChannelFor(fileHandle);
		if (channelFor == null) {
			logger.error("BUG: FileChannel for file handle {} open {} not found", fileHandle, muxedFile);
			return BUG;
		}
		try {
			long muxSize = channelFor.size();
			if (maxPosition >= muxSize) { // Read beyond current mux progress
				logger.debug("{}: read @ {} with mux progress {}, sleeping...", path, maxPosition, muxSize);
				int result = waitForMuxing(muxer, maxPosition, channelFor, fileHandle, muxedFile);
				if (result != 0) {
					return result;
				}
			}
			return super.read(path, buf, size, offset, fileHandle);
		} catch (IOException e) {
			logger.warn("IOException for {}", muxedFile, e);
			return -ErrorCodes.EIO();
		} catch (InterruptedException e) {
			logger.warn("Interrupted while waiting for {}", muxedFile, e);
			return -ErrorCodes.EINTR();
		}
	}

	/**
	 * At this point, we are still muxing, and trying to read beyond muxed data.
	 *
	 * We park here and wait until it is available.
	 */
	private int waitForMuxing(Muxer muxer, long maxPosition, FileChannel fileChannel, int fileHandle, MuxedFile muxedFile)
			throws IOException, InterruptedException {
		long currentSize = 0;
		while (maxPosition >= (currentSize = fileChannel.size())) {
			State state = muxer.state();
			switch (state) {
				case RUNNING:
					logger.debug("Want to read @ {} (file is {}), so waiting for {}", maxPosition, currentSize, muxer);
					sleeper.sleep(MUX_WAIT_LOOP_MS);
					break;
				case SUCCESSFUL:
					logger.debug("Done waiting to read @ {}", maxPosition, muxer);
					return SUCCESS;
				case FAILED:
					return muxingFailed(fileHandle, muxedFile, muxer);
				default:
					logger.error("BUG: Unhandled state {} in muxer {}", state, muxer);
					return BUG;
			}
		}
		logger.debug("Done waiting to read @ {}", maxPosition, muxer);
		return SUCCESS;
	}

	private int muxingFailed(int fileHandle, MuxedFile muxedFile, Muxer muxer) {
		logger.info("Muxing failed for {}", muxer);
		muxFiles.remove(muxedFile.getInfo(), muxer);
		openMuxFiles.remove(fileHandle, muxedFile);
		return -ErrorCodes.EIO();
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

	private long getExtraSizeOf(Path muxFile) {
		return getMatchingSubFiles(muxFile).stream().reduce(0L, (buf, path) -> buf + path.toFile().length(), (a, b) -> a + b);
	}

	private List<Path> getMatchingSubFiles(Path muxFile) {
		return getFileName(muxFile).map(name -> getMatchingSubFiles(muxFile.getParent(), name)).orElse(Collections.emptyList());
	}

	private List<Path> getMatchingSubFiles(Path parent, String muxName) {
		String muxFileNameLower = muxName.toLowerCase().substring(0, muxName.length() - 4);
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(parent)) {
			return StreamSupport.stream(directoryStream.spliterator(), false).filter(entry -> {
				String entryName = entry.getFileName().toString();
				return entryName.endsWith(".srt") && entryName.toLowerCase().startsWith(muxFileNameLower);
			}).collect(toList());
		} catch (IOException e) { // Ignored, non-critical
			logger.trace("", e);
			return Collections.emptyList();
		}
	}

	private boolean addWithExtraSize(DirectoryFiller filler, Path entry, long extraSize) {
		return add(entry, (fileName) -> filler.addWithExtraSize(fileName, entry, extraSize));
	}
}