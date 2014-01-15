package com.twinone.adblock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import twinone.lib.androidtools.shell.Command;
import twinone.lib.androidtools.shell.Shell;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {

	public static final String TAG = "MainActivity";

	public static final String DOWNLOAD_URL = "http://winhelp2002.mvps.org/hosts.txt";

	public static final String LOCAL_DIR = "tmp";
	public static final String LOCAL_FILE = "hosts";
	public static final String HOSTS_FILE = "/etc/hosts";

	TextView mTextViewInfo;
	Button mButtonToggle;

	Shell mShell;

	private Command MOUNT_RW = new Command("mount -o rw,remount /system");
	private Command MOUNT_RO = new Command("mount -o ro,remount /system");

	public MainActivity() {
		mShell = new Shell();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mTextViewInfo = (TextView) findViewById(R.id.tvInfo);
		mButtonToggle = (Button) findViewById(R.id.bToggle);

		mButtonToggle.setOnClickListener(this);
		mButtonToggle.setEnabled(false);

		// we need root to access /etc/hosts
		if (!mShell.getRoot().isRootShell()) {
			mTextViewInfo.setText("It seems like this device is not rooted.");
			mButtonToggle.setVisibility(View.GONE);
		}
		mTextViewInfo.setText("Checking if ads are blocked...");
		if (isBlocked()) {
			mTextViewInfo.setText("Ads are currently blocked");
			mButtonToggle.setText("Unblock ads");
		} else {
			mTextViewInfo.setText("Ads are currently not blocked");
			mButtonToggle.setText("Block ads");
		}

		mButtonToggle.setEnabled(true);
	}

	private boolean isBlocked() {
		Command grep = mShell.execute("cat '" + HOSTS_FILE
				+ "'| grep '### END AD BLOCK ###'");
		// if exitStatus == 0, grep matched, so ads are blocked
		return grep.exitStatus == 0;
	}

	private void block() {
		Log.d(TAG, "blocking ads...");
		AdBlockAsyncTask task = new AdBlockAsyncTask();
		task.execute(DOWNLOAD_URL);
		mTextViewInfo.setText("Downloading hosts file...");
	}

	// Downloads the blocking file from internet and returns a string
	class AdBlockAsyncTask extends AsyncTask<String, Void, String> {
		
		boolean success;

		@Override
		protected String doInBackground(String... params) {
			String url = params[0];
			String localFile = getLocalFileName();

			success = downloadHostsFile(url, localFile);
			return localFile;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if (!success) {
				mTextViewInfo.setText("Error downloading hosts file, check internet");
				return;
			}
			String block = "cat '" + result + "' > '" + HOSTS_FILE + "'";
			String endLine = "echo '### END AD BLOCK ###' >> '" + HOSTS_FILE + "'";
			mShell.execute(MOUNT_RW);
			mShell.execute(block);
			mShell.execute(endLine);
			mShell.execute(MOUNT_RO);

			mTextViewInfo.setText("Ads are now blocked");
			mButtonToggle.setText("Unblock");
		}

	}

	private boolean downloadHostsFile(String urlString, String destination) {
		InputStream input = null;
		OutputStream output = null;
		HttpURLConnection connection = null;
		try {
			URL url = new URL(urlString);
			connection = (HttpURLConnection) url.openConnection();
			connection.connect();

			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				return false;
			}

			input = connection.getInputStream();
			output = new FileOutputStream(destination);

			byte data[] = new byte[4096];
			int count;
			while ((count = input.read(data)) != -1) {
				output.write(data, 0, count);
			}
			output.close();
			input.close();
			connection.disconnect();
		} catch (Exception e) {
			Log.w(TAG, "Error downloading file!", e);
			return false;
		}
		return true;
	}

	private void unblock() {
		Log.d(TAG, "unblocking ads...");
		Command unblock = new Command("echo '127.0.0.1 localhost' > '"
				+ HOSTS_FILE + "'");
		mShell.execute(MOUNT_RW, unblock, MOUNT_RO);
		mTextViewInfo.setText("Ads are now unblocked");
		mButtonToggle.setText("Block");
	}

	private String getLocalFileName() {
		File tmpDir = getDir(LOCAL_DIR, Context.MODE_PRIVATE);
		File localFile = new File(tmpDir, LOCAL_FILE);
		Log.d(TAG, "file: " + localFile.getAbsolutePath());
		return localFile.getAbsolutePath();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.bToggle:
			if (!isBlocked()) {
				block();
			} else {
				unblock();
			}
			break;
		}
	}

}
