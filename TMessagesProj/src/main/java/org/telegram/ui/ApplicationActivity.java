/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.NotificationView;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ApplicationActivity extends ActionBarActivity implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate {
    private boolean finished = false;
    private NotificationView notificationView;
    private String photoPath = null;
    private String videoPath = null;
    private String sendingText = null;
    private int currentConnectionState;
    private View statusView;
    private View backStatusButton;
    private View statusBackground;
    private TextView statusText;
    private View containerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            Utilities.statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        NotificationCenter.Instance.postNotificationName(702, this);
        currentConnectionState = ConnectionsManager.Instance.connectionState;
        for (BaseFragment fragment : ApplicationLoader.fragmentsStack) {
            if (fragment.fragmentView != null) {
                ViewGroup parent = (ViewGroup)fragment.fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(fragment.fragmentView);
                }
                fragment.fragmentView = null;
            }
            fragment.parentActivity = this;
        }
        setContentView(R.layout.application_layout);
        NotificationCenter.Instance.addObserver(this, 1234);
        NotificationCenter.Instance.addObserver(this, 658);
        NotificationCenter.Instance.addObserver(this, 701);
        NotificationCenter.Instance.addObserver(this, 702);
        NotificationCenter.Instance.addObserver(this, 703);
        NotificationCenter.Instance.addObserver(this, GalleryImageViewer.needShowAllMedia);
        getSupportActionBar().setLogo(R.drawable.ab_icon_fixed2);

        statusView = getLayoutInflater().inflate(R.layout.updating_state_layout, null);
        statusBackground = statusView.findViewById(R.id.back_button_background);
        backStatusButton = statusView.findViewById(R.id.back_button);
        containerView = findViewById(R.id.container);
        statusText = (TextView)statusView.findViewById(R.id.status_text);
        statusBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ApplicationLoader.fragmentsStack.size() > 1) {
                    onBackPressed();
                }
            }
        });

        if (ApplicationLoader.fragmentsStack.isEmpty()) {
            MessagesActivity fragment = new MessagesActivity();
            fragment.onFragmentCreate();
            ApplicationLoader.fragmentsStack.add(fragment);
        }

        boolean pushOpened = false;

        Integer push_user_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_user_id", 0);
        Integer push_chat_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_chat_id", 0);
        Integer push_enc_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_enc_id", 0);
        Integer open_settings = (Integer)NotificationCenter.Instance.getFromMemCache("open_settings", 0);
        photoPath = (String)NotificationCenter.Instance.getFromMemCache(533);
        videoPath = (String)NotificationCenter.Instance.getFromMemCache(534);
        sendingText = (String)NotificationCenter.Instance.getFromMemCache(535);

        if (push_user_id != 0) {
            if (push_user_id == UserConfig.clientUserId) {
                open_settings = 1;
            } else {
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("user_id", push_user_id);
                fragment.setArguments(bundle);
                if (fragment.onFragmentCreate()) {
                    pushOpened = true;
                    ApplicationLoader.fragmentsStack.add(fragment);
                    getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
                }
            }
        } else if (push_chat_id != 0) {
            ChatActivity fragment = new ChatActivity();
            Bundle bundle = new Bundle();
            bundle.putInt("chat_id", push_chat_id);
            fragment.setArguments(bundle);
            if (fragment.onFragmentCreate()) {
                pushOpened = true;
                ApplicationLoader.fragmentsStack.add(fragment);
                getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
            }
        }  else if (push_enc_id != 0) {
            ChatActivity fragment = new ChatActivity();
            Bundle bundle = new Bundle();
            bundle.putInt("enc_id", push_enc_id);
            fragment.setArguments(bundle);
            if (fragment.onFragmentCreate()) {
                pushOpened = true;
                ApplicationLoader.fragmentsStack.add(fragment);
                getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
            }
        }
        if (videoPath != null || photoPath != null || sendingText != null) {
            MessagesActivity fragment = new MessagesActivity();
            fragment.selectAlertString = R.string.ForwardMessagesTo;
            fragment.animationType = 1;
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            fragment.setArguments(args);
            fragment.delegate = this;
            ApplicationLoader.fragmentsStack.add(fragment);
            fragment.onFragmentCreate();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, fragment.getTag()).commitAllowingStateLoss();
            pushOpened = true;
        }
        if (open_settings != 0) {
            SettingsActivity fragment = new SettingsActivity();
            ApplicationLoader.fragmentsStack.add(fragment);
            fragment.onFragmentCreate();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "settings").commitAllowingStateLoss();
            pushOpened = true;
        }
        if (!pushOpened) {
            BaseFragment fragment = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, fragment.getTag()).commitAllowingStateLoss();
        }

        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        getWindow().setFormat(PixelFormat.RGB_565);
    }

    @SuppressWarnings("unchecked")
    private void prepareForHideShowActionBar() {
        try {
            Class firstClass = getSupportActionBar().getClass();
            Class aClass = firstClass.getSuperclass();
            if (aClass == android.support.v7.app.ActionBar.class) {
                Method method = firstClass.getDeclaredMethod("setShowHideAnimationEnabled", boolean.class);
                method.invoke(getSupportActionBar(), false);
            } else {
                Field field = aClass.getDeclaredField("mActionBar");
                field.setAccessible(true);
                Method method = field.get(getSupportActionBar()).getClass().getDeclaredMethod("setShowHideAnimationEnabled", boolean.class);
                method.invoke(field.get(getSupportActionBar()), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showActionBar() {
        prepareForHideShowActionBar();
        getSupportActionBar().show();
    }

    public void hideActionBar() {
        prepareForHideShowActionBar();
        getSupportActionBar().hide();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        photoPath = (String)NotificationCenter.Instance.getFromMemCache(533);
        videoPath = (String)NotificationCenter.Instance.getFromMemCache(534);
        sendingText = (String)NotificationCenter.Instance.getFromMemCache(535);
        if (videoPath != null || photoPath != null || sendingText != null) {
            MessagesActivity fragment = new MessagesActivity();
            fragment.selectAlertString = R.string.ForwardMessagesTo;
            fragment.animationType = 1;
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            fragment.setArguments(args);
            fragment.delegate = this;
            ApplicationLoader.fragmentsStack.add(fragment);
            fragment.onFragmentCreate();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, fragment.getTag()).commitAllowingStateLoss();
        }

        Integer push_user_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_user_id", 0);
        Integer push_chat_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_chat_id", 0);
        Integer push_enc_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_enc_id", 0);
        Integer open_settings = (Integer)NotificationCenter.Instance.getFromMemCache("open_settings", 0);

        if (push_user_id != 0) {
            if (push_user_id == UserConfig.clientUserId) {
                open_settings = 1;
            } else {
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("user_id", push_user_id);
                fragment.setArguments(bundle);
                if (fragment.onFragmentCreate()) {
                    ApplicationLoader.fragmentsStack.add(fragment);
                    getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
                }
            }
        } else if (push_chat_id != 0) {
            ChatActivity fragment = new ChatActivity();
            Bundle bundle = new Bundle();
            bundle.putInt("chat_id", push_chat_id);
            fragment.setArguments(bundle);
            if (fragment.onFragmentCreate()) {
                ApplicationLoader.fragmentsStack.add(fragment);
                getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
            }
        } else if (push_enc_id != 0) {
            ChatActivity fragment = new ChatActivity();
            Bundle bundle = new Bundle();
            bundle.putInt("enc_id", push_enc_id);
            fragment.setArguments(bundle);
            if (fragment.onFragmentCreate()) {
                ApplicationLoader.fragmentsStack.add(fragment);
                getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
            }
        }
        if (open_settings != 0) {
            SettingsActivity fragment = new SettingsActivity();
            ApplicationLoader.fragmentsStack.add(fragment);
            fragment.onFragmentCreate();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "settings").commitAllowingStateLoss();
        }
    }

    @Override
    public void didSelectDialog(MessagesActivity messageFragment, long dialog_id) {
        if (dialog_id != 0) {
            int lower_part = (int)dialog_id;

            ChatActivity fragment = new ChatActivity();
            Bundle bundle = new Bundle();
            if (lower_part != 0) {
                if (lower_part > 0) {
                    NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                    bundle.putInt("user_id", lower_part);
                    fragment.setArguments(bundle);
                    fragment.scrollToTopOnResume = true;
                    presentFragment(fragment, "chat" + Math.random(), true, false);
                } else if (lower_part < 0) {
                    NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                    bundle.putInt("chat_id", -lower_part);
                    fragment.setArguments(bundle);
                    fragment.scrollToTopOnResume = true;
                    presentFragment(fragment, "chat" + Math.random(), true, false);
                }
            } else {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                int chat_id = (int)(dialog_id >> 32);
                bundle.putInt("enc_id", chat_id);
                fragment.setArguments(bundle);
                fragment.scrollToTopOnResume = true;
                presentFragment(fragment, "chat" + Math.random(), true, false);
            }
            if (photoPath != null) {
                fragment.processSendingPhoto(photoPath);
            } else if (videoPath != null) {
                fragment.processSendingVideo(videoPath);
            } else if (sendingText != null) {
                fragment.processSendingText(sendingText);
            }
            photoPath = null;
            videoPath = null;
            sendingText = null;
        }
    }

    private void checkForCrashes() {
        CrashManager.register(this, ConnectionsManager.HOCKEY_APP_HASH);
    }

    private void checkForUpdates() {
        if (ConnectionsManager.DEBUG_VERSION) {
            UpdateManager.register(this, ConnectionsManager.HOCKEY_APP_HASH);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ApplicationLoader.lastPauseTime = System.currentTimeMillis();
        if (notificationView != null) {
            notificationView.hide(false);
        }
        View focusView = getCurrentFocus();
        if (focusView instanceof EditText) {
            focusView.clearFocus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        processOnFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (notificationView == null && getLayoutInflater() != null) {
            notificationView = (NotificationView) getLayoutInflater().inflate(R.layout.notification_layout, null);
        }
        fixLayout();
        checkForCrashes();
        checkForUpdates();
        ApplicationLoader.resetLastPauseTime();
        supportInvalidateOptionsMenu();
        updateActionBar();
        try {
            NotificationManager mNotificationManager = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(1);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void processOnFinish() {
        if (finished) {
            return;
        }
        finished = true;
        NotificationCenter.Instance.removeObserver(this, 1234);
        NotificationCenter.Instance.removeObserver(this, 658);
        NotificationCenter.Instance.removeObserver(this, 701);
        NotificationCenter.Instance.removeObserver(this, 702);
        NotificationCenter.Instance.removeObserver(this, 703);
        NotificationCenter.Instance.removeObserver(this, GalleryImageViewer.needShowAllMedia);
        if (notificationView != null) {
            notificationView.hide(false);
            notificationView.destroy();
            notificationView = null;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (containerView != null) {
            ViewTreeObserver obs = containerView.getViewTreeObserver();
            obs.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
                    int rotation = manager.getDefaultDisplay().getRotation();

                    int height;
                    int currentActionBarHeight = getSupportActionBar().getHeight();
                    if (currentActionBarHeight != Utilities.dp(48) && currentActionBarHeight != Utilities.dp(40)) {
                        height = currentActionBarHeight;
                    } else {
                        height = Utilities.dp(48);
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            height = Utilities.dp(40);
                        }
                    }

                    if (notificationView != null) {
                        notificationView.applyOrientationPaddings(rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90, height);
                    }

                    if (Build.VERSION.SDK_INT < 16) {
                        containerView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        containerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == 1234) {
            for (BaseFragment fragment : ApplicationLoader.fragmentsStack) {
                fragment.onFragmentDestroy();
            }
            ApplicationLoader.fragmentsStack.clear();
            Intent intent2 = new Intent(this, LaunchActivity.class);
            startActivity(intent2);
            processOnFinish();
            finish();
        } else if (id == GalleryImageViewer.needShowAllMedia) {
            long dialog_id = (Long)args[0];
            MediaActivity fragment = new MediaActivity();
            Bundle bundle = new Bundle();
            if (dialog_id != 0) {
                bundle.putLong("dialog_id", dialog_id);
                fragment.setArguments(bundle);
                presentFragment(fragment, "media_" + dialog_id, false);
            }
        } else if (id == 658) {
            Integer push_user_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_user_id", 0);
            Integer push_chat_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_chat_id", 0);
            Integer push_enc_id = (Integer)NotificationCenter.Instance.getFromMemCache("push_enc_id", 0);

            if (push_user_id != 0) {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("user_id", push_user_id);
                fragment.setArguments(bundle);
                if (fragment.onFragmentCreate()) {
                    if (ApplicationLoader.fragmentsStack.size() > 0) {
                        BaseFragment lastFragment = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
                        lastFragment.willBeHidden();
                    }
                    ApplicationLoader.fragmentsStack.add(fragment);
                    getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
                }
            } else if (push_chat_id != 0) {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("chat_id", push_chat_id);
                fragment.setArguments(bundle);
                if (fragment.onFragmentCreate()) {
                    if (ApplicationLoader.fragmentsStack.size() > 0) {
                        BaseFragment lastFragment = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
                        lastFragment.willBeHidden();
                    }
                    ApplicationLoader.fragmentsStack.add(fragment);
                    getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
                }
            }  else if (push_enc_id != 0) {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("enc_id", push_enc_id);
                fragment.setArguments(bundle);
                if (fragment.onFragmentCreate()) {
                    if (ApplicationLoader.fragmentsStack.size() > 0) {
                        BaseFragment lastFragment = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
                        lastFragment.willBeHidden();
                    }
                    ApplicationLoader.fragmentsStack.add(fragment);
                    getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chat" + Math.random()).commitAllowingStateLoss();
                }
            }
        } else if (id == 701) {
            if (notificationView != null) {
                MessageObject message = (MessageObject)args[0];
                notificationView.show(message);
            }
        } else if (id == 702) {
            if (args[0] != this) {
                processOnFinish();
            }
        } else if (id == 703) {
            int state = (Integer)args[0];
            if (currentConnectionState != state) {
                FileLog.e("tmessages", "switch to state " + state);
                currentConnectionState = state;
                updateActionBar();
            }
        }
    }

    public void fixBackButton() {
        if(android.os.Build.VERSION.SDK_INT == 19) {
            //workaround for back button dissapear
            try {
                Class firstClass = getSupportActionBar().getClass();
                Class aClass = firstClass.getSuperclass();
                if (aClass == android.support.v7.app.ActionBar.class) {

                } else {
                    Field field = aClass.getDeclaredField("mActionBar");
                    field.setAccessible(true);
                    android.app.ActionBar bar = (android.app.ActionBar)field.get(getSupportActionBar());

                    field = bar.getClass().getDeclaredField("mActionView");
                    field.setAccessible(true);
                    View v = (View)field.get(bar);
                    aClass = v.getClass();

                    field = aClass.getDeclaredField("mHomeLayout");
                    field.setAccessible(true);
                    v = (View)field.get(v);
                    v.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void updateActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        BaseFragment currentFragment = null;
        if (!ApplicationLoader.fragmentsStack.isEmpty()) {
            currentFragment = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
        }
        boolean canApplyLoading = true;
        if (currentFragment != null && (currentConnectionState == 0 || !currentFragment.canApplyUpdateStatus() || statusView == null)) {
            currentFragment.applySelfActionBar();
            canApplyLoading = false;
        }
        if (canApplyLoading) {
            if (statusView != null) {
                actionBar.setDisplayShowTitleEnabled(false);
                actionBar.setDisplayShowHomeEnabled(false);
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setDisplayUseLogoEnabled(false);
                actionBar.setDisplayShowCustomEnabled(true);
                actionBar.setSubtitle(null);

                if (ApplicationLoader.fragmentsStack.size() > 1) {
                    backStatusButton.setVisibility(View.VISIBLE);
                    statusBackground.setEnabled(true);
                } else {
                    backStatusButton.setVisibility(View.GONE);
                    statusBackground.setEnabled(false);
                }

                if (currentConnectionState == 1) {
                    statusText.setText(getString(R.string.WaitingForNetwork));
                } else if (currentConnectionState == 2) {
                    statusText.setText(getString(R.string.Connecting));
                } else if (currentConnectionState == 3) {
                    statusText.setText(getString(R.string.Updating));
                }
                if (actionBar.getCustomView() != statusView) {
                    actionBar.setCustomView(statusView);
                }

                try {
                    if (statusView.getLayoutParams() instanceof android.support.v7.app.ActionBar.LayoutParams) {
                        android.support.v7.app.ActionBar.LayoutParams statusParams = (android.support.v7.app.ActionBar.LayoutParams)statusView.getLayoutParams();
                        statusText.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST));
                        statusParams.width = (statusText.getMeasuredWidth() + Utilities.dp(54));
                        statusView.setLayoutParams(statusParams);
                    } else if (statusView.getLayoutParams() instanceof android.app.ActionBar.LayoutParams) {
                        android.app.ActionBar.LayoutParams statusParams = (android.app.ActionBar.LayoutParams)statusView.getLayoutParams();
                        statusText.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST));
                        statusParams.width = (statusText.getMeasuredWidth() + Utilities.dp(54));
                        statusView.setLayoutParams(statusParams);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void presentFragment(BaseFragment fragment, String tag, boolean bySwipe) {
        presentFragment(fragment, tag, false, bySwipe);
    }

    public void presentFragment(BaseFragment fragment, String tag, boolean removeLast, boolean bySwipe) {
        if (getCurrentFocus() != null) {
            Utilities.hideKeyboard(getCurrentFocus());
        }
        if (!fragment.onFragmentCreate()) {
            return;
        }
        BaseFragment current = null;
        if (!ApplicationLoader.fragmentsStack.isEmpty()) {
            current = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
        }
        if (current != null) {
            current.willBeHidden();
        }
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fTrans = fm.beginTransaction();
        if (removeLast && current != null) {
            ApplicationLoader.fragmentsStack.remove(ApplicationLoader.fragmentsStack.size() - 1);
            current.onFragmentDestroy();
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (animations) {
            if (bySwipe) {
                fTrans.setCustomAnimations(R.anim.slide_left, R.anim.no_anim);
            } else {
                fTrans.setCustomAnimations(R.anim.scale_in, R.anim.no_anim);
            }
        }
        fTrans.replace(R.id.container, fragment, tag);
        fTrans.commitAllowingStateLoss();
        ApplicationLoader.fragmentsStack.add(fragment);
    }

    public void removeFromStack(BaseFragment fragment) {
        ApplicationLoader.fragmentsStack.remove(fragment);
        fragment.onFragmentDestroy();
    }

    public void finishFragment(boolean bySwipe) {
        if (getCurrentFocus() != null) {
            Utilities.hideKeyboard(getCurrentFocus());
        }
        if (ApplicationLoader.fragmentsStack.size() < 2) {
            for (BaseFragment fragment : ApplicationLoader.fragmentsStack) {
                fragment.onFragmentDestroy();
            }
            ApplicationLoader.fragmentsStack.clear();
            MessagesActivity fragment = new MessagesActivity();
            fragment.onFragmentCreate();
            ApplicationLoader.fragmentsStack.add(fragment);
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "chats").commitAllowingStateLoss();
            return;
        }
        BaseFragment fragment = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
        fragment.onFragmentDestroy();
        BaseFragment prev = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 2);
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fTrans = fm.beginTransaction();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (animations) {
            if (bySwipe) {
                fTrans.setCustomAnimations(R.anim.no_anim_show, R.anim.slide_right_away);
            } else {
                fTrans.setCustomAnimations(R.anim.no_anim_show, R.anim.scale_out);
            }
        }
        fTrans.replace(R.id.container, prev, prev.getTag());
        fTrans.commitAllowingStateLoss();
        ApplicationLoader.fragmentsStack.remove(ApplicationLoader.fragmentsStack.size() - 1);
    }

    @Override
    public void onBackPressed() {
        if (ApplicationLoader.fragmentsStack.size() == 1) {
            ApplicationLoader.fragmentsStack.get(0).onFragmentDestroy();
            ApplicationLoader.fragmentsStack.clear();
            processOnFinish();
            finish();
            return;
        }
        if (!ApplicationLoader.fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = ApplicationLoader.fragmentsStack.get(ApplicationLoader.fragmentsStack.size() - 1);
            if (lastFragment.onBackPressed()) {
                finishFragment(false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }
}
