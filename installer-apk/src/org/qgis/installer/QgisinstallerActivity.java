/**
 * @author  Marco Bernasocchi - <marco@bernawebdesign.ch>
 * @version 0.3
 */
/*
 Copyright (c) 2011, Marco Bernasocchi <marco@bernawebdesign.ch>
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.
 * Neither the name of the  Marco Bernasocchi <marco@bernawebdesign.ch> nor the
 names of its contributors may be used to endorse or promote products
 derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY Marco Bernasocchi <marco@bernawebdesign.ch> ''AS IS'' AND ANY
 EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL Marco Bernasocchi <marco@bernawebdesign.ch> BE LIABLE FOR ANY
 DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.qgis.installer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class QgisinstallerActivity extends Activity {
	private static final int DOWNLOAD_DIALOG = 0;
	private static final int PROMPT_INSTALL_DIALOG = 1;
	private static final int NO_CONNECIVITY_DIALOG = 2;
	private static final int ABOUT_DIALOG = 3;
	private static final int BYTE_TO_MEGABYTE = 1024 * 1024;

	protected DownloadApkTask mDownloadApkTask;
	protected DownloadVersionInfoTask mDownloadVersionInfoTask;
	protected ProgressDialog mProgressDialog;

	protected int mSize;
	protected String mMD5;
	protected String mVersion;
	protected String mVersionName;
	protected String mABI;
	protected String mApkFileName;
	protected String mApkUrl;
	protected String mFilePath;
	private String mFilePathBase;
	private String mLastMethod;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		final Button aboutButton = (Button) findViewById(R.id.aboutButton);
		aboutButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				about();
			}
		});

		final Button donateButton = (Button) findViewById(R.id.donateButton);
		donateButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				donate();
			}
		});

		final Button quitButton = (Button) findViewById(R.id.quitButton);
		quitButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});

		final Button installButton = (Button) findViewById(R.id.installButton);
		installButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				run();
			}
		});
	}

	private void run() {
		if (isOnline("run")) {
			initVars();
			mDownloadVersionInfoTask = null;
			mDownloadVersionInfoTask = new DownloadVersionInfoTask();
			mDownloadVersionInfoTask.execute();
		}
	}

	private void donate() {
		if (isOnline("donate")) {
			String url = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=27GAYFKF4U5EE";
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
		}
	}

	private void about() {
		showDialog(ABOUT_DIALOG);
	}

	private void visitOpenGis() {
		if (isOnline("visitOpenGis")) {
			String url = "http://www.opengis.ch/android-gis/";
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
		}
	}

	private void retryLastMethod() {
		// use reflection to recall the last method
		java.lang.reflect.Method method = null;
		try {
			method = this.getClass().getDeclaredMethod(mLastMethod);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		try {
			method.invoke(this);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	private boolean isOnline(String callerMethod) {
		mLastMethod = callerMethod;
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			return true;
		}
		showDialog(NO_CONNECIVITY_DIALOG);
		return false;
	}

	private void initVars() {
		mVersion = getVersion()[0];
		mVersionName = getVersion()[1];
		mABI = "armeabi"; // TODO: use android.os.Build.CPU_ABI;

		mApkFileName = "qgis-" + mVersion + "-" + mABI + ".apk";
		mApkUrl = "http://android.qgis.org/download/apk/" + mApkFileName;
		mFilePathBase = getExternalFilesDir(null) + "/downloaded_apk/";
		mFilePath = mFilePathBase + mApkFileName;
		new File(mFilePathBase).mkdir();
		Log.i("QGIS Downloader", "Downloading to " + mFilePath);
	}

	private String[] getVersion() {
		String[] v = new String[2];
		try {
			v[0] = Integer.toString(getPackageManager().getPackageInfo(
					getPackageName(), 0).versionCode);
			v[1] = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return v;
	}

	public void onDestroy() {
		super.onDestroy();
		mDownloadVersionInfoTask.cancel(true);
		mDownloadApkTask.cancel(true);
	}

	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DOWNLOAD_DIALOG:
			mProgressDialog = new ProgressDialog(QgisinstallerActivity.this);
			mProgressDialog
					.setMessage(getString(R.string.downloading_dialog_message)
							+ ": " + mApkUrl);
			// add cancel button
			mProgressDialog.setCancelable(true);
			mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
					getString(android.R.string.cancel),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
			// cancel download task oncancel (back button and cancel button)
			mProgressDialog
					.setOnCancelListener(new DialogInterface.OnCancelListener() {
						public void onCancel(DialogInterface dialog) {
							mDownloadApkTask.cancel(true);
							// finish();
						}
					});

			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setMax(mSize / BYTE_TO_MEGABYTE);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			return mProgressDialog;

		case PROMPT_INSTALL_DIALOG:
			return new AlertDialog.Builder(QgisinstallerActivity.this)
					.setTitle(getString(R.string.install_dialog_title))
					.setMessage(
							String.format(
									getString(R.string.install_dialog_message),
									mVersionName, mVersion,
									Math.round(mSize / BYTE_TO_MEGABYTE)))
					.setPositiveButton(getString(android.R.string.yes),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									if (mDownloadApkTask == null) {
										mDownloadApkTask = new DownloadApkTask();
									}
									mDownloadApkTask.execute(mApkUrl);
								}
							})
					.setNegativeButton(getString(R.string.quit),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									// Action for 'NO' Button
									finish();
								}
							})
					.setNeutralButton(getString(android.R.string.no),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									// Action for 'NO' Button
									dialog.cancel();
								}
							}).create();

		case NO_CONNECIVITY_DIALOG:
			return new AlertDialog.Builder(QgisinstallerActivity.this)
					.setTitle(getString(R.string.no_connectivity_dialog_title))
					.setMessage(
							getString(R.string.no_connectivity_dialog_message))
					.setPositiveButton(R.string.retry,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									removeDialog(NO_CONNECIVITY_DIALOG);
									retryLastMethod();
								}
							})
					.setNegativeButton(getString(android.R.string.cancel),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									dialog.cancel();
								}
							})
					.setNeutralButton(getString(R.string.wifi_settings),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									Intent openWirelessSettings = new Intent(
											"android.settings.WIFI_SETTINGS");
									startActivity(openWirelessSettings);
								}
							}).create();

		case ABOUT_DIALOG:
			return new AlertDialog.Builder(QgisinstallerActivity.this)
					.setTitle(getString(R.string.app_name))
					.setMessage(getString(R.string.about_dialog_message))
					.setNegativeButton(getString(android.R.string.cancel),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									dialog.cancel();
								}
							})
					.setNeutralButton(getString(R.string.visit_dev),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									visitOpenGis();
								}
							}).create();
		default:
			return null;
		}
	}

	private class DownloadVersionInfoTask extends
			AsyncTask<Void, Integer, String> {
		protected String doInBackground(Void... unused) {
			try {
				URL apkUrl = new URL(mApkUrl);
				URLConnection akpConnection = apkUrl.openConnection();
				akpConnection.connect();
				mSize = akpConnection.getContentLength();
				Log.i("QGIS Downloader", "APK is " + String.valueOf(mSize));

				URL md5Url = new URL(mApkUrl + ".md5");
				URLConnection md5Connection = md5Url.openConnection();
				md5Connection.connect();

				// download the info file
				BufferedReader in = new BufferedReader(new InputStreamReader(
						md5Url.openStream()));
				String str;
				while ((str = in.readLine()) != null) {
					mMD5 = str;
				}
				Log.i("QGIS Downloader", "APK MD5 is " + mMD5);

				in.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		protected void onPostExecute(String result) {
			showDialog(PROMPT_INSTALL_DIALOG);
		}
	}

	private class DownloadApkTask extends AsyncTask<String, Integer, String> {
		@Override
		protected String doInBackground(String... mUrlBaseString) {
			int count;
			try {
				URL url = new URL(mUrlBaseString[0]);
				URLConnection conexion = url.openConnection();
				conexion.connect();
				// this will be useful so that you can show a tipical 0-100%
				// progress bar
				int lenghtOfFile = conexion.getContentLength();

				// download the file
				InputStream input = new BufferedInputStream(url.openStream());
				OutputStream output = new FileOutputStream(mFilePath);

				byte data[] = new byte[1024];

				long total = 0;

				while (!isCancelled() && (count = input.read(data)) != -1) {
					total += count;
					// publishing the progress....
					int progress = (int) (total * 100 / lenghtOfFile);
					publishProgress(progress);
					output.write(data, 0, count);
				}

				output.flush();
				output.close();
				input.close();
			} catch (Exception e) {
			}

			// URL url = new URL(mUrlBaseString[0]);
			// HttpURLConnection connection = (HttpURLConnection) url
			// .openConnection();
			// int downloaded;
			// if (ISSUE_DOWNLOAD_STATUS.intValue() ==
			// ECMConstant.ECM_DOWNLOADING) {
			// File file = new File(mFilePathBase);
			// if (file.exists()) {
			// downloaded = (int) file.length();
			// connection.setRequestProperty("Range",
			// "bytes=" + (file.length()) + "-");
			// }
			// } else {
			// connection.setRequestProperty("Range", "bytes=" + downloaded
			// + "-");
			// }
			// connection.setDoInput(true);
			// connection.setDoOutput(true);
			// //progressBar.setMax(connection.getContentLength());
			// BufferedInputStream in = new
			// BufferedInputStream(connection.getInputStream());
			// FileOutputStream fos = (downloaded == 0) ? new
			// FileOutputStream(mFilePathBase)
			// : new FileOutputStream(mFilePathBase, true);
			// BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
			// byte[] data = new byte[1024];
			// int x = 0;
			// while ((x = in.read(data, 0, 1024)) >= 0) {
			// bout.write(data, 0, x);
			// downloaded += x;
			// //progressBar.setProgress(downloaded);
			// }

			return null;
		}

		protected void onPreExecute() {
			showDialog(DOWNLOAD_DIALOG);
		}

		protected void onProgressUpdate(Integer... progress) {
			super.onProgressUpdate(progress);
			mProgressDialog.setProgress(progress[0]);
		}

		protected void onPostExecute(String result) {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(new File(mFilePath)),
					"application/vnd.android.package-archive");
			startActivity(intent);
			QgisinstallerActivity.this.finish();
		}
	}
}
