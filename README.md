# mux2fs [![Build Status](https://travis-ci.org/tfiskgul/mux2fs.svg?branch=master)](https://travis-ci.org/tfiskgul/mux2fs) [![Coveralls](https://img.shields.io/coveralls/tfiskgul/mux2fs.svg)](https://coveralls.io/github/tfiskgul/mux2fs) [![Dependency Status](https://www.versioneye.com/user/projects/58c450f362d6020040aec7d1/badge.svg)](https://www.versioneye.com/user/projects/58c450f362d6020040aec7d1) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/2f8f8753add947b996c767f0ef037606)](https://www.codacy.com/app/tfiskgul/mux2fs?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=tfiskgul/mux2fs&amp;utm_campaign=Badge_Grade)

[![Join the chat at https://gitter.im/tfiskgul/mux2fs](https://badges.gitter.im/tfiskgul/mux2fs.svg)](https://gitter.im/tfiskgul/mux2fs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
Muxes subtitles into Matroska files as a FUSE filesystem

Usage
------
	mux2fs source mountpoint -o tempdir=<tempdir>,[options]
	mux2fs --source source --target mountpoint --tempdir tempdir [options]


About
------
mux2fs takes the _source_ directory and mirrors it under _mountpoint_, with a few changes. Files ending in .mkv are matched against files ending in .srt, and if they match, they are muxed using mkvmerge in _tempdir_.

Example:

|Source| |Mount point| |
|---|---|---|---|
|Name|Size|Name|Size
|file1.mkv|700 KiB|file1.mkv|712 KiB
|file1.eng.srt|12 KiB|
|file2.mkv|600 KiB|file2.mkv|607 KiB
|file2.srt|7 KiB|
|file2.txt|2 KiB|file2.txt|2 KiB


Requirements
------
* Java 8
* [mkvtoolnix](https://github.com/mbunkus/mkvtoolnix): Creating and working with Matroska files
* [fuse](https://github.com/libfuse/libfuse): The reference implementation of the Linux FUSE (Filesystem in Userspace) interface


Security implications
------
mux2fs should **not** be run as root.
It should be mounted with the -o ro flag, as it will never support any write operations.
It also should be mounted with the -o default_permissions flag, as it does no access or security checking.


Building
------
	gradle build

or

	./gradlew build


Installation
------
Pick a Debian or RPM package from [releases](https://github.com/tfiskgul/mux2fs/releases) and install it using the package manager of your distribution.


Alternate installation
------
**WARNING**
This might be unsafe. It will require an exitsing sudo ticket (or just run sudo before).

	sudo true && gradle deploy


Core dependencies
------
* [jnr-fuse](https://github.com/SerCeMan/jnr-fuse): jnr-fuse is a FUSE implementation in java using Java Native Runtime (JNR)
* [jcommander](https://github.com/cbeust/jcommander): Annotation based parameter parsing framework for Java 8
* [guava](https://github.com/google/guava): Google Core Libraries for Java
* [cyclops-react](https://github.com/aol/cyclops-react): An advanced, but easy to use, platform for writing functional applications in Java 8


Performance
------
Do not expect stellar performance, as performance is neither a goal, nor a strong consideration, of this project. Expect reading somewhere between 500-700 MB/s from a file in RAM. The performance should be good enough to stream media files from disk.


Supported platforms
------
| Supported platforms |     |      |
|---------------------|-----|------|
| Linux               | x64 | x86  |