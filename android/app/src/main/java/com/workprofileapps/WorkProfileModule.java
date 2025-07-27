package com.workprofileapps;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.lang.reflect.Method;
import java.util.List;

public class WorkProfileModule extends ReactContextBaseJavaModule {

    public WorkProfileModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "WorkProfileModule";
    }

    @ReactMethod
    public void getWorkProfileApps(Promise promise) {
        try {
            WritableArray result = Arguments.createArray();
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                promise.resolve(result);
                return;
            }

            UserManager userManager = (UserManager) getReactApplicationContext()
                .getSystemService(Context.USER_SERVICE);
            PackageManager pm = getReactApplicationContext().getPackageManager();

            if (userManager == null) {
                promise.resolve(result);
                return;
            }

            // Get all user profiles
            List<UserHandle> userProfiles = userManager.getUserProfiles();
            
            for (UserHandle userHandle : userProfiles) {
                // Check if this is a work profile
                if (isWorkProfile(userHandle)) {
                    try {
                        // Get apps for this work profile
                        List<ApplicationInfo> workApps = getInstalledApplicationsForUser(pm, userHandle);
                        
                        for (ApplicationInfo app : workApps) {
                            // Skip system apps unless they're work-related
                            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                                continue;
                            }
                            
                            WritableMap appInfo = Arguments.createMap();
                            appInfo.putString("packageName", app.packageName);
                            
                            try {
                                String appName = pm.getApplicationLabel(app).toString();
                                appInfo.putString("appName", appName);
                            } catch (Exception e) {
                                appInfo.putString("appName", app.packageName);
                            }
                            
                            try {
                                String versionName = pm.getPackageInfo(app.packageName, 0).versionName;
                                appInfo.putString("versionName", versionName != null ? versionName : "Unknown");
                            } catch (Exception e) {
                                appInfo.putString("versionName", "Unknown");
                            }
                            
                            try {
                                int versionCode = pm.getPackageInfo(app.packageName, 0).versionCode;
                                appInfo.putString("versionCode", String.valueOf(versionCode));
                            } catch (Exception e) {
                                appInfo.putString("versionCode", "Unknown");
                            }
                            
                            // This app is from work profile since we're iterating work profile users
                            appInfo.putBoolean("isWorkProfile", true);
                            result.pushMap(appInfo);
                        }
                    } catch (Exception e) {
                        // Continue to next profile if this one fails
                        continue;
                    }
                }
            }
            
            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get work profile apps: " + e.getMessage());
        }
    }

    private boolean isWorkProfile(UserHandle userHandle) {
        try {
            UserManager userManager = (UserManager) getReactApplicationContext()
                .getSystemService(Context.USER_SERVICE);
            
            if (userManager == null) {
                return false;
            }
            
            int userId = getUserId(userHandle);
            
            // Main user is always userId 0, work profile users have different IDs
            if (userId == 0) {
                return false;
            }
            
            // Check if this user profile is a managed profile (work profile)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    // Try to use reflection to call isManagedProfile with userId
                    Method isManagedProfileMethod = UserManager.class.getMethod("isManagedProfile", int.class);
                    return (Boolean) isManagedProfileMethod.invoke(userManager, userId);
                } catch (Exception e) {
                    // If reflection fails, assume non-zero user IDs are work profiles
                    return userId != 0;
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private int getUserId(UserHandle userHandle) {
        try {
            Method getIdentifier = UserHandle.class.getMethod("getIdentifier");
            return (Integer) getIdentifier.invoke(userHandle);
        } catch (Exception e) {
            return 0;
        }
    }

    private List<ApplicationInfo> getInstalledApplicationsForUser(PackageManager pm, UserHandle userHandle) 
            throws Exception {
        try {
            // Try to get apps for specific user
            Method getInstalledApplicationsAsUser = PackageManager.class.getMethod(
                "getInstalledApplicationsAsUser", int.class, int.class);
            
            return (List<ApplicationInfo>) getInstalledApplicationsAsUser.invoke(
                pm, PackageManager.GET_META_DATA, getUserId(userHandle));
        } catch (Exception e) {
            // Fallback to regular method
            return pm.getInstalledApplications(PackageManager.GET_META_DATA);
        }
    }

    @ReactMethod
    public void getAllApps(Promise promise) {
        try {
            WritableArray result = Arguments.createArray();
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // For older Android versions, just get regular apps
                PackageManager pm = getReactApplicationContext().getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                
                for (ApplicationInfo app : apps) {
                    WritableMap appInfo = Arguments.createMap();
                    appInfo.putString("packageName", app.packageName);
                    
                    try {
                        String appName = pm.getApplicationLabel(app).toString();
                        appInfo.putString("appName", appName);
                    } catch (Exception e) {
                        appInfo.putString("appName", app.packageName);
                    }
                    
                    try {
                        String versionName = pm.getPackageInfo(app.packageName, 0).versionName;
                        appInfo.putString("versionName", versionName != null ? versionName : "Unknown");
                    } catch (Exception e) {
                        appInfo.putString("versionName", "Unknown");
                    }
                    
                    try {
                        int versionCode = pm.getPackageInfo(app.packageName, 0).versionCode;
                        appInfo.putString("versionCode", String.valueOf(versionCode));
                    } catch (Exception e) {
                        appInfo.putString("versionCode", "Unknown");
                    }
                    
                    appInfo.putBoolean("isWorkProfile", false);
                    result.pushMap(appInfo);
                }
                
                promise.resolve(result);
                return;
            }

            UserManager userManager = (UserManager) getReactApplicationContext()
                .getSystemService(Context.USER_SERVICE);
            PackageManager pm = getReactApplicationContext().getPackageManager();

            if (userManager == null) {
                promise.resolve(result);
                return;
            }

            // First, get regular personal apps
            List<ApplicationInfo> personalApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo app : personalApps) {
                // Skip system apps to reduce noise
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }
                
                WritableMap appInfo = Arguments.createMap();
                appInfo.putString("packageName", app.packageName);
                
                try {
                    String appName = pm.getApplicationLabel(app).toString();
                    appInfo.putString("appName", appName);
                } catch (Exception e) {
                    appInfo.putString("appName", app.packageName);
                }
                
                try {
                    String versionName = pm.getPackageInfo(app.packageName, 0).versionName;
                    appInfo.putString("versionName", versionName != null ? versionName : "Unknown");
                } catch (Exception e) {
                    appInfo.putString("versionName", "Unknown");
                }
                
                try {
                    int versionCode = pm.getPackageInfo(app.packageName, 0).versionCode;
                    appInfo.putString("versionCode", String.valueOf(versionCode));
                } catch (Exception e) {
                    appInfo.putString("versionCode", "Unknown");
                }
                
                appInfo.putBoolean("isWorkProfile", false);
                result.pushMap(appInfo);
            }

            // Then, get work profile apps using LauncherApps
            UserHandle workProfile = getWorkProfileUser();
            if (workProfile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LauncherApps launcherApps = (LauncherApps) getReactApplicationContext()
                    .getSystemService(Context.LAUNCHER_APPS_SERVICE);
                
                if (launcherApps != null) {
                    try {
                        List<LauncherActivityInfo> workApps = launcherApps.getActivityList(null, workProfile);
                        
                        for (LauncherActivityInfo activityInfo : workApps) {
                            WritableMap appInfo = Arguments.createMap();
                            appInfo.putString("packageName", activityInfo.getApplicationInfo().packageName);
                            appInfo.putString("appName", activityInfo.getLabel().toString());
                            
                            try {
                                String versionName = pm.getPackageInfo(
                                    activityInfo.getApplicationInfo().packageName, 0).versionName;
                                appInfo.putString("versionName", versionName != null ? versionName : "Unknown");
                            } catch (Exception e) {
                                appInfo.putString("versionName", "Unknown");
                            }
                            
                            try {
                                int versionCode = pm.getPackageInfo(
                                    activityInfo.getApplicationInfo().packageName, 0).versionCode;
                                appInfo.putString("versionCode", String.valueOf(versionCode));
                            } catch (Exception e) {
                                appInfo.putString("versionCode", "Unknown");
                            }
                            
                            appInfo.putBoolean("isWorkProfile", true);
                            result.pushMap(appInfo);
                        }
                    } catch (Exception e) {
                        // Work profile apps couldn't be retrieved
                    }
                }
            }
            
            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get all apps: " + e.getMessage());
        }
    }

    @ReactMethod
    public void launchApp(String packageName, boolean isWorkProfile, Promise promise) {
        try {
            Context context = getReactApplicationContext();
            
            if (isWorkProfile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Use LauncherApps for work profile apps
                LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                UserHandle workProfile = getWorkProfileUser();
                
                if (launcherApps != null && workProfile != null) {
                    try {
                        // Get launcher activities for this package in work profile
                        List<LauncherActivityInfo> activities = launcherApps.getActivityList(packageName, workProfile);
                        if (!activities.isEmpty()) {
                            LauncherActivityInfo activity = activities.get(0);
                            launcherApps.startMainActivity(activity.getComponentName(), workProfile, null, null);
                            promise.resolve(true);
                            return;
                        }
                    } catch (Exception e) {
                        // Fall through to regular launch
                    }
                }
            }
            
            // Fallback to regular app launch for personal apps or if work profile launch fails
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                promise.resolve(true);
            } else {
                promise.reject("APP_NOT_FOUND", "Cannot launch app: " + packageName);
            }
        } catch (Exception e) {
            promise.reject("LAUNCH_ERROR", "Failed to launch app: " + e.getMessage());
        }
    }
    
    private UserHandle getWorkProfileUser() {
        try {
            UserManager userManager = (UserManager) getReactApplicationContext()
                .getSystemService(Context.USER_SERVICE);
            if (userManager == null) return null;
            
            List<UserHandle> userProfiles = userManager.getUserProfiles();
            for (UserHandle userHandle : userProfiles) {
                if (isWorkProfile(userHandle)) {
                    return userHandle;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    @ReactMethod
    public void hasWorkProfile(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                promise.resolve(false);
                return;
            }

            UserManager userManager = (UserManager) getReactApplicationContext()
                .getSystemService(Context.USER_SERVICE);
            
            if (userManager == null) {
                promise.resolve(false);
                return;
            }

            List<UserHandle> userProfiles = userManager.getUserProfiles();
            
            for (UserHandle userHandle : userProfiles) {
                if (isWorkProfile(userHandle)) {
                    promise.resolve(true);
                    return;
                }
            }
            
            promise.resolve(false);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }
}