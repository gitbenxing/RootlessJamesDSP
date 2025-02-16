package me.timschneeberger.hiddenapi_impl;

import android.app.AppOpsManager;
import android.app.AppOpsManagerHidden;
import android.media.IAudioPolicyService;
import android.os.IBinder;
import android.permission.IPermissionManager;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.app.IAppOpsService;

import java.util.Objects;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class ShizukuSystemServerApi {

    private static final Singleton<IPermissionManager> PERMISSION_MANAGER = new Singleton<IPermissionManager>() {
        @Override
        protected IPermissionManager create() {
            IBinder service = SystemServiceHelper.getSystemService("permissionmgr");
            ShizukuBinderWrapper wrapper = new ShizukuBinderWrapper(service);
            return IPermissionManager.Stub.asInterface(wrapper);
        }
    };

    public static final Singleton<IAppOpsService> APP_OPS_SERVICE = new Singleton<IAppOpsService>() {
        @Override
        protected IAppOpsService create() {
            IBinder service = SystemServiceHelper.getSystemService("appops");
            ShizukuBinderWrapper wrapper = new ShizukuBinderWrapper(service);
            return IAppOpsService.Stub.asInterface(wrapper);
        }
    };

    public static final Singleton<IAudioPolicyService> AUDIO_POLICY_SERVICE = new Singleton<IAudioPolicyService>() {
        @Override
        protected IAudioPolicyService create() {
            IBinder service = SystemServiceHelper.getSystemService("media.audio_policy");
            ShizukuBinderWrapper wrapper = new ShizukuBinderWrapper(service);
            return new AudioPolicyService(wrapper);
        }
    };

    public static void PermissionManager_grantRuntimePermission(String packageName, String permissionName, int userId) {
        try {
            PERMISSION_MANAGER.getOrThrow().grantRuntimePermission(packageName, permissionName, userId);
        }
        catch(Exception ex) {
            Log.e("ShizukuSystemServerApi", "Failed to call app ops service");
        }
    }

    public static final String APP_OPS_MODE_ALLOW = "allow";
    public static final String APP_OPS_MODE_IGNORE = "ignore";
    public static final String APP_OPS_MODE_DENY = "deny";
    public static final String APP_OPS_MODE_DEFAULT = "default";
    public static final String APP_OPS_MODE_FOREGROUND = "foreground";

    public static final String APP_OPS_OP_PROJECT_MEDIA = "PROJECT_MEDIA";

    public static boolean AppOpsService_setMode(String op, int packageUid, String packageName, String mode) throws RemoteException {
        int index = -1;
        for(int i = 0; i <= 10; i++) {
            if(mode.equals(AppOpsManagerHidden.modeToName(i))) {
                index = i;
                break;
            }
        }

        int opIndex = -1;
        try {
            opIndex = AppOpsManagerHidden.strOpToOp(op);
        }
        catch(IllegalArgumentException ignored) {}
        try {
            opIndex = AppOpsManagerHidden.strDebugOpToOp(op);
        }
        catch(IllegalArgumentException ignored) {}

        if(index < 0 || opIndex < 0)
            return false;

        try {
            APP_OPS_SERVICE.getOrThrow().setMode(
                    opIndex,
                    packageUid,
                    packageName,
                    index
            );
        }
        catch(NullPointerException ex) {
            Log.e("ShizukuSystemServerApi", "Failed to call app ops service");
            return false;
        }

        return true;
    }

    public static void AudioPolicyService_setAllowedCapturePolicy(int uid, CapturePolicy capturePolicy) {
        int flags;
        switch (capturePolicy) {
            case ALLOW_CAPTURE_BY_ALL:
                flags = IAudioPolicyService.AUDIO_FLAG_NONE;
                break;
            case ALLOW_CAPTURE_BY_SYSTEM:
                flags = IAudioPolicyService.AUDIO_FLAG_NO_MEDIA_PROJECTION;
                break;
            case ALLOW_CAPTURE_BY_NONE:
                flags = IAudioPolicyService.AUDIO_FLAG_NO_MEDIA_PROJECTION |
                        IAudioPolicyService.AUDIO_FLAG_NO_SYSTEM_CAPTURE;
                break;
            default:
                throw new IllegalArgumentException();
        }

        try {
            Log.d("ShizukuSystemServerApi", "AudioPolicyService_setAllowedCapturePolicy flags=" + flags);
            AUDIO_POLICY_SERVICE.getOrThrow().setAllowedCapturePolicy(uid, flags);
        }
        catch (Exception ex) {
            Log.d("ShizukuSystemServerApi", ex.toString());
        }
    }
}