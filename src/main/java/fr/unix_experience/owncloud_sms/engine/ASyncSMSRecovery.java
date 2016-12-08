package fr.unix_experience.owncloud_sms.engine;

import android.accounts.Account;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import fr.unix_experience.owncloud_sms.activities.remote_account.RestoreMessagesActivity;
import fr.unix_experience.owncloud_sms.enums.MailboxID;
import fr.unix_experience.owncloud_sms.providers.SmsDataProvider;

/*
 *  Copyright (c) 2014-2016, Loic Blot <loic.blot@unix-experience.fr>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public interface ASyncSMSRecovery {
	class SMSRecoveryTask extends AsyncTask<Void, Integer, Void> {
		private final RestoreMessagesActivity _context;
		private final Account _account;

		public SMSRecoveryTask(RestoreMessagesActivity context, Account account) {
			_context = context;
			_account = account;
		}

		@Override
		protected Void doInBackground(Void... params) {
			// This feature is only available for Android 4.4 and greater
			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
				return null;
			}
			Log.i(ASyncSMSRecovery.TAG, "Starting background recovery");

			// Verify connectivity

			Long start = (long) 0;

			OCSMSOwnCloudClient client = new OCSMSOwnCloudClient(_context, _account);
			SmsDataProvider smsDataProvider = new SmsDataProvider(_context);
			JSONObject obj = client.retrieveSomeMessages(start, 500);
			if (obj == null) {
				Log.i(ASyncSMSRecovery.TAG, "Retrieved returns failure");
				return null;
			}

			Integer nb = 0;
			try {
				while (obj.getLong("last_id") != start) {
					JSONObject messages = obj.getJSONObject("messages");
					Iterator<?> keys = messages.keys();
					while(keys.hasNext()) {
						String key = (String)keys.next();
						if (messages.get(key) instanceof JSONObject) {
							JSONObject msg = messages.getJSONObject(key);

							int mbid = msg.getInt("mailbox");
							// Ignore invalid mailbox
							if (mbid > MailboxID.ALL.getId()) {
								continue;
							}

							String address = msg.getString("address");

							ContentValues values = new ContentValues();
							values.put(Telephony.Sms.ADDRESS, address);
							values.put(Telephony.Sms.BODY, msg.getString("msg"));
							values.put(Telephony.Sms.DATE, key);
							values.put(Telephony.Sms.TYPE, msg.getInt("type"));
							values.put(Telephony.Sms.SEEN, 1);
							values.put(Telephony.Sms.READ, 1);

							MailboxID mailbox_id = MailboxID.fromInt(mbid);
							// @TODO verify message exists before inserting it
							_context.getContentResolver().insert(Uri.parse(mailbox_id.getURI()), values);

							nb++;
							if ((nb % 100) == 0) {
								publishProgress(nb);
							}
						}
					}

					start = obj.getLong("last_id");
					obj = client.retrieveSomeMessages(start, 500);
				}
			} catch (JSONException e) {
				Log.e(ASyncSMSRecovery.TAG, "Missing last_id field!");
			}

			// Force this refresh to fix dates
			_context.getContentResolver().delete(Uri.parse("content://sms/conversations/-1"), null, null);

			publishProgress(nb);

			Log.i(ASyncSMSRecovery.TAG, "Finishing background recovery");
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			_context.onProgressUpdate(values[0]);
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			super.onPostExecute(aVoid);
			_context.onRestoreDone();
		}
	}

	String TAG = ASyncSMSRecovery.class.getSimpleName();
}
