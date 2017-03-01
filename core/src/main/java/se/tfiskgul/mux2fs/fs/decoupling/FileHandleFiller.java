package se.tfiskgul.mux2fs.fs.decoupling;

@FunctionalInterface
public interface FileHandleFiller {

	void setFileHandle(int fileHandle);
}
