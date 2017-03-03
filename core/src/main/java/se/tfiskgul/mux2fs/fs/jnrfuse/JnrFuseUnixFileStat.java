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

import java.time.Instant;

import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.Timespec;
import se.tfiskgul.mux2fs.fs.base.UnixFileStatImpl;

public class JnrFuseUnixFileStat extends UnixFileStatImpl {

	JnrFuseUnixFileStat() {
	}

	void fill(FileStat stat) {
		stat.st_dev.set(getDev());
		stat.st_ino.set(getIno());
		stat.st_nlink.set(getLinks());
		stat.st_mode.set(getMode());
		stat.st_uid.set(getUid());
		stat.st_gid.set(getGid());
		stat.st_rdev.set(getRdev());
		stat.st_size.set(getSize());
		stat.st_blksize.set(getBlkSize());
		stat.st_blocks.set(getBlocks());
		fillTime(getAccessTime(), stat.st_atim);
		fillTime(getModificationTime(), stat.st_mtim);
		fillTime(getInodeTime(), stat.st_ctim);
	}

	private void fillTime(Instant instant, Timespec timespec) {
		timespec.tv_sec.set(instant.getEpochSecond());
		timespec.tv_nsec.set(instant.getNano());
	}
}
