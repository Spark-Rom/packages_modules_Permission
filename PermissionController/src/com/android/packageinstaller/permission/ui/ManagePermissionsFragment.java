/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.packageinstaller.permission.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.ArraySet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.PmCache;
import com.android.packageinstaller.permission.model.PermissionGroup;
import com.android.packageinstaller.permission.model.PermissionGroups;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.List;

public final class ManagePermissionsFragment extends PreferenceFragment
        implements PermissionGroups.PermissionsGroupsChangeCallback, OnPreferenceClickListener {
    private static final String LOG_TAG = "ManagePermissionsFragment";

    private static final String OS_PKG = "android";

    private static final String EXTRA_PREFS_KEY = "extra_prefs_key";

    private ArraySet<String> mLauncherPkgs;

    private PermissionGroups mPermissions;

    private PreferenceScreen mExtraScreen;

    private boolean mShowLegacyPermissions;

    public static ManagePermissionsFragment newInstance() {
        return new ManagePermissionsFragment();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
        mLauncherPkgs = Utils.getLauncherPackages(getContext());
        mPermissions = new PermissionGroups(getActivity(), getLoaderManager(), this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPermissions.refresh();
        updatePermissionsUi();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.toggle_legacy_permissions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.toggle_legacy_permissions);
        if (!mShowLegacyPermissions) {
            item.setTitle(R.string.show_legacy_permissions);
        } else {
            item.setTitle(R.string.hide_legacy_permissions);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                getActivity().finish();
                return true;
            }

            case R.id.toggle_legacy_permissions: {
                mShowLegacyPermissions = !mShowLegacyPermissions;
                updatePermissionsUi();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        PermissionGroup group = mPermissions.getGroup(key);
        if (group == null) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                .putExtra(Intent.EXTRA_PERMISSION_NAME, key);
        try {
            getActivity().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(LOG_TAG, "No app to handle " + intent);
        }

        return true;
    }

    @Override
    public void onPermissionGroupsChanged() {
        updatePermissionsUi();
    }

    private void updatePermissionsUi() {
        Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        List<PermissionGroup> groups = mPermissions.getGroups();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(activity);
            setPreferenceScreen(screen);
        }

        // Use this to speed up getting the info for all of the PermissionApps below.
        // Create a new one for each refresh to make sure it has fresh data.
        PmCache cache = new PmCache(getContext().getPackageManager());
        for (PermissionGroup group : groups) {
            // Show legacy permissions only if the user chose that.
            if (!mShowLegacyPermissions && group.getDeclaringPackage().equals(OS_PKG)
                    && !Utils.isModernPermissionGroup(group.getName())) {
                continue;
            }

            Preference preference = findPreference(group.getName());
            if (preference == null && mExtraScreen != null) {
                preference = mExtraScreen.findPreference(group.getName());
            }
            if (preference == null) {
                preference = new Preference(activity, null);
                preference.setOnPreferenceClickListener(this);
                preference.setKey(group.getName());
                preference.setIcon(Utils.applyTint(activity, group.getIcon(),
                        android.R.attr.colorControlNormal));
                preference.setTitle(group.getLabel());
                // Set blank summary so that no resizing/jumping happens when the summary is loaded.
                preference.setSummary(" ");
                preference.setPersistent(false);
                if (group.getDeclaringPackage().equals(OS_PKG)) {
                    screen.addPreference(preference);
                } else {
                    if (mExtraScreen == null) {
                        mExtraScreen = getPreferenceManager().createPreferenceScreen(activity);
                    }
                    mExtraScreen.addPreference(preference);
                }
            }
            final Preference finalPref = preference;

            new PermissionApps(getContext(), group.getName(), new PermissionApps.Callback() {
                @Override
                public void onPermissionsLoaded(PermissionApps permissionApps) {
                    if (getActivity() == null) {
                        return;
                    }
                    int granted = permissionApps.getGrantedCount(mLauncherPkgs);
                    int total = permissionApps.getTotalCount(mLauncherPkgs);
                    finalPref.setSummary(getString(R.string.app_permissions_group_summary,
                            granted, total));
                }
            }, cache).refresh(false);
        }

        if (mExtraScreen != null && mExtraScreen.getPreferenceCount() > 0
                && screen.findPreference(EXTRA_PREFS_KEY) == null) {
            Preference extraScreenPreference = new Preference(activity);
            extraScreenPreference.setKey(EXTRA_PREFS_KEY);
            extraScreenPreference.setIcon(R.drawable.ic_toc);
            extraScreenPreference.setTitle(R.string.additional_permissions);
            extraScreenPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AdditionalPermissionsFragment frag = new AdditionalPermissionsFragment();
                    frag.setTargetFragment(ManagePermissionsFragment.this, 0);
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(android.R.id.content, frag);
                    ft.addToBackStack("AdditionalPerms");
                    ft.commit();
                    return true;
                }
            });
            extraScreenPreference.setSummary(getString(R.string.additional_permissions_more,
                    mExtraScreen.getPreferenceCount()));
            screen.addPreference(extraScreenPreference);
        }
    }

    public static class AdditionalPermissionsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            getActivity().setTitle(R.string.additional_permissions);
            setPreferenceScreen(((ManagePermissionsFragment) getTargetFragment()).mExtraScreen);
            setHasOptionsMenu(true);
        }

        @Override
        public void onDestroy() {
            getActivity().setTitle(R.string.app_permissions);
            super.onDestroy();
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    getFragmentManager().popBackStack();
                    return true;
            }
            return super.onOptionsItemSelected(item);
        }

    }
}
