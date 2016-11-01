package regression;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Util {

	private static final String USER_DIR = System.getProperty("user.dir") + "/";
	private static final String TEST_ROOT = USER_DIR + "src/test/resources/";

	private Util() {
		throw new Error("Utility class");
	}

	public static String testDirectoryPath(String name) {
		return TEST_ROOT + name + "/";
	}

	public static File testDirectory(String name) {
		return new File(testDirectoryPath(name));
	}

	/**
	 * Compiles a sourceFile into a temp folder and returns this folder or null
	 * if compilation fails.
	 *
	 * @param sourceFile
	 *            the source file to compile
	 * @return the folder that contains the class file(s) or null if compilation
	 *         fails.
	 * @throws IOException
	 */
	public static File compileJavaFile(File sourceFile) throws IOException {
		final File tempDir = getTempDir();
		final String javac_command = String.format("javac -g %s -d %s",
				sourceFile.getAbsolutePath(), tempDir.getAbsolutePath());

		ProcessBuilder pb = new ProcessBuilder(javac_command.split(" "));
		pb.redirectOutput(Redirect.INHERIT);
		pb.redirectError(Redirect.INHERIT);
		Process p = pb.start();

		try {
			p.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}

		return tempDir;
	}

	/**
	 * Compiles a set of sourceFiles into a temp folder and returns this folder
	 * or null if compilation fails.
	 * 
	 * @param sourceFiles
	 *            an array of files to compile
	 * @return the folder that contains the class file(s) or null if compilation
	 *         fails.
	 * @throws IOException
	 */
	public static File compileJavaFiles(File[] sourceFiles) throws IOException {
		final File tempDir = getTempDir();
		StringBuilder sb = new StringBuilder();
		for (File f : sourceFiles) {
			sb.append(f.getAbsolutePath());
			sb.append(" ");
		}
		final String javac_command = String.format("javac -g -d %s %s", tempDir.getAbsolutePath(), sb.toString());

		System.out.println(javac_command);

		ProcessBuilder pb = new ProcessBuilder(javac_command.split(" "));
		pb.redirectOutput(Redirect.INHERIT);
		pb.redirectError(Redirect.INHERIT);
		Process p = pb.start();

		try {
			p.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}

		return tempDir;
	}

	public static File getTempDir() throws IOException {
		final File tempDir = File.createTempFile("bixie_test_temp", Long.toString(System.nanoTime()));
		if (!(tempDir.delete())) {
			throw new IOException("Could not delete temp file: " + tempDir.getAbsolutePath());
		}
		if (!(tempDir.mkdir())) {
			throw new IOException("Could not create temp directory: " + tempDir.getAbsolutePath());
		}
		return tempDir;
	}

	public static List<Object[]> getData(File testDirectory) {
		final Path start = Paths.get(testDirectory.toString());
		final List<Object[]> data = new CopyOnWriteArrayList<>();

		try {
			java.nio.file.Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					data.add(new Object[] { file.toFile(), file.toFile().getName() });

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			// ignores malformed files
		}
		return data;
	}

}
