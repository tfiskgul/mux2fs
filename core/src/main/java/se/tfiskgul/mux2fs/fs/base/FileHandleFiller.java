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

@FunctionalInterface
public interface FileHandleFiller {

	void setFileHandle(int fileHandle);

	public static class Recorder implements FileHandleFiller {

		private final FileHandleFiller delegate;
		private int fileHandle = -1;

		public static Recorder wrap(FileHandleFiller filler) {
			return new Recorder(filler);
		}

		private Recorder(FileHandleFiller filler) {
			this.delegate = filler;
		}

		public int getFileHandle() {
			return fileHandle;
		}

		@Override
		public void setFileHandle(int fileHandle) {
			this.fileHandle = fileHandle;
			delegate.setFileHandle(fileHandle);
		}
	}
}
