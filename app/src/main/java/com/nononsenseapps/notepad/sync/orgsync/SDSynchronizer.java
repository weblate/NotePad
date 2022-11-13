/*
 * Copyright (c) 2015 Jonas Kalderstam.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.nononsenseapps.notepad.sync.orgsync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.FileObserver;
import android.preference.PreferenceManager;

import com.nononsenseapps.notepad.prefs.SyncPrefs;
import com.nononsenseapps.util.PermissionsHelper;
import com.nononsenseapps.util.SharedPreferencesHelper;

import org.cowboyprogrammer.org.OrgFile;
import org.cowboyprogrammer.org.parser.OrgParser;
import org.cowboyprogrammer.org.parser.RegexParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;

/**
 * A synchronizer that that uses an external directory on the SD-card as
 * destination.
 */
public class SDSynchronizer extends Synchronizer implements SynchronizerInterface {

	private final static String SERVICENAME = "SDORG";

	/**
	 * if SD sync is enabled
	 */
	protected final boolean configured;

	/**
	 * Filesystem path of the folder where files are kept. User changeable in preferences.
	 */
	protected String ORG_DIR;

	public SDSynchronizer(Context context) {
		super(context);
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		Uri favoriteOrgDir;
		String favoriteUriStr = prefs.getString(SyncPrefs.KEY_SD_DIR_URI, null);
		if (favoriteUriStr == null)
			favoriteOrgDir = Uri.fromFile(new File(getDefaultOrgDir(context))); // TODO does it even work ??
		else favoriteOrgDir = Uri.parse(favoriteUriStr);

		// TODO actually use the user-selected directory for ORG files in "favoriteOrgDir":
		//  android's framework forces the use of URIs instead of file paths, so for now we ignore
		//  the user's choice and stick to the default directory, which can be used in File classes

		ORG_DIR = getDefaultOrgDir(context);
		final boolean permitted = PermissionsHelper.hasPermissions(context, PermissionsHelper.PERMISSIONS_SD);
		if (permitted) {
			configured = SharedPreferencesHelper.isSdSyncEnabled(context);
		} else {
			configured = false;
			// disable SD
			SharedPreferencesHelper.disableSdCardSync(context);
		}

	}

	/**
	 * @return the path of the default directory where ORG files are saved. It's something like
	 * /storage/emulated/0/Android/data/packagename/files/orgfiles/
	 */
	public static String getDefaultOrgDir(Context ctx) {
		File dir = ctx.getExternalFilesDir("orgfiles");
		return dir.getAbsolutePath();
	}

	/**
	 * @return A unique name for this service. Should be descriptive, like
	 * SDOrg or SSHOrg.
	 */
	@Override
	public String getAccountName() {
		return SERVICENAME;
	}

	/**
	 * @return The username of the configured service. Likely an e-mail.
	 */
	@Override
	public String getServiceName() {
		return SERVICENAME;
	}

	/**
	 * Returns true if the synchronizer has been configured. This is called
	 * before synchronization. It will be true if the user has selected an
	 * account, folder etc...
	 */
	@Override
	public boolean isConfigured() {
		// TODO handle errors
		if (this.configured) {
			File d = new File(ORG_DIR);
			if (!d.isDirectory()) {
				d.mkdir();
			}

			return this.configured && d.isDirectory();
		}

		return this.configured;
	}

	/**
	 * Returns an OrgFile object with a filename set that is guaranteed to
	 * not already exist. Use this method to avoid having multiple objects
	 * pointing to the same file. Also prevents names with slashes.
	 *
	 * @param orgdesiredName The name you'd want. If it exists,
	 *                       it will be used as the base in desiredName1,
	 *                       desiredName2, etc. Limited to 99.
	 * @return an OrgFile guaranteed not to exist.
	 * @throws java.io.IOException
	 */
	@Override
	public OrgFile getNewFile(final String orgdesiredName) throws
			IOException, IllegalArgumentException {
		OrgParser orgParser = new RegexParser();
		// Replace slashes with underscores
		String desiredName = orgdesiredName.replace("/", "_");
		String filename;
		for (int i = 0; i < 100; i++) {
			if (i == 0) {
				filename = desiredName + ".org";
			} else {
				filename = desiredName + i + ".org";
			}
			File f = new File(ORG_DIR, filename);
			if (!f.exists()) {
				return new OrgFile(orgParser, filename);
			}
		}
		throw new IllegalArgumentException("Filename not accessible");
	}

