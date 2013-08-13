/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.cyanogenmod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.Utils;

import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    private static final String CPU_SETTINGS_PROP = "sys.cpufreq.restored";
    private static final String IOSCHED_SETTINGS_PROP = "sys.iosched.restored";
    private static final String KSM_SETTINGS_PROP = "sys.ksm.restored";
    private static final String UNDERVOLTING_PROP = "persist.sys.undervolt";
    private static String UV_MODULE;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (SystemProperties.getBoolean(CPU_SETTINGS_PROP, false) == false
                && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            SystemProperties.set(CPU_SETTINGS_PROP, "true");
            configureCPU(ctx);
        } else {
            SystemProperties.set(CPU_SETTINGS_PROP, "false");
        }

        if (SystemProperties.getBoolean(IOSCHED_SETTINGS_PROP, false) == false
                && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            SystemProperties.set(IOSCHED_SETTINGS_PROP, "true");
            configureIOSched(ctx);
        } else {
            SystemProperties.set(IOSCHED_SETTINGS_PROP, "false");
        }

        if (Utils.fileExists(MemoryManagement.KSM_RUN_FILE)) {
            if (SystemProperties.getBoolean(KSM_SETTINGS_PROP, false) == false
                    && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                SystemProperties.set(KSM_SETTINGS_PROP, "true");
                configureKSM(ctx);
            } else {
                SystemProperties.set(KSM_SETTINGS_PROP, "false");
            }
        }
    }

    private void initFreqCapFiles(Context ctx)
    {
        if (Processor.freqCapFilesInitialized) return;
        Processor.FREQ_MAX_FILE = ctx.getResources().getString(R.string.max_cpu_freq_file);
        Processor.FREQ_MIN_FILE = ctx.getResources().getString(R.string.min_cpu_freq_file);
        Processor.freqCapFilesInitialized = true;
    }

    private void configureCPU(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        if (prefs.getBoolean(Processor.SOB_PREF, false) == false) {
            SystemProperties.set(UNDERVOLTING_PROP, "0");
            Log.i(TAG, "Restore disabled by user preference.");
            return;
        }

        UV_MODULE = ctx.getResources().getString(com.android.settings.R.string.undervolting_module);
	if (SystemProperties.getBoolean(UNDERVOLTING_PROP, false) == true) {
            String vdd_levels_path = "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels";
            File vdd_levels = new File(vdd_levels_path);
            if (vdd_levels.isFile() && vdd_levels.canRead()) {
                Utils.fileWriteOneLine(vdd_levels_path, "122880 0");
                Utils.fileWriteOneLine(vdd_levels_path, "245760 2");
                Utils.fileWriteOneLine(vdd_levels_path, "320000 3");
                Utils.fileWriteOneLine(vdd_levels_path, "480000 5");
                Utils.fileWriteOneLine(vdd_levels_path, "600000 6");
		Utils.fileWriteOneLine(vdd_levels_path, "800000 6");
            }
            else
                // insmod undervolting module for .29 kernel
                insmod(UV_MODULE, true);
        }
        else {
            String vdd_levels_path = "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels";
            File vdd_levels = new File(vdd_levels_path);
            if (vdd_levels.isFile() && vdd_levels.canRead()) {
                Utils.fileWriteOneLine(vdd_levels_path, "122880 3");
                Utils.fileWriteOneLine(vdd_levels_path, "245760 4");
                Utils.fileWriteOneLine(vdd_levels_path, "320000 5");
                Utils.fileWriteOneLine(vdd_levels_path, "480000 6");
                Utils.fileWriteOneLine(vdd_levels_path, "600000 7");
                Utils.fileWriteOneLine(vdd_levels_path, "800000 7");
            }
	}

        String governor = prefs.getString(Processor.GOV_PREF, null);
        String minFrequency = prefs.getString(Processor.FREQ_MIN_PREF, null);
        String maxFrequency = prefs.getString(Processor.FREQ_MAX_PREF, null);
        String availableFrequenciesLine = Utils.fileReadOneLine(Processor.FREQ_LIST_FILE);
        String availableGovernorsLine = Utils.fileReadOneLine(Processor.GOV_LIST_FILE);
        boolean noSettings = ((availableGovernorsLine == null) || (governor == null)) &&
                             ((availableFrequenciesLine == null) || ((minFrequency == null) && (maxFrequency == null)));
        List<String> frequencies = null;
        List<String> governors = null;

        if (noSettings) {
            Log.d(TAG, "No CPU settings saved. Nothing to restore.");
        } else {
            initFreqCapFiles(ctx);
            if (availableGovernorsLine != null){
                governors = Arrays.asList(availableGovernorsLine.split(" "));
            }
            if (availableFrequenciesLine != null){
                frequencies = Arrays.asList(availableFrequenciesLine.split(" "));
            }
            if (maxFrequency != null && frequencies != null && frequencies.contains(maxFrequency)) {
                Utils.fileWriteOneLine(Processor.FREQ_MAX_FILE, maxFrequency);
            }
            if (minFrequency != null && frequencies != null && frequencies.contains(minFrequency)) {
                Utils.fileWriteOneLine(Processor.FREQ_MIN_FILE, minFrequency);
            }
            if (governor != null && governors != null && governors.contains(governor)) {
                Utils.fileWriteOneLine(Processor.GOV_FILE, governor);
            }
            Log.d(TAG, "CPU settings restored.");
        }
    }

    private void configureIOSched(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        if (prefs.getBoolean(IOScheduler.SOB_PREF, false) == false) {
            Log.i(TAG, "Restore disabled by user preference.");
            return;
        }

        String ioscheduler = prefs.getString(IOScheduler.IOSCHED_PREF, null);
        String availableIOSchedulersLine = Utils.fileReadOneLine(IOScheduler.IOSCHED_LIST_FILE);
        boolean noSettings = ((availableIOSchedulersLine == null) || (ioscheduler == null));
        List<String> ioschedulers = null;

        if (noSettings) {
            Log.d(TAG, "No I/O scheduler settings saved. Nothing to restore.");
        } else {
            if (availableIOSchedulersLine != null){
                ioschedulers = Arrays.asList(availableIOSchedulersLine.replace("[", "").replace("]", "").split(" "));
            }
            if (ioscheduler != null && ioschedulers != null && ioschedulers.contains(ioscheduler)) {
                Utils.fileWriteOneLine(IOScheduler.IOSCHED_LIST_FILE, ioscheduler);
            }
            Log.d(TAG, "I/O scheduler settings restored.");
        }
    }

    private void configureKSM(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        boolean ksm = prefs.getBoolean(MemoryManagement.KSM_PREF, false);

        Utils.fileWriteOneLine(MemoryManagement.KSM_RUN_FILE, ksm ? "1" : "0");
        Log.d(TAG, "KSM settings restored.");
    }

    private static boolean insmod(String module, boolean insert) {
        String command;
    if (insert)
        command = "/system/bin/insmod /system/lib/modules/" + module;
    else
        command = "/system/bin/rmmod " + module;
        try {
            Process process = Runtime.getRuntime().exec("su");
            Log.e(TAG, "Executing: " + command);
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream()); 
            DataInputStream inputStream = new DataInputStream(process.getInputStream());
            outputStream.writeBytes(command + "\n");
            outputStream.flush();
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            process.waitFor();
        }
        catch (IOException e) {
            return false;
        }
        catch (InterruptedException e) {
            return false;
        }
        return true;
    }
}
