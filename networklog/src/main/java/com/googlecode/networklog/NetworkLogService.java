/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Messenger;
import android.os.Message;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.RemoteException;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import java.util.HashMap;
import java.util.ArrayList;
import java.io.File;
import java.io.PrintWriter;
import java.lang.Thread;
import java.lang.Runnable;

public class NetworkLogService extends Service {
	private static final String ACTION_SERVICE_STOP = "com.service.action.SERVICE_STOP";
	private static final int MSG_SHOW = 67;
	private static final int MSG_REMOVE = 68;
	public static final String ACTION_TOAST_CREATE = "com.service.action.TOAST_CREATE";
	ArrayList<Messenger> clients = new ArrayList<Messenger>();
	static final int NOTIFICATION_ID = "Network Log".hashCode();
	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	static final int MSG_UPDATE_NOTIFICATION = 3;
	static final int MSG_BROADCAST_LOG_ENTRY = 4;
	static final int MSG_TOGGLE_FOREGROUND = 5;
	final Messenger messenger = new Messenger(new IncomingHandler(this));
	boolean has_root = false;
	boolean has_binaries = false;
	public static NetworkLogService instance = null;
	private static Context context;
	public static Handler handler;
	public static String logfileString = "";
	public static Toast toast;
	public static TextView toastTextView;
	public static CharSequence toastText;
	public static boolean toastEnabled;
	public static int toastDuration;
	public static int toastPosition;
	public static int toastDefaultYOffset;
	public static int toastYOffset;
	public static int toastOpacity;
	public static boolean toastShowAddress;
	public static HashMap<String, String> blockedApps;
	public static HashMap<String, String> toastBlockedApps;
	public static boolean invertUploadDownload;
	public static boolean behindFirewall;
	public static boolean watchRules;
	public static int watchRulesTimeout;
	public static boolean throughputBps;
	private static WindowManager toastWindow;
	private static WindowManager.LayoutParams windowLayoutParams;
	private final static int MSG_HIDDEN = 66;
	private static boolean isWindowShow = false;
	public static boolean isWindowMoving = false;
	private static int windowWidth = 0;
	public static int windowHeight = 0;
//	private static WindowToastDrawable windowToastDrawable = new WindowToastDrawable();

	private static Handler handlerToast = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
				case MSG_HIDDEN:
					isWindowShow = false;
					hiddenWindow();
					break;
				case MSG_SHOW:
					showWindow();
					isWindowShow = true;
					break;
				case MSG_REMOVE:
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
						if (toastLayout.isAttachedToWindow()) {
							toastWindow.removeViewImmediate(toastLayout);
						}
					}
			}
		}
	};

	public static void showToastOpt(Context context, Handler handler, String msg, boolean b) {
//		if (toastWindow != null) {
//			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//				toastLayout.setBackground(windowToastDrawable);
//			}else {
//				toastLayout.setBackgroundDrawable(windowToastDrawable);
//			}
//			toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
		if (toastLayout != null) {
			if (drawable != null) {
				drawable.setAlpha(toastOpacity);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					toastLayout.setBackground(drawable);
				} else {
					toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
				}
			} else {
				toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
			}
		}
		showToast(context, handler, msg, b);
