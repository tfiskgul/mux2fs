package se.tfiskgul.mux2fs.fs.base;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Represents a unique way of identifying an unnamed file (including contents)
 *
 * If the file is modified, the mtime changes. If the meta data changes, ctime changes.
 */
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

	public FileInfo(long inode, Instant mtime, Instant ctime, long size) {
		super();
		this.inode = inode;
		this.mtime = FileTime.from(mtime);
		this.ctime = FileTime.from(ctime);
		this.size = size;
	}

	public static FileInfo of(Path path)
			throws IOException {
		Map<String, Object> attributes = Files.readAttributes(path, "unix:*"); // Follow links in this case
		return new FileInfo((long) attributes.get("ino"), (FileTime) attributes.get("lastModifiedTime"), (FileTime) attributes.get("ctime"),
				(long) attributes.get("size"));
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
