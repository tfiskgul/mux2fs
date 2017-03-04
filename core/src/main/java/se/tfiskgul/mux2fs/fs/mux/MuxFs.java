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
package se.tfiskgul.mux2fs.fs.mux;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.tfiskgul.mux2fs.fs.base.DirectoryFiller;
import se.tfiskgul.mux2fs.fs.mirror.MirrorFs;

public class MuxFs extends MirrorFs {

	private static final Logger logger = LoggerFactory.getLogger(MuxFs.class);

	public MuxFs(Path mirroredPath) {
		super(mirroredPath);
	}

	@Override
	public String getFSName() {
		return "mux2fs";
	}

	@Override
	public int readdir(String path, DirectoryFiller filler) {
		logger.debug(path);
		Path real = readdirInitial(path, filler);

		return tryCatch.apply(() -> {
			List<Path> muxFiles = null;
			List<Path> subFiles = null;

			// Read the directory, saving all .mkv and .srt files for further matching
			try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(real)) {
				for (Path entry : directoryStream) {
					String fileName = entry.getFileName().toString();
					if (fileName.endsWith(".mkv")) {
						if (muxFiles == null) {
							muxFiles = new ArrayList<>();
						}
						muxFiles.add(entry);
						continue;
					}
					if (fileName.endsWith(".srt")) {
						if (subFiles == null) {
							subFiles = new ArrayList<>();
						}
						subFiles.add(entry);
						continue;
					}
					if (!add(filler, entry)) {
						return 0;
					}
				}
			}

			// Hide matching .srt files from listing
			if (muxFiles != null) {
				for (Path muxFile : muxFiles) {
					String muxFileNameLower = muxFile.getFileName().toString().toLowerCase();
					muxFileNameLower = muxFileNameLower.substring(0, muxFileNameLower.length() - 4);
					if (subFiles != null) {
						Iterator<Path> subIterator = subFiles.iterator();
						while (subIterator.hasNext()) {
							Path subFile = subIterator.next();
							String subFileNameLower = subFile.getFileName().toString().toLowerCase();
							if (subFileNameLower.startsWith(muxFileNameLower)) {
								subIterator.remove();
								logger.debug("Hiding {} due to match with {}", subFile, muxFile);
							}
						}
					}
					if (!add(filler, muxFile)) {
						return 0;
					}
				}
			}

			// List the non-matching .srt files
			if (subFiles != null) {
				for (Path subFile : subFiles) {
					if (!add(filler, subFile)) {
						return 0;
					}
				}
			}
			return 0;
		});
	}
}