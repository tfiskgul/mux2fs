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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.tfiskgul.mux2fs.CommandLineArguments.Strict;

public class Main {

	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args)
			throws IOException {
		try {
			Strict parsed = CommandLineArguments.parse(args);
			if (parsed.isHelp()) {
				System.out.println(parsed.getHelp());
				System.exit(0);
			}
			parsed.validate();
		} catch (Exception e) {
			System.err.println(CommandLineArguments.getUsage());
			throw e;
		}
	}
}