//		}
	}

	private class IncomingHandler extends Handler {
		private Context context;

		public IncomingHandler(Context context) {
			this.context = context;
		}

		@Override
		public void handleMessage(Message msg) {
			MyLog.d("[service] got message: " + msg);

			switch (msg.what) {
				case MSG_REGISTER_CLIENT:
					MyLog.d("[service] registering client " + msg.replyTo);
					clients.add(msg.replyTo);
					break;

				case MSG_UNREGISTER_CLIENT:
					MyLog.d("[service] unregistering client " + msg.replyTo);
					clients.remove(msg.replyTo);
					break;

				case MSG_UPDATE_NOTIFICATION:
					if (MyLog.enabled) {
						MyLog.d("[service] updating notification: " + ((String) msg.obj));
					}
					updateNotification((String) msg.obj);
					break;

				case MSG_TOGGLE_FOREGROUND:
					MyLog.d("[service] toggling service foreground state: " + ((Boolean) msg.obj));
					start_foreground = (Boolean) msg.obj;

					if (start_foreground) {
						startForeground(notification);
					} else {
						stopForeground();
					}
					break;

				case MSG_BROADCAST_LOG_ENTRY:
					MyLog.d("[service] got MSG_BROADCOAST_LOG_ENTRY unexpectedly");
					break;

				default:
					MyLog.d("[service] unhandled message");
					super.handleMessage(msg);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		MyLog.d("[service] onBind");
		if (!has_root || !has_binaries) {
			return null;
		} else {
			return messenger.getBinder();
		}
	}

	private static HashMap<String, Integer> logEntriesMap = new HashMap<String, Integer>();
	private InteractiveShell loggerShell;
	private NetworkLogger logger;
	private static String logfile = null;
	private PrintWriter logWriter = null;
	private static NotificationManager nManager;
	private static Notification notification;
	private static int notificationIcon;
	private static LogEntry entry;
	private static Boolean start_foreground = true;
	private NetStat netstat = new NetStat();
	private FastParser parser = new FastParser();
	static Drawable drawable = null;
	public static float pointX = 0;

	public void startForeground(Notification n) {
		startForeground(NOTIFICATION_ID, n);
	}

	public void stopForeground() {
		stopForeground(true);
	}

	public Notification createNotification() {
		notificationIcon = R.drawable.up0_down0;
		Notification n = null;
		Intent intent = new Intent(this, NetworkLog.class);

		PendingIntent pendingIntent = (PendingIntent) PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Notification.Builder notificatoinBuilder;
		Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.up0_down0);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			notificatoinBuilder = new Notification.Builder(getApplicationContext());
			notificatoinBuilder.setContentTitle("网络日志")
					.setContentText("网络日志运行中")
					.setLargeIcon(bitmap)
					.setAutoCancel(true)
					.setContentIntent(pendingIntent)
					.setSmallIcon(R.drawable.up0_down0);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				notificatoinBuilder.setCategory(Notification.CATEGORY_TRANSPORT);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				notificatoinBuilder.addAction(R.drawable.loading_icon, "停止", PendingIntent.getService(this, 233, new Intent(NetworkLogService.this, NetworkLogService.class), PendingIntent.FLAG_UPDATE_CURRENT));
				notificatoinBuilder.setPriority(Notification.PRIORITY_DEFAULT);
				notificatoinBuilder.setOngoing(true);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				notificatoinBuilder.setChannelId("networklog");
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				n = notificatoinBuilder.build();
			} else {
				n = notificatoinBuilder.getNotification();
			}
		} else {
			n = new Notification(notificationIcon, getString(R.string.logging_started), System.currentTimeMillis());
		}
		Intent i = new Intent(this, NetworkLog.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
		return n;
	}

	static Runnable updateNotificationRunner = new Runnable() {
		public void run() {
			updateNotification();
		}
	};

	public static void updateNotification(int icon) {
		if (instance != null && handler != null) {
			notificationIcon = icon;
			handler.postDelayed(updateNotificationRunner, 1000);
		}
	}

	public static void updateNotification() {
		if (logfileString.length() > 0) {
			updateNotification(ThroughputTracker.throughputString + " [" + logfileString + "]");
		} else {
			updateNotification(ThroughputTracker.throughputString);
		}
	}

	public static void updateNotification(String text) {
		if (instance == null || context == null || notification == null) {
			return;
		}
		Intent intent = new Intent(context, NetworkLog.class);

		PendingIntent pendingIntent = (PendingIntent) PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Notification.Builder notificatoinBuilder = null;
		Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), notificationIcon);
		Intent i = new Intent(context, NetworkLog.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);
