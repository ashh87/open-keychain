/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.util.LongSparseArray;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ImageView;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.keyimport.ImportKeysListEntry;
import org.sufficientlysecure.keychain.keyimport.Keyserver;
import org.sufficientlysecure.keychain.keyimport.ParcelableKeyRing;
import org.sufficientlysecure.keychain.provider.KeychainContract;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.KeychainIntentService;
import org.sufficientlysecure.keychain.service.KeychainIntentServiceHandler;
import org.sufficientlysecure.keychain.service.results.ImportKeyResult;
import org.sufficientlysecure.keychain.service.results.OperationResult;
import org.sufficientlysecure.keychain.ui.adapter.AsyncTaskResultWrapper;
import org.sufficientlysecure.keychain.ui.adapter.ImportKeysListCloudLoader;
import org.sufficientlysecure.keychain.ui.adapter.ImportKeysListLoader;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.ui.widget.ExchangeKeySpinner;
import org.sufficientlysecure.keychain.ui.widget.KeySpinner;
import org.sufficientlysecure.keychain.util.InputData;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.ParcelableFileCache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import edu.cmu.cylab.starslinger.exchange.ExchangeActivity;
import edu.cmu.cylab.starslinger.exchange.ExchangeConfig;

public class AddKeysActivity extends ActionBarActivity implements
        LoaderManager.LoaderCallbacks<AsyncTaskResultWrapper<ArrayList<ImportKeysListEntry>>> {

    ExchangeKeySpinner mSafeSlingerKeySpinner;
    View mActionSafeSlinger;
    ImageView mActionSafeSlingerIcon;
    View mActionQrCode;
    View mActionSearchCloud;

    ProviderHelper mProviderHelper;

    long mExchangeMasterKeyId = Constants.key.none;

    byte[] mImportBytes;
    private LongSparseArray<ParcelableKeyRing> mCachedKeyData;


    private static final int REQUEST_CODE_SAFE_SLINGER = 1;


    private static final int LOADER_ID_BYTES = 0;
    private static final int LOADER_ID_CLOUD = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProviderHelper = new ProviderHelper(this);

        setContentView(R.layout.add_key_activity);

        mSafeSlingerKeySpinner = (ExchangeKeySpinner) findViewById(R.id.add_keys_safeslinger_key_spinner);
        mActionSafeSlinger = findViewById(R.id.add_keys_safeslinger);
        mActionSafeSlingerIcon = (ImageView) findViewById(R.id.add_keys_safeslinger_icon);
        // make certify image gray, like action icons
        mActionSafeSlingerIcon.setColorFilter(getResources().getColor(R.color.tertiary_text_light),
                PorterDuff.Mode.SRC_IN);
        mActionQrCode = findViewById(R.id.add_keys_qr_code);
        mActionSearchCloud = findViewById(R.id.add_keys_search_cloud);

        mSafeSlingerKeySpinner.setOnKeyChangedListener(new KeySpinner.OnKeyChangedListener() {
            @Override
            public void onKeyChanged(long masterKeyId) {
                mExchangeMasterKeyId = masterKeyId;
            }
        });

        mActionSafeSlinger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startExchange();
            }
        });

        mActionQrCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQrCode();
            }
        });

        mActionSearchCloud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchCloud();
            }
        });

    }

    private void startExchange() {
        if (mExchangeMasterKeyId == 0) {
            Notify.showNotify(this, getString(R.string.select_key_for_exchange),
                    Notify.Style.ERROR);
        } else {
            // retrieve public key blob and start SafeSlinger
            Uri uri = KeychainContract.KeyRingData.buildPublicKeyRingUri(mExchangeMasterKeyId);
            try {
                byte[] keyBlob = (byte[]) mProviderHelper.getGenericData(
                        uri, KeychainContract.KeyRingData.KEY_RING_DATA, ProviderHelper.FIELD_TYPE_BLOB);

                Intent slingerIntent = new Intent(this, ExchangeActivity.class);
                slingerIntent.putExtra(ExchangeConfig.extra.USER_DATA, keyBlob);
                slingerIntent.putExtra(ExchangeConfig.extra.HOST_NAME, Constants.SAFESLINGER_SERVER);
                startActivityForResult(slingerIntent, REQUEST_CODE_SAFE_SLINGER);
            } catch (ProviderHelper.NotFoundException e) {
                Log.e(Constants.TAG, "personal key not found", e);
            }
        }
    }

    private void startQrCode() {

    }

    private void searchCloud() {
        Intent importIntent = new Intent(this, ImportKeysActivity.class);
        startActivity(importIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // if a result has been returned, display a notify
        if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
            OperationResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
            result.createNotify(this).show();
        } else {
            switch (requestCode) {
                case REQUEST_CODE_SAFE_SLINGER:
                    switch (resultCode) {
                        case ExchangeActivity.RESULT_EXCHANGE_OK:
                            // import exchanged keys
                            mImportBytes = getSlingedKeys(data);
                            getSupportLoaderManager().restartLoader(LOADER_ID_BYTES, null, this);
//                            Intent importIntent = new Intent(this, ImportKeysActivity.class);
//                            importIntent.setAction(ImportKeysActivity.ACTION_IMPORT_KEY);
//                            importIntent.putExtra(ImportKeysActivity.EXTRA_KEY_BYTES, getSlingedKeys(data));
//                            startActivity(importIntent);
                            break;
                        case ExchangeActivity.RESULT_EXCHANGE_CANCELED:
                            // handle canceled result
                            // ...
                            break;
                    }
                    break;
            }
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private static byte[] getSlingedKeys(Intent data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Bundle extras = data.getExtras();
        if (extras != null) {
            byte[] d;
            int i = 0;
            do {
                d = extras.getByteArray(ExchangeConfig.extra.MEMBER_DATA + i);
                if (d != null) {
                    try {
                        out.write(d);
                    } catch (IOException e) {
                        Log.e(Constants.TAG, "IOException", e);
                    }
                    i++;
                }
            } while (d != null);
        }

        return out.toByteArray();
    }

    @Override
    public Loader<AsyncTaskResultWrapper<ArrayList<ImportKeysListEntry>>> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_ID_BYTES: {
                InputData inputData = new InputData(new ByteArrayInputStream(mImportBytes), mImportBytes.length);
                return new ImportKeysListLoader(this, inputData);
            }
            case LOADER_ID_CLOUD: {
//                ImportKeysListFragment.CloudLoaderState ls = (ImportKeysListFragment.CloudLoaderState) mLoaderState;
//                return new ImportKeysListCloudLoader(this, ls.mServerQuery, ls.mCloudPrefs);
            }

            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<AsyncTaskResultWrapper<ArrayList<ImportKeysListEntry>>> loader, AsyncTaskResultWrapper<ArrayList<ImportKeysListEntry>> data) {

        Log.d(Constants.TAG, "data: " + data.getResult());

        // swap in the real data!
//        mAdapter.setData(data.getResult());
//        mAdapter.notifyDataSetChanged();
//
//        setListAdapter(mAdapter);
//
//        // The list should now be shown.
//        if (isResumed()) {
//            setListShown(true);
//        } else {
//            setListShownNoAnimation(true);
//        }

        Exception error = data.getError();

        // free old cached key data
        mCachedKeyData = null;


        // TODO: Use parcels!!!!!!!!!!!!!!!
        switch (loader.getId()) {
            case LOADER_ID_BYTES:

                if (error == null) {
                    // No error
                    mCachedKeyData = ((ImportKeysListLoader) loader).getParcelableRings();
                } else if (error instanceof ImportKeysListLoader.NoValidKeysException) {
                    Notify.showNotify(this, R.string.error_import_no_valid_keys, Notify.Style.ERROR);
                } else if (error instanceof ImportKeysListLoader.NonPgpPartException) {
                    Notify.showNotify(this,
                            ((ImportKeysListLoader.NonPgpPartException) error).getCount() + " " + getResources().
                                    getQuantityString(R.plurals.error_import_non_pgp_part,
                                            ((ImportKeysListLoader.NonPgpPartException) error).getCount()),
                            Notify.Style.OK
                    );
                } else {
                    Notify.showNotify(this, R.string.error_generic_report_bug, Notify.Style.ERROR);
                }
                break;

            case LOADER_ID_CLOUD:

                if (error == null) {
                    // No error
                } else if (error instanceof Keyserver.QueryTooShortException) {
                    Notify.showNotify(this, R.string.error_query_too_short, Notify.Style.ERROR);
                } else if (error instanceof Keyserver.TooManyResponsesException) {
                    Notify.showNotify(this, R.string.error_too_many_responses, Notify.Style.ERROR);
                } else if (error instanceof Keyserver.QueryTooShortOrTooManyResponsesException) {
                    Notify.showNotify(this, R.string.error_too_short_or_too_many_responses, Notify.Style.ERROR);
                } else if (error instanceof Keyserver.QueryFailedException) {
                    Log.d(Constants.TAG,
                            "Unrecoverable keyserver query error: " + error.getLocalizedMessage());
                    String alert = this.getString(R.string.error_searching_keys);
                    alert = alert + " (" + error.getLocalizedMessage() + ")";
                    Notify.showNotify(this, alert, Notify.Style.ERROR);
                }
                break;

            default:
                break;
        }

        importKeys();
    }

    @Override
    public void onLoaderReset(Loader<AsyncTaskResultWrapper<ArrayList<ImportKeysListEntry>>> loader) {
        switch (loader.getId()) {
            case LOADER_ID_BYTES:
                // Clear the data in the adapter.
//                mAdapter.clear();
                break;
            case LOADER_ID_CLOUD:
                // Clear the data in the adapter.
//                mAdapter.clear();
                break;
            default:
                break;
        }
    }

    public ParcelableFileCache.IteratorWithSize<ParcelableKeyRing> getSelectedData() {
        return new ParcelableFileCache.IteratorWithSize<ParcelableKeyRing>() {
            int i = 0;

            @Override
            public int getSize() {
                return mCachedKeyData.size();
            }

            @Override
            public boolean hasNext() {
                return (mCachedKeyData.get(i + 1) != null);
            }

            @Override
            public ParcelableKeyRing next() {
                ParcelableKeyRing key = mCachedKeyData.get(i);
                i++;
                return key;
            }

            @Override
            public void remove() {
                mCachedKeyData.remove(i);
            }
        };
    }

    public void importKeys() {
        // Message is received after importing is done in KeychainIntentService
        KeychainIntentServiceHandler saveHandler = new KeychainIntentServiceHandler(
                this,
                getString(R.string.progress_importing),
                ProgressDialog.STYLE_HORIZONTAL,
                true) {
            public void handleMessage(Message message) {
                // handle messages by standard KeychainIntentServiceHandler first
                super.handleMessage(message);

                if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {
                    // get returned data bundle
                    Bundle returnData = message.getData();
                    if (returnData == null) {
                        return;
                    }
                    final ImportKeyResult result =
                            returnData.getParcelable(OperationResult.EXTRA_RESULT);
                    if (result == null) {
                        Log.e(Constants.TAG, "result == null");
                        return;
                    }

//                    if (ACTION_IMPORT_KEY_FROM_KEYSERVER_AND_RETURN_RESULT.equals(getIntent().getAction())
//                            || ACTION_IMPORT_KEY_FROM_FILE_AND_RETURN.equals(getIntent().getAction())) {
//                        Intent intent = new Intent();
//                        intent.putExtra(ImportKeyResult.EXTRA_RESULT, result);
//                        ImportKeysActivity.this.setResult(RESULT_OK, intent);
//                        ImportKeysActivity.this.finish();
//                        return;
//                    }
//                    if (ACTION_IMPORT_KEY_FROM_KEYSERVER_AND_RETURN_TO_SERVICE.equals(getIntent().getAction())) {
//                        ImportKeysActivity.this.setResult(RESULT_OK, mPendingIntentData);
//                        ImportKeysActivity.this.finish();
//                        return;
//                    }

                    result.createNotify(AddKeysActivity.this).show();
                }
            }
        };

//        ImportKeysListFragment.LoaderState ls = mListFragment.getLoaderState();
//        if (importBytes != null) {
            Log.d(Constants.TAG, "importKeys started");

            // Send all information needed to service to import key in other thread
            Intent intent = new Intent(this, KeychainIntentService.class);

            intent.setAction(KeychainIntentService.ACTION_IMPORT_KEYRING);

            // fill values for this action
            Bundle data = new Bundle();

            // get DATA from selected key entries
//            ParcelableFileCache.IteratorWithSize<ParcelableKeyRing> selectedEntries = mListFragment.getSelectedData();

            // instead of giving the entries by Intent extra, cache them into a
            // file to prevent Java Binder problems on heavy imports
            // read FileImportCache for more info.
            try {
                // We parcel this iteratively into a file - anything we can
                // display here, we should be able to import.
                ParcelableFileCache<ParcelableKeyRing> cache =
                        new ParcelableFileCache<ParcelableKeyRing>(this, "key_import.pcl");
                cache.writeCache(getSelectedData());

                intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

                // Create a new Messenger for the communication back
                Messenger messenger = new Messenger(saveHandler);
                intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

                // show progress dialog
                saveHandler.showProgressDialog(this);

                // start service with intent
                startService(intent);
            } catch (IOException e) {
                Log.e(Constants.TAG, "Problem writing cache file", e);
                Notify.showNotify(this, "Problem writing cache file!", Notify.Style.ERROR);
            }
//        } else if (ls instanceof ImportKeysListFragment.CloudLoaderState) {
//            ImportKeysListFragment.CloudLoaderState sls = (ImportKeysListFragment.CloudLoaderState) ls;
//
//            // Send all information needed to service to query keys in other thread
//            Intent intent = new Intent(this, KeychainIntentService.class);
//
//            intent.setAction(KeychainIntentService.ACTION_DOWNLOAD_AND_IMPORT_KEYS);
//
//            // fill values for this action
//            Bundle data = new Bundle();
//
//            data.putString(KeychainIntentService.DOWNLOAD_KEY_SERVER, sls.mCloudPrefs.keyserver);
//
//            // get selected key entries
//            ArrayList<ImportKeysListEntry> selectedEntries = mListFragment.getSelectedEntries();
//            data.putParcelableArrayList(KeychainIntentService.DOWNLOAD_KEY_LIST, selectedEntries);
//
//            intent.putExtra(KeychainIntentService.EXTRA_DATA, data);
//
//            // Create a new Messenger for the communication back
//            Messenger messenger = new Messenger(saveHandler);
//            intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);
//
//            // show progress dialog
//            saveHandler.showProgressDialog(this);
//
//            // start service with intent
//            startService(intent);
//        } else {
//            Notify.showNotify(this, R.string.error_nothing_import, Notify.Style.ERROR);
//        }
    }
}