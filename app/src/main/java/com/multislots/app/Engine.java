package com.multislots.app;

import top.niunaijun.blackbox.BlackBoxCore;

/** Thin wrapper over the virtualization engine, so UI code does not touch it directly. */
final class Engine {
    private Engine() {}

    static BlackBoxCore core() {
        return BlackBoxCore.get();
    }

    /** Copies the app installed on the phone into the isolated slot. Returns null on success, else an error text. */
    static String install(String pkg, int userId) {
        try {
            if (core().getUserInfo(userId) == null) core().createUser(userId);
            top.niunaijun.blackbox.entity.pm.InstallResult r = core().installPackageAsUser(pkg, userId);
            if (r != null && r.success) return null;
            return r == null ? "unknown error" : String.valueOf(r.msg);
        } catch (Throwable t) {
            return t.toString();
        }
    }

    static boolean isInstalled(String pkg, int userId) {
        try {
            return core().isInstalled(pkg, userId);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean launch(String pkg, int userId) {
        try {
            return core().launchApk(pkg, userId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Removes the slot with all its data (the account in it is logged out locally). */
    static void remove(String pkg, int userId) {
        try {
            core().uninstallPackageAsUser(pkg, userId);
        } catch (Throwable ignored) {
        }
        try {
            core().deleteUser(userId);
        } catch (Throwable ignored) {
        }
    }
}
