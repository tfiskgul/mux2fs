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

import static se.tfiskgul.mux2fs.Constants.MUX_WAIT_LOOP_MS;
import static se.tfiskgul.mux2fs.Constants.SUCCESS;
import static se.tfiskgul.mux2fs.mux.Muxer.State.FAILED;
import static se.tfiskgul.mux2fs.mux.Muxer.State.NOT_STARTED;
import static se.tfiskgul.mux2fs.mux.Muxer.State.RUNNING;
import static se.tfiskgul.mux2fs.mux.Muxer.State.SUCCESSFUL;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import se.tfiskgul.mux2fs.fs.base.Sleeper;

/**
 * TODO: Add cancel()
 *
 * TODO: Support for multiple srtFiles
 */
public class Muxer {

	private static final Logger logger = LoggerFactory.getLogger(Muxer.class);
	private final Path mkv;
	private final Path srt;
	private final Path tempDir;
	private final Path output;
	private final AtomicReference<State> state = new AtomicReference<Muxer.State>(NOT_STARTED);
	private volatile Process process;
	private final ProcessBuilderFactory factory;
	private final Sleeper sleeper;

	public enum State {
		NOT_STARTED, RUNNING, SUCCESSFUL, FAILED
	}

	@FunctionalInterface
	public static interface ProcessBuilderFactory {
		ProcessBuilder from(String... command);
	}

	@FunctionalInterface
	public static interface MuxerFactory {
		Muxer from(Path mkv, Path srt, Path tempDir);

		static MuxerFactory defaultFactory() {
			return (mkv, srt, tempDir) -> Muxer.of(mkv, srt, tempDir);
		}
	}

	private Muxer(Path mkv, Path srt, Path tempDir, ProcessBuilderFactory factory, Sleeper sleeper) {
		this.mkv = mkv;
		this.srt = srt;
		this.tempDir = tempDir;
		UUID randomUUID = UUID.randomUUID();
		this.output = tempDir.resolve(randomUUID.toString() + ".mkv");
		this.factory = factory;
		this.sleeper = sleeper;
	}

	public static Muxer of(Path mkv, Path srt, Path tempDir) {
		return new Muxer(mkv, srt, tempDir, command -> new ProcessBuilder(command), (ms) -> Thread.sleep(ms));
	}

	@VisibleForTesting
	static Muxer of(Path mkv, Path srt, Path tempDir, ProcessBuilderFactory factory, Sleeper sleeper) {
		return new Muxer(mkv, srt, tempDir, factory, sleeper);
	}

	/**
	 * Starts this Muxer, if not already started.
	 *
	 * This is thread safe to be called at any time, multiple times. Returns immediately.
	 *
	 * @throws IOException
	 */
	public void start()
			throws IOException {
		if (state.compareAndSet(NOT_STARTED, RUNNING)) {
			try {
				access(mkv, AccessMode.READ);
				access(srt, AccessMode.READ);
				access(tempDir, AccessMode.WRITE);
				output.toFile().deleteOnExit();
				ProcessBuilder builder = factory.from("mkvmerge", "-o", output.toString(), mkv.toString(), srt.toString());
				builder.directory(tempDir.toFile()).inheritIO(); // TODO: Better solution than inheritIO
				process = builder.start();
			} catch (Exception e) {
				state.set(FAILED);
				deleteWarn(output);
				throw e;
			}
		}
	}

	private void deleteWarn(Path path) {
		if (!path.toFile().delete()) {
			logger.warn("Failed to delete {}", path);
		}
	}

	private void access(Path path, AccessMode mode)
			throws IOException {
		path.getFileSystem().provider().checkAccess(path, mode);
	}

	public State state() {
		State current = state.get();
		if (current == RUNNING) {
			if (process != null && !process.isAlive()) { // NOPMD
				if (process.exitValue() == SUCCESS) {
					state.set(SUCCESSFUL);
					return SUCCESSFUL;
				} else {
					state.set(FAILED);
					deleteWarn(output);
					return FAILED;
				}
			}
		}
		return current;
	}

	// TODO: A better Result class wrapping stdout + stderr as well as the code.
	public int waitFor()
			throws InterruptedException {
		switch (state()) {
			case NOT_STARTED:
				throw new IllegalStateException("Not started");
			case FAILED:
				return process != null ? process.exitValue() : -127;
			case RUNNING:
			case SUCCESSFUL:
				return process.waitFor();
			default:
				throw new IllegalStateException("BUG: Unkown state");
		}
	}

	public boolean waitFor(long timeout, TimeUnit unit)
			throws InterruptedException {
		switch (state()) {
			case NOT_STARTED:
				throw new IllegalStateException("Not started");
			case FAILED:
				return true;
			case RUNNING:
			case SUCCESSFUL:
				return process.waitFor(timeout, unit);
			default:
				throw new IllegalStateException("BUG: Unkown state");
		}
	}

	public Optional<Path> getOutput() {
		State current = state();
		if (current == RUNNING || current == SUCCESSFUL) {
			return Optional.of(output);
		}
		return Optional.empty();
	}

	@VisibleForTesting
	Path getOutputForTest() {
		return output;
	}

	@Override
	public int hashCode() {
		return Objects.hash(mkv, output, srt, tempDir);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Muxer other = (Muxer) obj;
		return Objects.equals(mkv, other.mkv) && Objects.equals(output, other.output) && Objects.equals(srt, other.srt)
				&& Objects.equals(tempDir, other.tempDir);
	}

	@Override
	public String toString() {
		return "Muxer [mkv=" + mkv + ", srt=" + srt + ", tempDirPath=" + tempDir + ", output=" + output + ", state=" + state + ", process=" + process + "]";
	}

	public Path getMkv() {
		return mkv;
	}

	public boolean waitForOutput() {
		final File file = output.toFile();
		while (!file.isFile() && state() == RUNNING) {
			try {
				sleeper.sleep(MUX_WAIT_LOOP_MS);
			} catch (InterruptedException e) {
				logger.info("{} was interrupted", this, e);
				return false;
			}
		}
		return file.isFile();
	}
}
