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
package se.tfiskgul.mux2fs.fs.decoupling;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import se.tfiskgul.mux2fs.fs.base.NamedFileSystem;

public abstract class DecoupledFileSystem extends FuseStubFS implements NamedFileSystem {

	@Override
	public final int getattr(String path, FileStat stat) {
		UnixFileStatImpl unixFileStat = new UnixFileStatImpl();
		int result = getattr(path, unixFileStat);
		if (result == 0) {
			unixFileStat.fill(stat);
		}
		return result;
	}

	@Override
	public final int readdir(String path, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi) {
		DirectoryFiller filler = new FuseDirectoryFiller(buf, filter);
		return readdir(path, filler);
	}

	@Override
	public final int open(String path, FuseFileInfo fi) {
		return open(path, fh -> fi.fh.set(fh));
	}

	@Override
	public final int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		if (size >= Integer.MAX_VALUE) {
			return -ErrorCodes.EINVAL();
		}
		return read(path, (data) -> buf.put(0, data, 0, data.length), (int) size, offset, fi.fh.intValue());
	}

	@Override
	public final int release(String path, FuseFileInfo fi) {
		return release(path, fi.fh.intValue());
	}

	@Override
	public final void destroy(Pointer initResult) {
		destroy();
	}

	private static class FuseDirectoryFiller implements DirectoryFiller {

		private final Pointer buf;
		private final FuseFillDir filter;

		private FuseDirectoryFiller(Pointer buf, FuseFillDir filter) {
			super();
			this.buf = buf;
			this.filter = filter;
		}

		@Override
		public int add(String name, Path path)
				throws IOException {
			FileStat fuseStat = new FileStat(Runtime.getSystemRuntime());
			UnixFileStatImpl unixFileStat = new UnixFileStatImpl();
			unixFileStat.stat(path);
			unixFileStat.fill(fuseStat);
			return filter.apply(buf, name, fuseStat, 0);
		}
	}

	@Override
	public abstract String getFSName();

	public abstract int getattr(String path, StatFiller stat);

	public abstract int readdir(String path, DirectoryFiller filler);

	public abstract int open(String path, FileHandleFiller filler);

	public abstract int read(String path, Consumer<byte[]> buf, int size, long offset, int fileHandle);

	public abstract int release(String path, int fileHandle);

	public abstract void destroy();
}