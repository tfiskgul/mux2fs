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
package se.tfiskgul.mux2fs;

import ru.serce.jnrfuse.ErrorCodes;

public final class Constants {

	public static final int BUG = -ErrorCodes.ENOSYS();
	public static final int FILE_HANDLE_START_NO = 32;
	public static final int SUCCESS = 0;
	public static final int MUX_WAIT_LOOP_MS = 500;
	// Sizes
	public static final long KILOBYTE = 1024;
	public static final long MEGABYTE = 1024 * KILOBYTE;
	public static final long GIGABYTE = 1024 * MEGABYTE;
	public static final int THREAD_BUF_SIZE = (int) (128 * KILOBYTE);

	private Constants() { // NOPMD
	}
}