//      notification.setLatestEventInfo(context, context.getResources().getString(R.string.app_name), text, pi);
		notification.icon = notificationIcon;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			notificatoinBuilder = new Notification.Builder(context);
			notificatoinBuilder.setContentTitle("网络日志")
					.setContentText(text)
					.setLargeIcon(bitmap)
					.setAutoCancel(true)
					.setContentIntent(pendingIntent)
					.setSmallIcon(R.drawable.up0_down0);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				notificatoinBuilder.setCategory(Notification.CATEGORY_TRANSPORT);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				notificatoinBuilder.addAction(R.drawable.loading_icon, "停止", PendingIntent.getService(context, 233, new Intent(context, NetworkLogService.class), PendingIntent.FLAG_UPDATE_CURRENT));
				notificatoinBuilder.setPriority(Notification.PRIORITY_DEFAULT);
				notificatoinBuilder.setOngoing(true);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				notificatoinBuilder.setChannelId("networklog");
			}
		}

		if (start_foreground) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				nManager.notify(NOTIFICATION_ID, notificatoinBuilder.getNotification());
			} else {
				nManager.notify(NOTIFICATION_ID, notification);

			}
		}
	}

	private static abstract class CancelableRunnable implements Runnable {
		public boolean cancel;
	}

	private static Runnable showOnlyToastRunnable;
	private static CancelableRunnable showToastRunnable;
	private static View toastLayout;

	public static void showToast(final Context context, final Handler handler, final CharSequence text, boolean cancel) {
		if (showToastRunnable == null) {
			showToastRunnable = new CancelableRunnable() {
				public void run() {
//
//					if (cancel) {
//						if (toastLayout == null) {
//							toastLayout = LayoutInflater.from(context).inflate(R.layout.custom_toast, null);
//							toastTextView = (TextView) toastLayout.findViewById(R.id.toasttext);
//						}
//						if (Build.VERSION.SDK_INT > 10 || toast == null) {
//							toast = new Toast(context);
//						}
//						switch (toastDuration) {
//							case 3500:
//								toast.setDuration(Toast.LENGTH_SHORT);
//								break;
//							case 7000:
//								toast.setDuration(Toast.LENGTH_SHORT);
//								break;
//							default:
//								toast.setDuration(Toast.LENGTH_SHORT);
//						}

//						toastDefaultYOffset = toast.getYOffset();
//						GradientDrawable background = (GradientDrawable) toastLayout.getBackground();
//						if (drawable != null) {
//							drawable.setAlpha(toastOpacity << 24);
//						} else {
//							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//								drawable = context.getDrawable(R.drawable.toast_bg);
//							} else {
//								toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
//							}
//						}
//						toast.setView(toastLayout);
//						toast.show();
//
//						return;
//					}


//          switch(toastPosition) {
//            case 1:
//              toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, toastYOffset);
//              break;
//            case 2:
//              toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, toastYOffset);
//              break;
//            default:
//              toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, toastDefaultYOffset);
//              break;
//          }
//					if (!isWindowMoving){
					if (toastTextView != null) {
						toastTextView.setText(Html.fromHtml(toastText.toString()));
					}
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
						if (!isWindowShow) {
							handlerToast.removeMessages(MSG_SHOW);
							handlerToast.sendEmptyMessage(MSG_SHOW);
						}
						handlerToast.removeMessages(MSG_HIDDEN);
						handlerToast.sendEmptyMessageDelayed(MSG_HIDDEN, toastDuration);
					}
//					}

//          toast.show();
				}
			};
		}

		showToastRunnable.cancel = cancel;
		toastText = text;
		handler.postDelayed(showToastRunnable, 1000);
	}

	public static void showToast(final CharSequence text) {
		if (context == null || handler == null || !toastEnabled) {
			return;
		}
		showToast(context, handler, text, false);
	}

	public boolean hasRoot() {
		return SysUtils.checkRoot(this);
	}

	class ToastChangeBrocast extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null && intent.getAction().equals(ACTION_TOAST_CREATE)) {
				if (toastEnabled && toastWindow == null) {
					toastWindow = (WindowManager) getSystemService(WINDOW_SERVICE);

					windowLayoutParams = new WindowManager.LayoutParams();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
					} else {
						windowLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
					}
					windowLayoutParams.format = PixelFormat.RGBA_8888;
					windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

					windowLayoutParams.gravity = Gravity.CENTER | Gravity.BOTTOM;
					windowLayoutParams.x = 0;
					windowLayoutParams.y = toastYOffset + 100;
//      windowLayoutParams.alpha=toastOpacity;
					windowLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
					windowLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
					if (toastLayout == null) {
						toastLayout = LayoutInflater.from(context).inflate(R.layout.custom_toast, null);
					}
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
						toastLayout.setAlpha(0f);
					}
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						drawable = getDrawable(R.drawable.toast_bg);
						if (drawable != null) {
							drawable.setAlpha(toastOpacity);
						}
					}
					if (drawable != null) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
							toastLayout.setBackground(drawable);
						} else {
							toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
						}
					} else {
						toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
					}
					toastTextView = (TextView) toastLayout.findViewById(R.id.toasttext);
					toastWindow.addView(toastLayout, windowLayoutParams);
					Log.d("TAG", "onReceive: createToastWindow");


				}
			}
		}
	}

	ToastChangeBrocast toastChangeBrocast = new ToastChangeBrocast();
	private static int firstPoint;
	private static boolean change = false;
	private int windowW = 0;

	@Override
	public void onCreate() {
		MyLog.d("[service] onCreate");

		if (NetworkLog.shell == null) {
			NetworkLog.shell = SysUtils.createRootShell(this, "NLServiceRootShell", true);
		}

		if (!hasRoot()) {
			SysUtils.showError(this, getString(R.string.error_default_title), getString(R.string.error_noroot));
			has_root = false;
			stopSelf();
			return;
		} else {
			has_root = true;
		}

		if (!SysUtils.installBinaries(this)) {
			has_binaries = false;
			stopSelf();
			return;
		} else {
			has_binaries = true;
		}

		if (instance != null) {
			Log.w("NetworkLog", "[service] Last instance destroyed unexpectedly");
		}

		instance = this;
		handler = new Handler();

		if (ApplicationsTracker.installedApps == null) {
			ApplicationsTracker.getInstalledApps(this, null);
		}

		if (NetworkLog.settings == null) {
			NetworkLog.settings = new Settings(this);
		}

		toastEnabled = NetworkLog.settings.getToastNotifications();
		toastDuration = NetworkLog.settings.getToastNotificationsDuration();
		toastPosition = NetworkLog.settings.getToastNotificationsPosition();
		toastYOffset = NetworkLog.settings.getToastNotificationsYOffset();
		toastOpacity = NetworkLog.settings.getToastNotificationsOpacity();
		toastShowAddress = NetworkLog.settings.getToastNotificationsShowAddress();
		toastBlockedApps = new SelectToastApps().loadBlockedApps(this);
		blockedApps = new SelectBlockedApps().loadBlockedApps(this);
		invertUploadDownload = NetworkLog.settings.getInvertUploadDownload();
		behindFirewall = NetworkLog.settings.getBehindFirewall();
		watchRules = NetworkLog.settings.getWatchRules();
		watchRulesTimeout = NetworkLog.settings.getWatchRulesTimeout();
		throughputBps = NetworkLog.settings.getThroughputBps();

		windowW = getResources().getDisplayMetrics().widthPixels;
		updateLogfileString();
		ThroughputTracker.startUpdater();

		nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel notificationChannel = new NotificationChannel("networklog", "网络日志", NotificationManager.IMPORTANCE_LOW);
			nManager.createNotificationChannel(notificationChannel);
		}
		notification = createNotification();
		start_foreground = NetworkLog.settings.getStartForeground();
