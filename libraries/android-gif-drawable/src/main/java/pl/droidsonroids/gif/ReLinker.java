/**
 * Copyright 2015 KeepSafe Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.droidsonroids.gif;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Based on https://github.com/KeepSafe/ReLinker
 * ReLinker is a small library to help alleviate {@link UnsatisfiedLinkError} exceptions thrown due
 * to Android's inability to properly install / load native libraries for Android versions before
 * API 21
 */
@TargetApi(Build.VERSION_CODES.GINGERBREAD)
class ReLinker {
	private static final String LIB_DIR = "lib";
	private static final int MAX_TRIES = 5;
	private static final int COPY_BUFFER_SIZE = 8192;

	private ReLinker() {
		// No instances
	}

	/**
	 * Utilizes the regular system call to attempt to load a native library. If a failure occurs,
	 * then the function extracts native .so library out of the app's APK and attempts to load it.
	 * <p/>
	 * <strong>Note: This is a synchronous operation</strong>
	 */
	@SuppressLint("UnsafeDynamicallyLoadedCode") //intended fallback of System#loadLibrary()
	static void loadLibrary(Context context, final String library) {
		final String libName = System.mapLibraryName(library);
		synchronized (ReLinker.class) {
			final File workaroundFile = unpackLibrary(context, libName);
			System.load(workaroundFile.getAbsolutePath());
		}
	}

	/**
	 * Attempts to unpack the given library to the workaround directory. Implements retry logic for
	 * IO operations to ensure they succeed.
	 *
	 * @param context {@link Context} to describe the location of the installed APK file
	 * @param libName The name of the library to load
	 */
	private static File unpackLibrary(final Context context, final String libName) {
		File outputFile = new File(context.getDir(LIB_DIR, Context.MODE_PRIVATE), libName + BuildConfig.VERSION_NAME);
		if (outputFile.isFile()) {
			return outputFile;
		}

		final File cachedLibraryFile = new File(context.getCacheDir(), libName + BuildConfig.VERSION_NAME);
		if (cachedLibraryFile.isFile()) {
			return cachedLibraryFile;
		}

		final FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.startsWith(libName);
			}
		};
		clearOldLibraryFiles(outputFile, filter);
		clearOldLibraryFiles(cachedLibraryFile, filter);

		final ApplicationInfo appInfo = context.getApplicationInfo();
		final File apkFile = new File(appInfo.sourceDir);
		ZipFile zipFile = null;
		try {
			zipFile = openZipFile(apkFile);

			int tries = 0;
			while (tries++ < MAX_TRIES) {
				ZipEntry libraryEntry = findLibraryEntry(libName, zipFile);
				if (libraryEntry == null) {
					throw new IllegalStateException("Library " + libName + " for supported ABIs not found in APK file");
				}

				InputStream inputStream = null;
				FileOutputStream fileOut = null;
				try {
					inputStream = zipFile.getInputStream(libraryEntry);
					fileOut = new FileOutputStream(outputFile);
					copy(inputStream, fileOut);
				} catch (IOException e) {
					if (tries > MAX_TRIES / 2) {
						outputFile = cachedLibraryFile;
					}
					continue;
				} finally {
					closeSilently(inputStream);
					closeSilently(fileOut);
				}
				setFilePermissions(outputFile);
				break;
			}
		} finally {
			//Should not use closeSilently() on ZipFile.
			//Because ZipFile DO NOT implement Closeable when API < 19.Otherwise, app will crash!!
			//http://bugs.java.com/view_bug.do?bug_id=6389768
			try {
				if (zipFile != null) {
					zipFile.close();
				}
			} catch (IOException ignored) {
			}
		}
		return outputFile;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@SuppressWarnings("deprecation") //required for old API levels
	private static ZipEntry findLibraryEntry(final String libName, final ZipFile zipFile) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			for (final String abi : Build.SUPPORTED_ABIS) {
				final ZipEntry libraryEntry = getEntry(libName, zipFile, abi);
				if (libraryEntry != null) {
					return libraryEntry;
				}
			}
		}
		return getEntry(libName, zipFile, Build.CPU_ABI);
	}

	private static ZipEntry getEntry(final String libName, final ZipFile zipFile, final String abi) {
		return zipFile.getEntry("lib/" + abi + "/" + libName);
	}

	private static ZipFile openZipFile(final File apkFile) {
		int tries = 0;
		ZipFile zipFile = null;
		while (tries++ < MAX_TRIES) {
			try {
				zipFile = new ZipFile(apkFile, ZipFile.OPEN_READ);
				break;
			} catch (IOException ignored) {
				//no-op, optionally retried
			}
		}

		if (zipFile == null) {
			throw new IllegalStateException("Could not open APK file: " + apkFile.getAbsolutePath());
		}
		return zipFile;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored") //intended, nothing useful can be done
	private static void clearOldLibraryFiles(final File outputFile, final FilenameFilter filter) {
		final File[] fileList = outputFile.getParentFile().listFiles(filter);
		if (fileList != null) {
			for (File file : fileList) {
				file.delete();
			}
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored") //intended, nothing useful can be done
	@SuppressLint("SetWorldReadable") //intended, default permission
	private static void setFilePermissions(File outputFile) {
		// Try change permission to rwxr-xr-x
		outputFile.setReadable(true, false);
		outputFile.setExecutable(true, false);
		outputFile.setWritable(true);
	}

	/**
	 * Copies all data from an {@link InputStream} to an {@link OutputStream}.
	 *
	 * @param in  The stream to read from.
	 * @param out The stream to write to.
	 * @throws IOException when a stream operation fails.
	 */
	private static void copy(InputStream in, OutputStream out) throws IOException {
		final byte[] buf = new byte[COPY_BUFFER_SIZE];
		while (true) {
			final int bytesRead = in.read(buf);
			if (bytesRead == -1) {
				break;
			}
			out.write(buf, 0, bytesRead);
		}
	}

	/**
	 * Closes a {@link Closeable} silently (without throwing or handling any exceptions)
	 *
	 * @param closeable {@link Closeable} to close
	 */
	private static void closeSilently(final Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (IOException ignored) {
			//no-op
		}
	}
}