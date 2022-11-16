package com.nononsenseapps.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.nononsenseapps.helpers.NnnLogger;
import com.nononsenseapps.notepad.prefs.BackupPrefs;
import com.nononsenseapps.notepad.prefs.SyncPrefs;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * Methods to help navigate through Google's mess regarding file access in android 10
 * and higher.
 *
 * Functions that start with "document" are related to {@link DocumentFile}
 * objects, which Google recommends, but which we can still avoid, for now.
 */
public final class FileHelper {

	public static boolean documentIsWritableFolder(DocumentFile docDir) {
		return docDir != null && docDir.exists() && docDir.isDirectory() && docDir.canWrite();
	}

	/**
	 * @return a {@link FileDescriptor} for the File at the given {@link Uri}, or NULL if it
	 * could not find one
	 */
	private static FileDescriptor getFileDescriptor(@NonNull Uri docUri,
													@NonNull Context context) {
		// TODO this is here for the poor soul that will try to migrate from File to DocumentFile,
		//  but as of now this code is useless
		var docFile = DocumentFile.fromTreeUri(context, docUri);
		if (docFile == null || docFile.isDirectory()) return null;

		try {
			ParcelFileDescriptor parcelFileDescriptor = context
					.getContentResolver()
					.openFileDescriptor(docUri, "r");
			FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();

			boolean ok = fileDescriptor.valid();

			return fileDescriptor;

		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Writes the given {@link String} to the given {@link File}
	 *
	 * @return TRUE if it worked, FALSE otherwise
	 */
	public static boolean writeStringToFile(String content, File target) {
		if (content == null || target == null || target.isDirectory()) return false;
		target.getParentFile().mkdirs();

		try {
			target.createNewFile();
		} catch (IOException e) {
			// you just can't write to that folder
			return false;
		}

		try (PrintStream out = new PrintStream(new FileOutputStream(target.getAbsolutePath()))) {
			out.print(content);
			return true;
		} catch (Exception e) {
			NnnLogger.exception(e);
			return false;
		}
	}

	// TODO see https://stackoverflow.com/a/59536115/6307322

	/**
	 * Returns the folder used by the app to save files with the normal {@link File} objects.
	 * It is a subdirectory of {@link Environment#DIRECTORY_DOCUMENTS}
	 */
	public static File getAppExternalStorageFolder() {
		// android 10 and newer don't allow us to make a folder in the "root" of the external
		// storage. The workaround is to make the directory in the "Documents" folder
		File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
		File ourDir = new File(baseDir, "NoNonsenseNotes");

		// must ensure that it exists
		ourDir.mkdirs();
		return ourDir;
	}

	/**
	 * @return the path of the directory where ORG files are saved, or NULL if
	 * it could get one. It can be one of these: <br/>
	 * /storage/emulated/0/Android/data/packagename/files/orgfiles/ <br/>
	 * /storage/emulated/0/Documents/NoNonsenseNotes/
	 */
	public static String getUserSelectedOrgDir(@NonNull Context ctx) {
		// see if the user requested the Documents directory
		var sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		boolean useDocDir = sharedPrefs.getBoolean(SyncPrefs.KEY_SD_USE_DOC_DIR, false);

		// eventually you should use a DocumentFile from this:
		// Uri dir = Uri.parse( sharedPrefs.getString(SyncPrefs.KEY_SD_DIR_URI,null));

		File dir;
		if (useDocDir) {
			// the user requested the Documents directory => save org files in:
			// /storage/emulated/0/Documents/NoNonsenseNotes/
			dir = getAppExternalStorageFolder();
		} else {
			// we are going to use the default directory:
			// /storage/emulated/0/Android/data/packagename/files/orgfiles/
			dir = ctx.getExternalFilesDir("orgfiles");
		}

		boolean ok = dir != null && dir.exists() && dir.isDirectory() && dir.canWrite();
		if (ok) return dir.getAbsolutePath();
		else return null;
	}

	/**
	 * @return a representation of the JSON file used for backups, located in the
	 * folder chosen by the user in the Backup preferences page. NOT guaranteed
	 * to be writable
	 */
	@NonNull
	public static File getBackupJsonFile(@NonNull Context ctx) {
		var sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		String chosenPath = sharedPrefs.getString(BackupPrefs.KEY_BACKUP_LOCATION, null);
		if (chosenPath == null) {
			// the user did not choose a path yet => use a safe fallback path
			chosenPath = ctx.getExternalFilesDir(null).getAbsolutePath();
		}

		String fName = "NoNonsenseNotes_Backup.json";
		File fJson = new File(chosenPath, fName);

		// checks like .mkdirs() and .canWrite() are up to the caller.
		// The code already took care of those, anyway
		return fJson;
	}

	/**
	 * @return an array of folder paths where files can be saved using the simple
	 * {@link File} API, without bothering with filepickers and the Storage Access
	 * Framework. The list consists of:<br/>
	 * the app's folder in <i>/Android/data/</i> <br/>
	 * the <i>Download</i> directory <br/>
	 * the <i>Documents</i> directory <br/>
	 * and subdirectories of those. <br/>
	 * Every other folder is either dedicated to audio files, therefore useless for us, or
	 * impossible to access in Android API >= 30 without using the DocumentFile API.
	 * @implNote As of android 12, you can write files to all these folders without the
	 * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE} permission
	 */
	public static String[] getPathsOfPossibleFolders(@NonNull Context context) {
		File dirDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		// (don't use DCIM, you can't write to it anymore with the File API)
		File dirDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
		String subDirName = "No Nonsense Notes/";

		var dirs = new File[] {
				// the safest, recommended option
				context.getExternalFilesDir(null),
				// The directories themselves
				dirDownload,
				dirDocs,
				// a subfolder that identifies the app
				new File(dirDownload, subDirName),
				new File(dirDocs, subDirName),
		};

		String[] paths = Arrays.stream(dirs)
				.map(File::getAbsolutePath)
				.toArray(String[]::new);
		return paths;
	}

	/**
	 * When you delete a file in android, additional attention is required.
	 * This function takes care of that
	 */
	public static boolean tryDeleteFile(@NonNull File toDelete, @NonNull Context context) {
		if (toDelete.exists()) {
			try {
				if (!toDelete.delete()) return false;
			} catch (SecurityException e) {
				return false;
			}
		}

		// once you successfully deleted it, you have to update the media scanner to
		// let android know that the file was deleted, ELSE IT WILL CRASH!
		MediaScannerConnection.scanFile(context, new String[] { toDelete.getAbsolutePath() },
				null, null);

		// wait a bit for the mediascanner to do its work
		// 2 seconds should be enough
		SystemClock.sleep(1900);

		return true;
	}

}
