package se.tfiskgul.mux2fs.fs.base;

import java.nio.file.attribute.FileTime;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

@Immutable
public class FileInfo {

	private final long inode;
	private final FileTime mtime;
	private final FileTime ctime;
	private final long size;

	public FileInfo(long inode, FileTime mtime, FileTime ctime, long size) {
		super();
		this.inode = inode;
		this.mtime = mtime;
		this.ctime = ctime;
		this.size = size;
	}

	public long getInode() {
		return inode;
	}

	public FileTime getMtime() {
		return mtime;
	}

	public FileTime getCtime() {
		return ctime;
	}

	public long getSize() {
		return size;
	}

	@Override
	public int hashCode() {
		return Objects.hash(inode, mtime, ctime, size);
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
		FileInfo other = (FileInfo) obj;
		return Objects.equals(inode, other.inode) && Objects.equals(mtime, other.mtime) && Objects.equals(ctime, other.ctime)
				&& Objects.equals(size, other.size);
	}

	@Override
	public String toString() {
		return "FileInfo [inode=" + inode + ", mtime=" + mtime + ", ctime=" + ctime + ", size=" + size + "]";
	}

}
