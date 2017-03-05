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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import se.tfiskgul.mux2fs.fs.base.FileInfo;

@Immutable
public class MuxedFile {

	private final FileInfo info;
	private final Muxer muxer;

	public MuxedFile(FileInfo info, Muxer muxer) {
		super();
		this.info = info;
		this.muxer = muxer;
	}

	public FileInfo getInfo() {
		return info;
	}

	public Muxer getMuxer() {
		return muxer;
	}

	@Override
	public String toString() {
		return "MuxedFile [info=" + info + ", muxer=" + muxer + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(info, muxer);
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
		MuxedFile other = (MuxedFile) obj;
		return Objects.equals(info, other.info) && Objects.equals(muxer, other.muxer);
	}
}