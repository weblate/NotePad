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

package com.nononsenseapps.helpers;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.nononsenseapps.notepad.ActivityMain;
import com.nononsenseapps.notepad.R;
import com.nononsenseapps.notepad.database.Task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class NotificationHelper extends BroadcastReceiver {

	// Intent notification argument
	public static final String NOTIFICATION_CANCEL_ARG = "notification_cancel_arg";
	public static final String NOTIFICATION_DELETE_ARG = "notification_delete_arg";

	static final String ARG_TASKID = "taskid";
	private static final String ACTION_COMPLETE = "com.nononsenseapps.notepad.ACTION.COMPLETE";
	private static final String ACTION_SNOOZE = "com.nononsenseapps.notepad.ACTION.SNOOZE";
	private static final String ACTION_RESCHEDULE = "com.nononsenseapps.notepad.ACTION.RESCHEDULE";
	public static final String CHANNEL_ID = "remindersNotificationChannelId";

	private static ContextObserver observer = null;

	private static ContextObserver getObserver(final Context context) {
		if (observer == null) {
			observer = new ContextObserver(context, null);
		}
		return observer;
	}

	/**
	 * Fires notifications that have elapsed and sets an alarm to be woken at
	 * the next notification.
	 *
	 * If the intent action is ACTION_DELETE, will delete the notification with
	 * the indicated ID, and cancel it from any active notifications.
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
				|| Intent.ACTION_RUN.equals(intent.getAction())) {
			// Can't cancel anything. Just schedule and notify at end
		} else {
			// Always cancel
			cancelNotification(context, intent.getData());

			if (Intent.ACTION_DELETE.equals(intent.getAction())
					|| ACTION_RESCHEDULE.equals(intent.getAction())) {
				// Just a notification
				com.nononsenseapps.notepad.database.Notification
						.deleteOrReschedule(context, intent.getData());
			} else if (ACTION_SNOOZE.equals(intent.getAction())) {
				// msec/sec * sec/min * 30
				long delay30min = 1000 * 60 * 30;
				final Calendar now = Calendar.getInstance();

				com.nononsenseapps.notepad.database.Notification
						.setTime(context, intent.getData(), delay30min + now.getTimeInMillis());
			} else if (ACTION_COMPLETE.equals(intent.getAction())) {
				// Complete note
				Task.setCompletedSynced(context, true,
						intent.getLongExtra(ARG_TASKID, -1));
				// Delete notifications with the same task id
				com.nononsenseapps.notepad.database.Notification
						.removeWithTaskIdsSynced(context, intent.getLongExtra(ARG_TASKID, -1));
			}
		}

		notifyPast(context, true);
		scheduleNext(context);
	}

	/**
	 * creates the notification channel needed on API 26 and higher to show notifications.
	 * This is safe to call multiple times
	 */
	@TargetApi(Build.VERSION_CODES.O)
	@RequiresApi(Build.VERSION_CODES.O)
	public static void createNotificationChannel(final Context context, NotificationManager nm) {
		String name = context.getString(R.string.notification_channel_name);
		String description = context.getString(R.string.notification_channel_description);

		// not higher, not lower. This is equivalent to notifications before API 24
		int importance = NotificationManager.IMPORTANCE_DEFAULT;

		NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
		channel.setDescription(description);
		nm.createNotificationChannel(channel);
	}

	private static void monitorUri(final Context context) {
		context.getContentResolver().unregisterContentObserver(getObserver(context));
		context.getContentResolver().registerContentObserver(
				com.nononsenseapps.notepad.database.Notification.URI, true,
				getObserver(context));
	}


	public static void clearNotification(@NonNull final Context context, @NonNull final Intent
			intent) {
		if (intent.getLongExtra(NOTIFICATION_DELETE_ARG, -1) > 0) {
			com.nononsenseapps.notepad.database.Notification.deleteOrReschedule(context,
					com.nononsenseapps.notepad.database.Notification.getUri(
							intent.getLongExtra(NOTIFICATION_DELETE_ARG, -1)));
		}
		if (intent.getLongExtra(NOTIFICATION_CANCEL_ARG, -1) > 0) {
			NotificationHelper.cancelNotification(context,
					(int) intent.getLongExtra(NOTIFICATION_CANCEL_ARG, -1));
		}
	}

	public static void unnotifyGeofence(final Context context,
										final long... ids) {
		final NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		String idStrings = "(";
		for (Long id : ids) {
			idStrings += id + ",";
		}
		idStrings = idStrings.substring(0, idStrings.length() - 1);
		idStrings += ")";

		final Cursor c = context
				.getContentResolver()
				.query(com.nononsenseapps.notepad.database.Notification.URI_WITH_TASK_PATH,
						com.nononsenseapps.notepad.database.Notification.ColumnsWithTask.FIELDS,
						com.nononsenseapps.notepad.database.Notification.Columns._ID
								+ " IN " + idStrings, null, null);

		try {
			while (c.moveToNext()) {
				com.nononsenseapps.notepad.database.Notification not
						= new com.nononsenseapps.notepad.database.Notification(c);
				if (not.taskID != null) {
					notificationManager.cancel(not.taskID.intValue());
				}
			}
		} finally {
			c.close();
		}
	}

	/**
	 * Displays notifications that have a time occurring in the past (and no
	 * location). If no notifications like that exist, will make sure to cancel
	 * any notifications showing.
	 */
	private static void notifyPast(Context context, boolean alertOnce) {
		// Get list of past notifications
		final Calendar now = Calendar.getInstance();

		final List<com.nononsenseapps.notepad.database.Notification> notifications
				= com.nononsenseapps.notepad.database.Notification
				.getNotificationsWithTime(context, now.getTimeInMillis(), true);

		// Remove duplicates
		makeUnique(context, notifications);

		final NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(context, notificationManager);
		}

		NnnLogger.debugOnly(NotificationHelper.class, "Number of notifications: " + notifications.size());

		// If empty, cancel
		if (notifications.isEmpty()) {
			// cancelAll permanent notifications here if/when that is
			// implemented. Don't touch others.
			// Dont do this, it clears location
			// notificationManager.cancelAll();
		} else {
			// else, notify
			// Fetch sound and vibrate settings
			final SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(context);

			// Always use default lights
			int lightAndVibrate = Notification.DEFAULT_LIGHTS;
			// If vibrate on, use default vibration pattern also
			if (prefs.getBoolean(context.getString(R.string.key_pref_vibrate),
					false)) lightAndVibrate |= Notification.DEFAULT_VIBRATE;

			// Need to get a new one because the action buttons will duplicate
			// otherwise
			NotificationCompat.Builder builder;

			// if (false)
			// //
			// prefs.getBoolean(context.getString(R.string.key_pref_group_on_lists),
			// // false))
			// {
			// // Group together notes contained in the same list.
			// // Always use listid
			// for (long listId : getRelatedLists(notifications)) {
			// builder = getNotificationBuilder(context,
			// Integer.parseInt(prefs.getString(
			// context.getString(R.string.key_pref_prio),
			// "0")), lightAndVibrate,
			// Uri.parse(prefs.getString(context
			// .getString(R.string.key_pref_ringtone),
			// "DEFAULT_NOTIFICATION_URI")), alertOnce);
			//
			// List<com.nononsenseapps.notepad.database.Notification> subList =
			// getSubList(
			// listId, notifications);
			// if (subList.size() == 1) {
			// // Notify as single
			// notifyBigText(context, notificationManager, builder,
			// listId, subList.get(0));
			// }
			// else {
			// notifyInboxStyle(context, notificationManager, builder,
			// listId, subList);
			// }
			// }
			// }
			// else {

			final int priority = Integer.parseInt(
					prefs.getString(context.getString(R.string.key_pref_prio), "0"));
			final Uri ringtone = Uri.parse(
					prefs.getString(context.getString(R.string.key_pref_ringtone), "DEFAULT_NOTIFICATION_URI"));

			// Notify for each individually
			for (com.nononsenseapps.notepad.database.Notification note : notifications) {
				builder = getNotificationBuilder(context, priority, lightAndVibrate, ringtone, alertOnce);
				notifyBigText(context, notificationManager, builder, note);
			}
			// }
		}
	}

	/**
	 * Returns a notification builder set with non-item specific properties.
	 */
	private static NotificationCompat.Builder getNotificationBuilder(
			final Context context, final int priority,
			final int lightAndVibrate, final Uri ringtone,
			final boolean alertOnce) {
		// useless ? the small icon should be enough
		final Bitmap largeIcon = BitmapFactory
				.decodeResource(context.getResources(), R.drawable.app_icon);

		final NotificationCompat.Builder builder = new NotificationCompat
				.Builder(context, CHANNEL_ID) // let's use only 1 channel in this app
				.setWhen(0)
				.setSmallIcon(R.drawable.ic_stat_notification_edit)
				.setLargeIcon(largeIcon)
				.setPriority(priority) // TODO always use NotificationCompat.PRIORITY_DEFAULT instead ?
				.setDefaults(lightAndVibrate)
				.setAutoCancel(true)
				.setOnlyAlertOnce(alertOnce)
				.setSound(ringtone);
		return builder;
	}

	/**
	 * Remove from the database, and the specified list, duplicate
	 * notifications. The result is that each note is only associated with ONE
	 * EXPIRED notification.
	 */
	private static void makeUnique(
			final Context context,
			final List<com.nononsenseapps.notepad.database.Notification> notifications) {
		// get duplicates and iterate over them
		for (com.nononsenseapps.notepad.database.Notification noti : getLatestOccurence(notifications)) {
			// remove all but the first one from database, and big list
			for (com.nononsenseapps.notepad.database.Notification dupNoti : getDuplicates(
					noti, notifications)) {
				notifications.remove(dupNoti);
				cancelNotification(context, dupNoti);
				// Cancelled called in delete
				dupNoti.deleteOrReschedule(context);
			}
		}

	}

	/**
	 * Returns the first occurrence of each note's notification. Effectively the
	 * returned list has unique elements with regard to the note id.
	 */
	private static List<com.nononsenseapps.notepad.database.Notification> getLatestOccurence(
			final List<com.nononsenseapps.notepad.database.Notification> notifications) {
		final ArrayList<Long> seenIds = new ArrayList<>();
		final ArrayList<com.nononsenseapps.notepad.database.Notification> firsts = new ArrayList<>();

		com.nononsenseapps.notepad.database.Notification noti;
		for (int i = notifications.size() - 1; i >= 0; i--) {
			noti = notifications.get(i);
			if (!seenIds.contains(noti.taskID)) {
				seenIds.add(noti.taskID);
				firsts.add(noti);
			}
		}
		return firsts;
	}

	private static List<com.nononsenseapps.notepad.database.Notification> getDuplicates(
			final com.nononsenseapps.notepad.database.Notification first,
			final List<com.nononsenseapps.notepad.database.Notification> notifications) {
		final ArrayList<com.nononsenseapps.notepad.database.Notification> dups = new ArrayList<>();

		for (com.nononsenseapps.notepad.database.Notification noti : notifications) {
			if (noti.taskID == first.taskID && noti._id != first._id) {
				dups.add(noti);
			}
		}
		return dups;
	}

	/**
	 * Needs the builder that contains non-note specific values.
	 */
	private static void notifyBigText(final Context context,
									  final NotificationManager notificationManager,
									  final NotificationCompat.Builder builder,
									  final com.nononsenseapps.notepad.database.Notification note) {
		// create the intent that reacts to deleting the notification
		final Intent iDelete = new Intent(context, NotificationHelper.class)
				.setAction(Intent.ACTION_DELETE)
				.setData(note.getUri());
		if (note.repeats != 0) {
			iDelete.setAction(ACTION_RESCHEDULE);
		}
		// Add extra so we don't delete all
		// if (note.time != null) {
		// iDelete.putExtra(ARG_MAX_TIME, note.time);
		// }
		iDelete.putExtra(ARG_TASKID, note.taskID);
		// Delete it on clear
		PendingIntent piDelete = PendingIntent.getBroadcast(context, 0,
				iDelete, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		// Open intent
		final Intent openIntent = new Intent(Intent.ACTION_VIEW, Task.getUri(note.taskID));
		// Should create a new instance to avoid fragment problems
		openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

		// Repeating reminders should have a delete intent:
		// opening the note should delete/reschedule the notification
		openIntent.putExtra(NOTIFICATION_DELETE_ARG, note._id);

		// Opening always cancels the notification though
		openIntent.putExtra(NOTIFICATION_CANCEL_ARG, note._id);

		// Open note on click
		PendingIntent clickIntent = PendingIntent.getActivity(context, 0,
				openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		// Action to complete
		Intent iComplete = new Intent(context, NotificationHelper.class)
				.setAction(ACTION_COMPLETE)
				.setData(note.getUri())
				.putExtra(ARG_TASKID, note.taskID);
		PendingIntent piComplete = PendingIntent.getBroadcast(context, 0,
				iComplete, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		// Action to snooze
		Intent iSnooze = new Intent(context, NotificationHelper.class)
				.setAction(ACTION_SNOOZE)
				.setData(note.getUri())
				.putExtra(ARG_TASKID, note.taskID);
		PendingIntent piSnooze = PendingIntent.getBroadcast(context, 0,
				iSnooze, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		// Build notification
		builder.setContentTitle(note.taskTitle)
				.setContentText(note.taskNote)
				.setChannelId(CHANNEL_ID)
				.setContentIntent(clickIntent)
				.setStyle(
						new NotificationCompat.BigTextStyle()
								.bigText(note.taskNote));

		// the Delete intent for non-location repeats
		builder.setDeleteIntent(piDelete);

		// Snooze button only on time non-repeating
		if (note.time != null && note.repeats == 0) {
			builder.addAction(R.drawable.ic_alarm_24dp_white, context.getText(R.string.snooze), piSnooze);
		}
		// Complete button only on non-repeating, both time and location
		if (note.repeats == 0) {
			builder.addAction(R.drawable.ic_check_24dp_white, context.getText(R.string.completed), piComplete);
		}

		final Notification noti = builder.build();
		notificationManager.notify((int) note._id, noti);
	}

	// private static void notifyInboxStyle(
	// final Context context,
	// final NotificationManager notificationManager,
	// final NotificationCompat.Builder builder,
	// final Long idToUse,
	// final List<com.nononsenseapps.notepad.database.Notification>
	// notifications) {
	// // Delete intent must delete all notifications
	// Intent delint = new Intent(Intent.ACTION_DELETE,
	// com.nononsenseapps.notepad.database.Notification.URI);
	// // Add extra so we don't delete all
	// final long maxTime = getLatestTime(notifications);
	// delint.putExtra(ARG_MAX_TIME, maxTime);
	// delint.putExtra(ARG_LISTID, idToUse);
	// PendingIntent deleteIntent = PendingIntent.getBroadcast(context, 0,
	// delint, PendingIntent.FLAG_UPDATE_CURRENT);
	//
	// // Open intent should open the list
	// PendingIntent clickIntent = PendingIntent.getActivity(context, 0,
	// new Intent(Intent.ACTION_VIEW, TaskList.getUri(idToUse),
	// context, ActivityMain_.class),
	// PendingIntent.FLAG_UPDATE_CURRENT);
	//
	// final String title = notifications.get(0).listTitle + " ("
	// + notifications.size() + ")";
	// // Build notification
	// builder.setContentTitle(title).setNumber(notifications.size())
	// .setContentText(notifications.get(0).taskTitle)
	// .setContentIntent(clickIntent).setDeleteIntent(deleteIntent);
	//
	// // Action to snooze
	// PendingIntent snoozeIntent = PendingIntent.getBroadcast(
	// context,
	// 0,
	// new Intent(Intent.ACTION_EDIT, TaskList.getUri(idToUse))
	// .putExtra(ARG_SNOOZE, true)
	// .putExtra(ARG_LISTID, idToUse)
	// .setClass(context, NotificationHelper.class),
	// PendingIntent.FLAG_UPDATE_CURRENT);
	// builder.addAction(R.drawable.ic_stat_snooze,
	// context.getText(R.string.snooze), snoozeIntent);
	//
	// // Action to complete
	// PendingIntent completeIntent = PendingIntent.getBroadcast(
	// context,
	// 0,
	// new Intent(Intent.ACTION_DELETE, TaskList.getUri(idToUse))
	// .putExtra(ARG_COMPLETE, true)
	// .putExtra(ARG_LISTID, idToUse)
	// .putExtra(ARG_MAX_TIME, maxTime)
	// .setClass(context, NotificationHelper.class),
	// PendingIntent.FLAG_UPDATE_CURRENT);
	// builder.addAction(R.drawable.navigation_accept_dark,
	// context.getText(R.string.completed), completeIntent);
	//
	// NotificationCompat.InboxStyle ib = new NotificationCompat.InboxStyle()
	// .setBigContentTitle(title);
	// if (notifications.size() > 6)
	// ib.setSummaryText("+" + (notifications.size() - 6) + " "
	// + context.getString(R.string.more));
	//
	// for (com.nononsenseapps.notepad.database.Notification e : notifications)
	// {
	// ib.addLine(e.taskTitle);
	// }
	//
	// final Notification noti = builder.setStyle(ib).build();
	// notificationManager.notify(idToUse.intValue(), noti);
	// }

	private static long getLatestTime(
			final List<com.nononsenseapps.notepad.database.Notification> notifications) {
		long latest = 0;
		for (com.nononsenseapps.notepad.database.Notification noti : notifications) {
			if (noti.time > latest) latest = noti.time;
		}
		return latest;
	}

	/**
	 * Schedules to be woken up at the next notification time.
	 */
	private static void scheduleNext(Context context) {
		// Get first future notification
		final Calendar now = Calendar.getInstance();
		final List<com.nononsenseapps.notepad.database.Notification> notifications
				= com.nononsenseapps.notepad.database.Notification
				.getNotificationsWithTime(context, now.getTimeInMillis(), false);

		// if not empty, schedule alarm wake up
		if (!notifications.isEmpty()) {
			// at first's time
			// Create a new PendingIntent and add it to the AlarmManager
			Intent intent = new Intent(Intent.ACTION_RUN);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
					1, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
			AlarmManager am = (AlarmManager) context
					.getSystemService(Context.ALARM_SERVICE);
			am.cancel(pendingIntent);
			am.set(AlarmManager.RTC_WAKEUP, notifications.get(0).time,
					pendingIntent);
		}

		monitorUri(context);
	}

	/**
	 * Schedules coming notifications, and displays expired ones. Only
	 * notififies once for existing notifications.
	 */
	public static void schedule(final Context context) {
		notifyPast(context, true);
		scheduleNext(context);
	}

	/**
	 * Updates/Inserts notifications in the database. Immediately notifies and
	 * schedules next wake up on finish.
	 *
	 * @param context
	 * @param notification
	 */
	public static void updateNotification(final Context context,
										  final com.nononsenseapps.notepad.database.Notification notification) {
		/*
		 * Only don't insert if update is success This way the editor can update
		 * a deleted notification and still have it persisted in the database
		 */
		boolean shouldInsert = true;
		// If id is -1, then this should be inserted
		if (notification._id > 0) {
			// Else it should be updated
			int result = notification.save(context);
			if (result > 0) shouldInsert = false;
		}

		if (shouldInsert) {
			notification._id = -1;
			notification.save(context);
		}

		notifyPast(context, true);
		scheduleNext(context);
	}

	/**
	 * Deletes the indicated notification from the notification tray (does not
	 * touch database)
	 *
	 * Called by notification.delete()
	 *
	 * @param context
	 * @param not
	 */
	public static void cancelNotification(final Context context,
										  final com.nononsenseapps.notepad.database.Notification not) {
		if (not != null) {
			cancelNotification(context, not.getUri());
		}
	}

	/**
	 * Does not touch db
	 */
	public static void cancelNotification(final Context context, final Uri uri) {
		if (uri != null)
			cancelNotification(context, Integer.parseInt(uri.getLastPathSegment()));
	}

	/**
	 * Does not touch db
	 */
	public static void cancelNotification(final Context context, final int notId) {
		final NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(notId);
	}

	// Modifies DB
	// public static void deleteNotification(final Context context, long listId,
	// long maxTime) {
	// com.nononsenseapps.notepad.database.Notification.removeWithListId(
	// context, listId, maxTime);
	//
	// final NotificationManager notificationManager = (NotificationManager)
	// context
	// .getSystemService(Context.NOTIFICATION_SERVICE);
	// notificationManager.cancel((int) listId);
	// }

	/**
	 * Given a list of notifications, returns a list of the lists the notes
	 * belong to.
	 */
	private static Collection<Long> getRelatedLists(
			final List<com.nononsenseapps.notepad.database.Notification> notifications) {
		final HashSet<Long> lists = new HashSet<Long>();
		for (com.nononsenseapps.notepad.database.Notification not : notifications) {
			lists.add(not.listID);
		}

		return lists;
	}

	/**
	 * Returns a list of those notifications that are associated to notes in the
	 * specified list.
	 */
	private static List<com.nononsenseapps.notepad.database.Notification> getSubList(
			final long listId,
			final List<com.nononsenseapps.notepad.database.Notification> notifications) {
		final ArrayList<com.nononsenseapps.notepad.database.Notification> subList = new ArrayList<>();
		for (com.nononsenseapps.notepad.database.Notification not : notifications) {
			if (not.listID == listId) {
				subList.add(not);
			}
		}

		return subList;
	}

	private static class ContextObserver extends ContentObserver {
		private final Context context;

		public ContextObserver(final Context context, Handler h) {
			super(h);
			this.context = context.getApplicationContext();
		}

		// Implement the onChange(boolean) method to delegate the
		// change notification to the onChange(boolean, Uri) method
		// to ensure correct operation on older versions
		// of the framework that did not have the onChange(boolean,
		// Uri) method.
		@Override
		public void onChange(boolean selfChange) {
			onChange(selfChange, null);
		}

		// Implement the onChange(boolean, Uri) method to take
		// advantage of the new Uri argument.
		@Override
		public void onChange(boolean selfChange, Uri uri) {
			// Handle change but don't spam
			notifyPast(context, true);
		}
	}
}
