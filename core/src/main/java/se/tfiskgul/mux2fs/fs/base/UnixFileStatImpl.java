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
package se.tfiskgul.mux2fs.fs.base;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class UnixFileStatImpl implements UnixFileStat, StatFiller {

	private static final int BLK_SIZE = 4096;
	private long dev;
	private long ino;
	private int links;
	private int mode;
	private int uid;
	private int gid;
	private long rdev;
	private long size;
	private int blkSize;
	private long blocks;
	private Instant accessTime;
	private Instant modificationTime;
	private Instant inodeTime;

	protected UnixFileStatImpl() {
		// NoOp
	}

	@Override
	public long getDev() {
		return dev;
	}

	private void setDev(long dev) {
		this.dev = dev;
	}

	@Override
	public long getIno() {
		return ino;
	}

	private void setIno(long ino) {
		this.ino = ino;
	}

	@Override
	public int getLinks() {
		return links;
	}

	private void setLinks(int links) {
		this.links = links;
	}

	@Override
	public int getMode() {
		return mode;
	}

	private void setMode(int mode) {
		this.mode = mode;
	}

	@Override
	public int getUid() {
		return uid;
	}

	private void setUid(int uid) {
		this.uid = uid;
	}

	@Override
	public int getGid() {
		return gid;
	}

	private void setGid(int gid) {
		this.gid = gid;
	}

	@Override
	public long getRdev() {
		return rdev;
	}

	private void setRdev(long rdev) {
		this.rdev = rdev;
	}

	@Override
	public long getSize() {
		return size;
	}

	private void setSize(long size) {
		this.size = size;
	}

	@Override
	public int getBlkSize() {
		return blkSize;
	}

	private void setBlkSize(int blkSize) {
		this.blkSize = blkSize;
	}

	@Override
	public long getBlocks() {
		return blocks;
	}

	private void setBlocks(long blocks) {
		this.blocks = blocks;
	}

	@Override
	public Instant getAccessTime() {
		return accessTime;
	}

	private void setAccessTime(Instant accessTime) {
		this.accessTime = accessTime;
	}

	@Override
	public Instant getModificationTime() {
		return modificationTime;
	}

	private void setModificationTime(Instant modificationTime) {
		this.modificationTime = modificationTime;
	}

	@Override
	public Instant getInodeTime() {
		return inodeTime;
	}

	private void setInodeTime(Instant inodeTime) {
		this.inodeTime = inodeTime;
	}

	@Override
	public UnixFileStat stat(Path path)
			throws IOException {
		Map<String, Object> map = Files.readAttributes(path, "unix:*", LinkOption.NOFOLLOW_LINKS);
		setDev((long) map.get("dev"));
		setIno((long) map.get("ino"));
		setLinks((int) map.get("nlink"));
		setMode((int) map.get("mode"));
		setUid((int) map.get("uid"));
		setGid((int) map.get("gid"));
		setRdev((long) map.get("rdev"));
		long size = (long) map.get("size");
		setSize(size);
		setBlkSize(BLK_SIZE);
		setBlocks(size / 512 + 1); // Fake it till you make it
		setAccessTime(getInstant(map, "lastAccessTime"));
		setModificationTime(getInstant(map, "lastModifiedTime"));
		setInodeTime(getInstant(map, "ctime"));
		return this;
	}

	private Instant getInstant(Map<String, Object> map, String string) {
		FileTime time = (FileTime) map.get(string);
		return time.toInstant();
	}

	@Override
	public UnixFileStat statWithSize(Path path, Function<FileInfo, Optional<Long>> sizeGetter, Supplier<Long> extraSizeGetter)
			throws IOException {
		stat(path);
		FileInfo fileInfo = new FileInfo(ino, modificationTime, inodeTime, size);
		this.size = sizeGetter.apply(fileInfo).orElse(this.size + extraSizeGetter.get());
		return this;
	}
}
