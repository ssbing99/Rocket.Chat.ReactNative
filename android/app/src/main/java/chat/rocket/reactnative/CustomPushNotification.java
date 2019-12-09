package chat.rocket.reactnative;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;

import com.google.gson.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import java.util.concurrent.ExecutionException;
import java.lang.InterruptedException;

import com.wix.reactnativenotifications.core.AppLaunchHelper;
import com.wix.reactnativenotifications.core.AppLifecycleFacade;
import com.wix.reactnativenotifications.core.JsIOHelper;
import com.wix.reactnativenotifications.core.notification.PushNotification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_EVENT_NAME;

public class CustomPushNotification extends PushNotification {
    public CustomPushNotification(Context context, Bundle bundle, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper, JsIOHelper jsIoHelper) {
        super(context, bundle, appLifecycleFacade, appLaunchHelper, jsIoHelper);
    }

    private static Map<String, List<String>> notificationMessages = new HashMap<String, List<String>>();
    public static String KEY_REPLY = "KEY_REPLY";

    @Override
    public void onReceived() throws InvalidNotificationException {
        final Bundle bundle = mNotificationProps.asBundle();

        String notId = bundle.getString("notId");
        String message = bundle.getString("message");

        if (notificationMessages.get(notId) == null) {
            notificationMessages.put(notId, new ArrayList<String>());
        }
        notificationMessages.get(notId).add(message);

        super.postNotification(Integer.parseInt(notId));

        notifyReceivedToJS();
    }

    @Override
    public void onOpened() {
        Bundle bundle = mNotificationProps.asBundle();
        final String notId = bundle.getString("notId");
        notificationMessages.remove(notId);
        digestNotification();
    }

    @Override
    protected Notification.Builder getNotificationBuilder(PendingIntent intent) {
        final Notification.Builder notification = new Notification.Builder(mContext);

        final Resources res = mContext.getResources();
        String packageName = mContext.getPackageName();

        Bundle bundle = mNotificationProps.asBundle();
        int smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
        String title = bundle.getString("title");
        String message = bundle.getString("message");
        String notId = bundle.getString("notId");

        Gson gson = new Gson();
        Ejson ejson = gson.fromJson(bundle.getString("ejson", "{}"), Ejson.class);

        String CHANNEL_ID = "rocketchatrn_channel_01";
        String CHANNEL_NAME = "All";

        Notification.InboxStyle messageStyle = new Notification.InboxStyle();
        List<String> messages = notificationMessages.get(notId);
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                messageStyle.addLine(messages.get(i));
            }
        }

        notification
            .setSmallIcon(smallIconResId)
            .setLargeIcon(getAvatar(ejson.getAvatarUri()))
            .setContentIntent(intent)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(messageStyle)
            .setNumber(messages.size())
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notification.setColor(mContext.getColor(R.color.notification_text));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                                                                  CHANNEL_NAME,
                                                                  NotificationManager.IMPORTANCE_DEFAULT);

            final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);

            notification.setChannelId(CHANNEL_ID);
        }

        notificationReply(notification, Integer.parseInt(notId), bundle);

        return notification;
    }

    private void notifyReceivedToJS() {
        mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_EVENT_NAME, mNotificationProps.asBundle(), mAppLifecycleFacade.getRunningReactContext());
    }

    private Bitmap getAvatar(String uri) {
        try {
            return Glide.with(mContext)
                .asBitmap()
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(10)))
                .load(uri)
                .submit(100, 100)
                .get();
        } catch (final ExecutionException | InterruptedException e) {
            return null;
        }
    }

    private void notificationReply(Notification.Builder notification, int notificationId, Bundle bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        String label = "Reply";

        final Resources res = mContext.getResources();
        String packageName = mContext.getPackageName();
        int smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);

        Intent replyIntent = new Intent(mContext, ReplyBroadcast.class);
        replyIntent.setAction(KEY_REPLY);
        replyIntent.putExtra("pushNotification", bundle);

        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(mContext, notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteInput remoteInput = new RemoteInput.Builder(KEY_REPLY)
            .setLabel(label)
            .build();

        CharSequence title = label;
        Notification.Action replyAction = new Notification.Action.Builder(smallIconResId, title, replyPendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build();

        notification
            .setShowWhen(true)
            .addAction(replyAction);
    }
}
