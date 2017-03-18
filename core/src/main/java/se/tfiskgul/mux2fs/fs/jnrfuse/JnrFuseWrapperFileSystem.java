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
package se.tfiskgul.mux2fs.fs.jnrfuse;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Path;

import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import se.tfiskgul.mux2fs.fs.base.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.base.FileSystem;

public final class JnrFuseWrapperFileSystem extends FuseStubFS implements NamedJnrFuseFileSystem {

	private final FileSystem delegate;

	public JnrFuseWrapperFileSystem(FileSystem delegate) {
		this.delegate = delegate;
	}

	@Override
	public String getFSName() {
		return delegate.getFSName();
	}

	@Override
	public int getattr(String path, FileStat stat) {
		JnrFuseUnixFileStat unixFileStat = new JnrFuseUnixFileStat();
		int result = delegate.getattr(path, unixFileStat);
		if (result == 0) {
			unixFileStat.fill(stat);
		}
		return result;
	}

	@Override
	public int readdir(String path, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi) {
		DirectoryFiller filler = new FuseDirectoryFiller(buf, filter);
		return delegate.readdir(path, filler);
	}

	@Override
	public int readlink(String path, Pointer buf, long size) {
		int intSize = (int) size;
		return delegate.readLink(path, (name) -> buf.putString(0, name, intSize, UTF_8), intSize);
	}

	@Override
	public int open(String path, FuseFileInfo fi) {
		return delegate.open(path, fh -> fi.fh.set(fh));
	}

	@Override
	public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		if (size >= Integer.MAX_VALUE) {
			return -ErrorCodes.EINVAL();
		}
		return delegate.read(path, (data) -> buf.put(0, data, 0, data.length), (int) size, offset, fi.fh.intValue());
	}

	@Override
	public int release(String path, FuseFileInfo fi) {
		return delegate.release(path, fi.fh.intValue());
	}

	@Override
	public final void destroy(Pointer initResult) {
		delegate.destroy();
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
			JnrFuseUnixFileStat unixFileStat = new JnrFuseUnixFileStat();
			unixFileStat.stat(path);
			unixFileStat.fill(fuseStat);
			return filter.apply(buf, name, fuseStat, 0);
		}

		@Override
		public int addWithExtraSize(String name, Path path, long extraSize)
				throws IOException {
			FileStat fuseStat = new FileStat(Runtime.getSystemRuntime());
			JnrFuseUnixFileStat unixFileStat = new JnrFuseUnixFileStat();
			unixFileStat.stat(path);
			unixFileStat.fill(fuseStat);
			if (extraSize != 0) {
				fuseStat.st_size.set(fuseStat.st_size.longValue() + extraSize);
			}
			return filter.apply(buf, name, fuseStat, 0);
		}
	}
}