//		nManager.notify(NOTIFICATION_ID,notification);

		if (start_foreground) {
			startForeground(notification);
		}

		IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(ACTION_TOAST_CREATE);
		registerReceiver(toastChangeBrocast, intentFilter);
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		int windowsx = preferences.getInt("windowx", 0);
		int windowsy = preferences.getInt("windowx", 0);
		if (toastEnabled) {
			toastWindow = (WindowManager) getSystemService(WINDOW_SERVICE);
			windowLayoutParams = new WindowManager.LayoutParams();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
			} else {
				windowLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
			}
			windowLayoutParams.format = PixelFormat.RGBA_8888;
//			windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
			windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
			windowLayoutParams.gravity = Gravity.START | Gravity.TOP;
			windowLayoutParams.x = windowsx;
			windowLayoutParams.y = windowsy;
			windowLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
			windowLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
			if (toastLayout == null) {
				toastLayout = LayoutInflater.from(this).inflate(R.layout.custom_toast, null);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					toastLayout.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
				}

			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				toastLayout.setAlpha(0f);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				drawable = getDrawable(R.drawable.toast_bg);
				if (drawable != null) {
					drawable.setAlpha(toastOpacity);
				}
			}
			if (drawable != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					toastLayout.setBackground(drawable);
				} else {
					toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
				}
			} else {
				toastLayout.setBackgroundColor(Color.argb(toastOpacity, 0, 0, 0));
			}
			toastTextView = (TextView) toastLayout.findViewById(R.id.toasttext);
			toastWindow.addView(toastLayout, windowLayoutParams);


			toastLayout.setOnTouchListener(new View.OnTouchListener() {
				private float mStartX;
				private float mStartY;
				private float mRawY;
				private float mRawX;
				private float startRawY;


				@Override
				public boolean onTouch(View view, MotionEvent event) {
					mRawX = event.getRawX();
					mRawY = event.getRawY();

					switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN:
							mStartX = event.getX();
							mStartY = event.getY();
							startRawY = event.getRawY();
							isWindowMoving = true;
							firstPoint = windowLayoutParams.y;
//							handlerToast.removeMessages(MSG_HIDDEN);
							break;
						case MotionEvent.ACTION_MOVE:
							if ((windowW - windowLayoutParams.x) < (windowLayoutParams.width + 50)) {
								windowLayoutParams.x = windowW;
								windowLayoutParams.y = (int) (firstPoint + (mRawY - startRawY));
								toastWindow.updateViewLayout(toastLayout, windowLayoutParams);
								break;
							}
							if (windowLayoutParams.x < 50) {
								windowLayoutParams.x = 0;
								windowLayoutParams.y = (int) (firstPoint + (mRawY - startRawY));
								toastWindow.updateViewLayout(toastLayout, windowLayoutParams);
							}
							windowLayoutParams.x = (int) (mRawX - mStartX);
							windowLayoutParams.y = (int) (firstPoint + (mRawY - startRawY));
							toastWindow.updateViewLayout(toastLayout, windowLayoutParams);

							break;
						case MotionEvent.ACTION_UP:
							isWindowMoving = false;
//							handlerToast.sendEmptyMessageDelayed(MSG_HIDDEN,3000);
							preferences.edit()
									.putInt("windowx", (int) (mRawX - mStartX))
									.putInt("windowy", (int) (mRawY - mStartY))
									.commit();
							break;
					}


					return false;
				}
			});
		}
	}


	private static void hiddenWindow() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			ViewPropertyAnimator viewPropertyAnimator = toastLayout.animate().alpha(0f).setDuration(300);
			viewPropertyAnimator.setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					super.onAnimationEnd(animation);
					toastText = "";

				}
			});
			viewPropertyAnimator.start();
		}

	}

	private static void showWindow() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			toastLayout.animate().alpha(1f).setDuration(300).start();
		}

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		MyLog.d("[service] onStartCommand");

		if (!has_root || !has_binaries) {
			return Service.START_NOT_STICKY;
		}


		Bundle ext = null;

		if (intent == null) {
			MyLog.d("[service] Service null intent");
		} else if (intent.getAction() != null && intent.getAction().equals(ACTION_SERVICE_STOP)) {
			stopSelf();
			return Service.START_STICKY;
		} else {
			ext = intent.getExtras();
		}

		final Bundle extras = ext;
		context = this;

		// run in background thread
		new Thread(new Runnable() {
			public void run() {
				String logfile_from_intent = null;

				if (extras != null) {
					logfile_from_intent = extras.getString("logfile");
					MyLog.d("[service] set logfile: " + logfile_from_intent);
				}

				if (logfile_from_intent == null) {
					logfile_from_intent = NetworkLog.settings.getLogFile();
				}

				MyLog.d("[service] NetworkLog service starting [" + logfile_from_intent + "]");
				;

				logfile = logfile_from_intent;
				initEntriesMap();

				if (!startLogging()) {
					MyLog.d("[service] start logging error, aborting");
					handler.post(new Runnable() {
						public void run() {
							stopSelf();
						}
					});
				}
			}
		}).start();

		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		MyLog.d("[service] onDestroy");

		stopForeground();
		unregisterReceiver(toastChangeBrocast);
		if (toastLayout != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				if (toastLayout.isAttachedToWindow()) {
					toastWindow.removeViewImmediate(toastLayout);
				}
			} else {
				toastWindow.removeViewImmediate(toastLayout);
			}
		}

		ThroughputTracker.stopUpdater();

		if (NetworkLog.loggingButton != null) {
			NetworkLog.loggingButton.setChecked(false);
		}

		if (has_root && has_binaries) {
			stopLogging();
			Toast.makeText(this, getString(R.string.logging_stopped), Toast.LENGTH_SHORT).show();
		}

		instance = null;
		context = null;
		handler = null;
	}

	public static NetworkLogService getInstance() {
		return instance;
	}

	public void initEntriesMap() {
		ArrayList<NetStat.Connection> connections = netstat.getConnections();

		for (NetStat.Connection connection : connections) {
			String mapKey = connection.src + ":" + connection.spt + " -> " + connection.dst + ":" + connection.dpt;

			if (MyLog.enabled && MyLog.level >= 5) {
				MyLog.d(5, "[netstat src-dst] New entry " + connection.uid + " for [" + mapKey + "]");
			}

			logEntriesMap.put(mapKey, Integer.valueOf(connection.uid));

			mapKey = connection.dst + ":" + connection.dpt + " -> " + connection.src + ":" + connection.spt;

			if (MyLog.enabled && MyLog.level >= 5) {
				MyLog.d(5, "[netstat dst-src] New entry " + connection.uid + " for [" + mapKey + "]");
			}

			logEntriesMap.put(mapKey, Integer.valueOf(connection.uid));
		}
	}

	public void parseResult(String result) {
		if (MyLog.enabled && MyLog.level >= 10) {
			MyLog.d(10, "--------------- parsing network entry --------------");
		}
		int pos = 0, lastpos, thisEntry, nextEntry, newline, space;
		String in, out, src, dst, proto, uidString;
		int spt, dpt, len, uid;
		parser.setLine(result.toCharArray(), result.length() - 1);


//    System.err.println(result.indexOf("{NL}", pos));
		while ((pos = result.indexOf("{NL}", pos)) > -1) {


			if (MyLog.enabled && MyLog.level >= 10) {
				MyLog.d(10, "---- got {NL} at " + pos + " ----");
			}

			pos += "{NL}".length(); // skip past "{NL}"

			thisEntry = pos;
			newline = result.indexOf("\n", pos);
			nextEntry = result.indexOf("{NL}", pos);

			if (newline == -1) {
				newline = result.length();
			}

			if (nextEntry != -1 && nextEntry < newline) {
				// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
				pos = newline;
				continue;
			}

			try {
				pos = result.indexOf("IN=", pos);

				if (pos == -1 || pos > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				space = result.indexOf(" ", pos);

				if (space == -1 || space > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				parser.setPos(pos + 3);
				in = parser.getString();

				pos = result.indexOf("OUT=", pos);

				if (pos == -1 || pos > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				space = result.indexOf(" ", pos);

				if (space == -1 || space > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				parser.setPos(pos + 4);
				out = parser.getString();

				pos = result.indexOf("SRC=", pos);

				if (pos == -1 || pos > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				space = result.indexOf(" ", pos);

				if (space == -1 || space > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				parser.setPos(pos + 4);
				src = parser.getString();

				pos = result.indexOf("DST=", pos);

				if (pos == -1 || pos > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				space = result.indexOf(" ", pos);

				if (space == -1 || space > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				parser.setPos(pos + 4);
				dst = parser.getString();

				pos = result.indexOf("LEN=", pos);

				if (pos == -1 || pos > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				space = result.indexOf(" ", pos);

				if (space == -1 || space > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				parser.setPos(pos + 4);
				len = parser.getInt();

				pos = result.indexOf("PROTO=", pos);

				if (pos == -1 || pos > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				space = result.indexOf(" ", pos);

				if (space == -1 || space > newline) {
					// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
					pos = newline;
					continue;
				}

				parser.setPos(pos + 6);
				proto = parser.getString();

				lastpos = pos;
				pos = result.indexOf("SPT=", pos);

				if (pos == -1 || pos > newline) {
					// no SPT field, probably a broadcast packet
					spt = 0;
					pos = lastpos;
				} else {
					space = result.indexOf(" ", pos);

					if (space == -1 || space > newline) {
						// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
						pos = newline;
						continue;
					}

					parser.setPos(pos + 4);
					spt = parser.getInt();
				}

				lastpos = pos;
				pos = result.indexOf("DPT=", pos);

				if (pos == -1 || pos > newline) {
					// no DPT field, probably a broadcast packet
					dpt = 0;
					pos = lastpos;
				} else {
					space = result.indexOf(" ", pos);

					if (space == -1 || space > newline) {
						// Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
						pos = newline;
						continue;
					}

					parser.setPos(pos + 4);
					dpt = parser.getInt();
				}

				lastpos = pos;
				pos = result.indexOf("UID=", pos);

				if (pos == -1 || pos > newline) {
					uid = -1;
					uidString = "-1";
					pos = lastpos;
				} else {
					parser.setPos(pos + 4);
					uid = parser.getInt();
					parser.setPos(pos + 4);
					uidString = parser.getString();
				}
			} catch (Exception e) {
				Log.e("NetworkLog", "Bad data for: [" + result.substring(thisEntry, newline) + "]", e);
				pos = newline;
				continue;
			}

			if (MyLog.enabled && MyLog.level >= 9) {
				MyLog.d(9, "Setting map key: src=[" + src + "] spt=" + spt + " dst=[" + dst + "] dpt=" + dpt);
			}

			String srcDstMapKey = src + ":" + spt + "->" + dst + ":" + dpt;
			String dstSrcMapKey = dst + ":" + dpt + "->" + src + ":" + spt;

			if (MyLog.enabled && MyLog.level >= 10) {
				MyLog.d(10, "Checking entry for " + uid + " " + srcDstMapKey + " and " + dstSrcMapKey);
			}

			Integer srcDstMapUid = logEntriesMap.get(srcDstMapKey);
			Integer dstSrcMapUid = logEntriesMap.get(dstSrcMapKey);

			if (uid < 0) {
				// Unknown uid, retrieve from entries map
				if (MyLog.enabled && MyLog.level >= 9) {
					MyLog.d(9, "Unknown uid");
				}

				if (srcDstMapUid == null || dstSrcMapUid == null) {
					// refresh netstat and try again
					if (MyLog.enabled && MyLog.level >= 9) {
						MyLog.d(9, "Refreshing netstat ...");
					}
					initEntriesMap();
					srcDstMapUid = logEntriesMap.get(srcDstMapKey);
					dstSrcMapUid = logEntriesMap.get(dstSrcMapKey);
				}

				if (srcDstMapUid == null) {
					if (MyLog.enabled && MyLog.level >= 9) {
						MyLog.d(9, "[src-dst] No entry uid for " + uid + " [" + srcDstMapKey + "]");
					}

					if (uid == -1) {
						if (dstSrcMapUid != null) {
							if (MyLog.enabled && MyLog.level >= 9) {
								MyLog.d(9, "[dst-src] Reassigning kernel packet -1 to " + dstSrcMapUid);
							}
							uid = dstSrcMapUid;
							uidString = StringPool.get(dstSrcMapUid);
						} else {
							if (MyLog.enabled && MyLog.level >= 9) {
								MyLog.d(9, "[src-dst] New kernel entry -1 for [" + srcDstMapKey + "]");
							}
							srcDstMapUid = uid;
							logEntriesMap.put(srcDstMapKey, srcDstMapUid);
						}
					} else {
						if (MyLog.enabled && MyLog.level >= 9) {
							MyLog.d(9, "[src-dst] New entry " + uid + " for [" + srcDstMapKey + "]");
						}
						srcDstMapUid = uid;
						logEntriesMap.put(srcDstMapKey, srcDstMapUid);
					}
				} else {
					if (MyLog.enabled && MyLog.level >= 9) {
						MyLog.d(9, "[src-dst] Found entry uid " + srcDstMapUid + " for " + uid + " [" + srcDstMapKey + "]");
					}
					uid = srcDstMapUid;
					uidString = StringPool.get(srcDstMapUid);
				}

				if (dstSrcMapUid == null) {
					if (MyLog.enabled && MyLog.level >= 9) {
						MyLog.d(9, "[dst-src] No entry uid for " + uid + " [" + dstSrcMapKey + "]");
					}

					if (uid == -1) {
						if (srcDstMapUid != null) {
							if (MyLog.enabled && MyLog.level >= 9) {
								MyLog.d(9, "[src-dst] Reassigning kernel packet -1 to " + srcDstMapUid);
							}
							uid = srcDstMapUid;
							uidString = StringPool.get(srcDstMapUid);
						} else {
							if (MyLog.enabled && MyLog.level >= 9) {
								MyLog.d(9, "[dst-src] New kernel entry -1 for [" + dstSrcMapKey + "]");
							}
							dstSrcMapUid = uid;
							logEntriesMap.put(dstSrcMapKey, dstSrcMapUid);
						}
					} else {
						if (MyLog.enabled && MyLog.level >= 9) {
							MyLog.d(9, "[dst-src] New entry " + uid + " for [" + dstSrcMapKey + "]");
						}
						dstSrcMapUid = uid;
						logEntriesMap.put(dstSrcMapKey, dstSrcMapUid);
					}
				} else {
					if (MyLog.enabled && MyLog.level >= 9) {
						MyLog.d(9, "[dst-src] Found entry uid " + dstSrcMapUid + " for " + uid + " [" + dstSrcMapKey + "]");
					}
					uid = dstSrcMapUid;
					uidString = StringPool.get(dstSrcMapUid);
				}
			} else {
				if (MyLog.enabled && MyLog.level >= 9) {
					MyLog.d(9, "Known uid");
				}

				if (srcDstMapUid == null || dstSrcMapUid == null || srcDstMapUid != uid || dstSrcMapUid != uid) {
					if (MyLog.enabled && MyLog.level >= 9) {
						MyLog.d(9, "Updating uid " + uid + " to netstat map for " + srcDstMapKey + " and " + dstSrcMapKey);
					}
					logEntriesMap.put(srcDstMapKey, uid);
					logEntriesMap.put(dstSrcMapKey, uid);
				}
			}

			entry = new LogEntry();
			entry.uid = uid;
			entry.uidString = uidString;
			entry.in = in;
			entry.out = out;
			entry.src = src;
			entry.spt = spt;
			entry.dst = dst;
			entry.dpt = dpt;
			entry.proto = proto;
			entry.len = len;
			entry.timestamp = System.currentTimeMillis();

			if (MyLog.enabled && MyLog.level >= 10) {
				MyLog.d(10, "+++ entry: (" + entry.uid + ") in=" + entry.in + " out=" + entry.out + " " + entry.src + ":" + entry.spt + " -> " + entry.dst + ":" + entry.dpt + " proto=" + entry.proto + " len=" + entry.len);
			}

			notifyNewEntry(entry);
		}
	}

	private static ApplicationsTracker.AppEntry appEntry;

	public void notifyNewEntry(LogEntry entry) {
		appEntry = ApplicationsTracker.uidMap.get(entry.uidString);

		// check if logfile needs to be opened and that external storage is available
//    if(logWriter == null) {
//      if(android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
//        try {
////          logWriter = new PrintWriter(new BufferedWriter(new FileWriter(logfile, true)), true);
//          Log.d("NetworkLog", "Opened " + logfile + " for logging");
//        } catch(final Exception e) {
//          Log.e("NetworkLog", "Exception opening logfile [" + logfile +"]", e);
//          handler.post(new Runnable() {
//            public void run() {
//              SysUtils.showError(context, getString(R.string.error_default_title), getString(R.string.error_openlogfile) + e.getMessage());
//            }
//          });
//          return;
//        }
//      } else {
//        Log.w("NetworkLog", "External storage " + logfile + " not available");
//      }
//    }

		if (!entry.isValid()) {
			return;
		}

		// log entry to logfile
		if (logWriter != null) {
			logWriter.println(entry.timestamp + "," + entry.in + "," + entry.out + "," + entry.uid + "," + entry.src + "," + entry.spt + "," + entry.dst + "," + entry.dpt + "," + entry.len + "," + entry.proto);
		}

		if (MyLog.enabled && MyLog.level >= 5) {
			MyLog.d(5, "[service] notifyNewEntry: clients: " + clients.size());
		}

		for (int i = clients.size() - 1; i >= 0; i--) {
			try {
				if (MyLog.enabled && MyLog.level >= 5) {
					MyLog.d(5, "[service] Sending entry to " + clients.get(i));
				}
				clients.get(i).send(Message.obtain(null, MSG_BROADCAST_LOG_ENTRY, entry));
			} catch (RemoteException e) {
				// client dead
				MyLog.d("[service] Dead client " + clients.get(i));
				clients.remove(i);
			}
		}

		ThroughputTracker.updateEntry(entry);
//    System.err.println("update");
	}

	public void stopLogger() {
		if (logger != null) {
			logger.stop();
		}
	}

	public void closeLogfile() {
		if (logWriter != null) {
			logWriter.close();
			logWriter = null;
		}
	}

	public void killLoggerCommand() {
		if (loggerShell != null) {
			loggerShell.sendCommand("kill $!", InteractiveShell.IGNORE_OUTPUT);
			loggerShell.close();
			loggerShell = null;
		}
	}

	public boolean startLoggerCommand() {
		MyLog.d("Starting iptables logger");

		if (Iptables.targets == null && Iptables.getTargets(this) == false) {
			return false;
		}

		String binary;
		if (Iptables.targets.get("LOG") != null) {
			binary = SysUtils.getGrepBinary(this);
			if (binary == null) {
				return false;
			}
		} else if (Iptables.targets.get("NFLOG") != null) {
			binary = SysUtils.getNflogBinary(this);
			if (binary == null) {
				return false;
			}
		} else {
			Log.e("NetworkLog", "No supported iptables targets available");
			SysUtils.showError(context,
					context.getResources().getString(R.string.iptables_error_unsupported_title),
					context.getResources().getString(R.string.iptables_error_missingfeatures_text));
			return false;
		}

		if (loggerShell == null) {
			loggerShell = new InteractiveShell("su", "LoggerShell");
			loggerShell.start();

			if (loggerShell.hasError()) {
				String error = loggerShell.getError(true);
				Log.e("NetworkLog", "Error starting logger shell: " + error);
				SysUtils.showError(context, context.getResources().getString(R.string.error_default_title), "Error starting logger shell: " + error);
				return false;
			}
		}

		if (Iptables.targets.get("LOG") != null) {
			Log.d("TAG", "startLoggerCommand: " + NetworkLog.settings.getLogMethod());
			switch (NetworkLog.settings.getLogMethod()) {
				case 1:
					loggerShell.sendCommand("grep '{NL}' /proc/kmsg &", InteractiveShell.BACKGROUND);
					break;
				case 2:
					loggerShell.sendCommand("cat /proc/kmsg &", InteractiveShell.BACKGROUND);
					break;
				default:
					loggerShell.sendCommand(binary + " '{NL}' /proc/kmsg &", InteractiveShell.BACKGROUND);
			}
		} else if (Iptables.targets.get("NFLOG") != null) {
			loggerShell.sendCommand(binary + " 0 &", InteractiveShell.BACKGROUND);
		}

		try {
			// give logger command a chance to do its thing
			Thread.sleep(1500);
		} catch (Exception e) {
		}

		if (loggerShell.hasError()) {
			SysUtils.showError(this, getString(R.string.error_default_title), loggerShell.getError(true));
			loggerShell.close();
			loggerShell = null;
			return false;
		}

		// ensure logger command didn't exit
		if (loggerShell.exitval == 0) {
			loggerShell.sendCommand("kill -0 $!", InteractiveShell.IGNORE_OUTPUT);
		}

		if (loggerShell.exitval != 0) {
			loggerShell.sendCommand("wait $!", InteractiveShell.IGNORE_OUTPUT);
			Log.e("NetworkLog", "Error starting logger: exit " + loggerShell.exitval);
			String error = "Error starting logger: exit " + loggerShell.exitval;
			loggerShell.close();
			loggerShell = null;
			SysUtils.showError(this, getString(R.string.error_default_title), error);
			return false;
		}

		return true;
	}

	public boolean startLogging() {
		killLoggerCommand();
		MyLog.d("adding logging rules");
		if (!Iptables.addRules(this)) {
			return false;
		}

		if (!startLoggerCommand()) {
			return false;
		}

		logger = new NetworkLogger();
		new Thread(logger, "NetworkLogger").start();

		startWatchingExternalStorage();
		startWatchingRules();

		return true;
	}

	public void stopLogging() {
		stopWatchingRules();
		Iptables.removeRules(this);
		stopWatchingExternalStorage();
		stopLogger();
		closeLogfile();
		killLoggerCommand();
	}

	public class NetworkLogger implements Runnable {
		boolean running = false;

		public void stop() {
			running = false;
		}

		public void run() {
			Log.d("NetworkLog", "Network logger " + this + " starting");
			String result;
			running = true;

			while (true) {
				while (running && !loggerShell.checkForExit()) {
					if (loggerShell.stdoutAvailable()) {
						result = loggerShell.readLine();
					} else {
						try {
							Thread.sleep(500);
						} catch (Exception e) {
							Log.d("NetworkLog", "NetworkLogger exception while sleeping", e);
						}

						continue;
					}

					if (running == false) {
						break;
					}

					if (result == null) {
						Log.d("NetworkLog", "Network logger " + this + " read null; exiting");
						break;
					}
					parseResult(result);
				}

				if (running != false) {
					Log.d("NetworkLog", "Network logger " + this + " terminated unexpectedly, restarting in 5 seconds");
					try {
						Thread.sleep(5000);
					} catch (Exception e) {
						// ignored
					}
					if (!startLoggerCommand()) {
						SysUtils.showError(context, context.getResources().getString(R.string.error_default_title),
								"Logger process has terminated unexpectedly and was unable to restart");
						running = false;
					}
				} else {
					Log.d("NetworkLog", "Network logger " + this + " reached end of loop; exiting");
					break;
				}
			}
		}
	}

	public static void updateLogfileString() {
		if (context == null) {
			return;
		}

		try {
			String file = logfile;
			if (file == null) {
				file = NetworkLog.settings.getLogFile();
			}
			logfileString = StringUtils.formatToBytes(new File(file).length()) + "B";
		} catch (Exception e) {
			logfileString = context.getResources().getString(R.string.logfile_bad) + e.getMessage();
		}

		if (instance != null && handler != null) {
			handler.postDelayed(updateNotificationRunner, 1000);
		}

		if (NetworkLog.handler != null) {
			NetworkLog.handler.post(NetworkLog.updateStatusRunner);
		}
	}

	BroadcastReceiver mExternalStorageReceiver = null;

	void updateExternalStorageState() {
		if (!android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
			// internal storage not mounted
			if (logWriter != null) {
				MyLog.d("Stopping logfile logging");
				logWriter.close();
				logWriter = null;
			}
		}
	}

	void startWatchingExternalStorage() {
		if (mExternalStorageReceiver == null) {
			mExternalStorageReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					Log.i("NetworkLog", "External storage: " + intent.getData());
					updateExternalStorageState();
				}
			};
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		registerReceiver(mExternalStorageReceiver, filter);
		updateExternalStorageState();
	}

	void stopWatchingExternalStorage() {
		try {
			unregisterReceiver(mExternalStorageReceiver);
		} catch (Exception e) {
			// ignored
		}
	}

	public class RulesWatcher extends Thread {
		boolean running = false;

		public RulesWatcher() {
			setName("RulesWatcher");
		}

		public void stopRunning() {
			running = false;
			interrupt();
		}

		@Override
		public void run() {
			String md5sum;
			String lastMd5sum = null;

			running = true;
			while (running) {
				try {
					Thread.sleep(watchRulesTimeout);
				} catch (Exception e) {
					// ignored
				}

				if (context == null || running == false) {
					break;
				}

				md5sum = MD5Sum.digestString(Iptables.getRules(context));

				if (lastMd5sum == null) {
					lastMd5sum = md5sum;
				} else {
					if (!md5sum.equals(lastMd5sum)) {
						Log.i("NetworkLog", "Iptables rules changed, reapplying Network Log rules");
						Iptables.removeRules(context);
						Iptables.addRules(context);
						lastMd5sum = MD5Sum.digestString(Iptables.getRules(context));
					}
				}
			}
		}
	}

	public static RulesWatcher rulesWatcher;

	void startWatchingRules() {
		stopWatchingRules();
		if (watchRules) {
			rulesWatcher = new RulesWatcher();
			rulesWatcher.start();
		}
	}

	void stopWatchingRules() {
		if (rulesWatcher != null) {
			rulesWatcher.stopRunning();
			rulesWatcher = null;
		}
	}
}