	/**
	 * Replaces the file on the remote end with the given content.
	 *
	 * @param orgFile The file to save. Uses the filename stored in the object.
	 */
	@Override
	public void putRemoteFile(OrgFile orgFile) throws IOException {
		final File file = new File(ORG_DIR, orgFile.getFilename());
		final BufferedWriter bw = new BufferedWriter(new FileWriter(file));
		bw.write(orgFile.treeToString());
		bw.close();
	}

	/**
	 * Delete the file on the remote end.
	 *
	 * @param orgFile The file to delete.
	 */
	@Override
	public void deleteRemoteFile(OrgFile orgFile) {
		if (orgFile != null && orgFile.getFilename() != null) {
			final File file = new File(ORG_DIR, orgFile.getFilename());
			file.delete();
		}
	}

	/**
	 * Rename the file on the remote end.
	 *
	 * @param oldName The name it is currently stored as on the remote end.
	 * @param orgFile
	 */
	@Override
	public void renameRemoteFile(String oldName, OrgFile orgFile) {
		if (orgFile == null || orgFile.getFilename() == null) {
			throw new NullPointerException("No new filename");
		}
		final File oldFile = new File(ORG_DIR, oldName);
		final File newFile = new File(ORG_DIR, orgFile.getFilename());
		oldFile.renameTo(newFile);
	}

	/**
	 * Returns a BufferedReader to the remote file. Null if it doesn't exist.
	 *
	 * @param filename Name of the file, without path
	 */
	@Override
	public BufferedReader getRemoteFile(String filename) {
		final File file = new File(ORG_DIR, filename);
		BufferedReader br = null;
		if (file.exists()) {
			try {
				br = new BufferedReader(new FileReader(file));
			} catch (FileNotFoundException e) {
				br = null;
			}
		}

		return br;
	}

	/**
	 * @return a set of all remote files.
	 */
	@SuppressLint("DefaultLocale")
	@Override
	public HashSet<String> getRemoteFilenames() {
		final HashSet<String> filenames = new HashSet<>();
		final File dir = new File(ORG_DIR);
		final File[] files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".org"));

		if (files != null) {
			for (File f : files) {
				filenames.add(f.getName());
			}
		}

		return filenames;
	}

	/**
	 * Use this to disconnect from any services and cleanup.
	 */
	@Override
	public void postSynchronize() {
		// Nothing to do
	}

	@Override
	public Monitor getMonitor() {
		return new FileWatcher(ORG_DIR);
	}

	public static class FileWatcher extends FileObserver implements Monitor {

		public OrgSyncService.SyncHandler handler;
		private final int changeId = 0;

		public FileWatcher(String path) {
			super(path, FileObserver.CREATE | FileObserver.DELETE
					| FileObserver.DELETE_SELF | FileObserver.MODIFY
					| FileObserver.MOVE_SELF | FileObserver.MOVED_FROM
					| FileObserver.MOVED_TO);
		}

		@Override
		public void onEvent(int event, String path) {
			if (handler != null) {
				handler.onMonitorChange();
			}
		}

		@Override
		public void startMonitor(final OrgSyncService.SyncHandler handler) {
			this.handler = handler;
			startWatching();
		}

		@Override
		public void pauseMonitor() {
			stopWatching();
			handler = null;
		}

		@Override
		public void terminate() {
			stopWatching();
		}
	}
}
