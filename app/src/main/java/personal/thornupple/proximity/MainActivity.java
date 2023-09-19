package personal.thornupple.proximity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity
{
	final static String TAG = MainActivity.class.getSimpleName();

	private ProxBLE mProxBLE_Service;
	private ProximityDataClass mProx_DataClass;

	private static final long SCAN_PERIOD = 15000;

	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothLeScanner mBluetoothLeScanner;

	private String mstrRemoteDeviceName;
	private String mstrRemoveDeviceAddress;

	private boolean mScanning = false;
	private boolean isSubscribed = false;
	private boolean misServiceBound = false;

	/*******************************************
	 /***** MAKE GATT UPDATE INTENT FILTER ****
	 /*******************************************
	 RETURNS:  Intent Filter used by the broadcast
	 /*******************************************/
	private static IntentFilter makeGattUpdateIntentFilter()
	{
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ProxBLE.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(ProxBLE.ACTION_GATT_CONNECTED);
		intentFilter.addAction(ProxBLE.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(ProxBLE.ACTION_GATT_SERVICES_DISCOVERED);
		return intentFilter;
	}

	/********************************************************
	 /***** BROADCAST RECEIVER OBJECT ****
	 /********************************************************
	 RETURNS:  Broadcast Receiver using the previous intent
	 /********************************************************/
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
	{
		@SuppressLint("DefaultLocale")
		@Override
		public void onReceive(Context context, Intent intent)
		{
			final String action = intent.getAction();
			Log.i(TAG, "\nBroadcast Receiver got the following action: " + action);
			if (ProxBLE.ACTION_DATA_AVAILABLE.equals(action))
			{
				final byte[] data = intent.getByteArrayExtra(ProxBLE.EXTRA_DATA);
				String characteristicReadResponse = "Raw Data Read: " + Arrays.toString(data);
				Log.i(TAG, characteristicReadResponse);

				mProx_DataClass.PlayTone(data);

				int LEFT = 2;
				int CENTER = 1;
				int RIGHT = 0;
				UpdateDistances(data[LEFT], data[CENTER], data[RIGHT]);

				if (!isSubscribed)
				{
					mProxBLE_Service.setCharacteristicNotification();
					isSubscribed = true;
				}
			}
			else if (ProxBLE.ACTION_GATT_CONNECTED.equals(action))
			{
				GiveUserFeedback("Gatt is Connected", null, true);
			}
			else if (ProxBLE.ACTION_GATT_DISCONNECTED.equals(action))
			{
				GiveUserFeedback("Gatt is Disconnected", null, true);
			}
			else if (ProxBLE.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
			{
				GiveUserFeedback("Services have been Discovered", null, true);
			}
		}
	};

	/********************************************************
	 /***** SERVICE CONNECTION OBJECT ****
	 /********************************************************
	 RETURNS:  Service Connection
	 /********************************************************/
	private final ServiceConnection mServiceConnection = new ServiceConnection()
	{

		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			mProxBLE_Service = ((ProxBLE.LocalBinder) service).getService();
			mProxBLE_Service.Initialize();
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			mProxBLE_Service = null;
		}
	};

	/********************************************************
	 /***** ON CREATE ****
	 /********************************************************
	 This is where it all starts so create stuff
	 /********************************************************/
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Log.i(TAG, "onCreateCalled");

		mProx_DataClass = new ProximityDataClass();

		Intent gattServiceIntent = new Intent(this, ProxBLE.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		Log.i(TAG, "Service is now Bound");
		misServiceBound = true;

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

		PopulateViews();

		GiveUserFeedback("One Minute Scan for Proximity Device",null, true);

		// CHECK BLUETOOTH AND OTHER PERMISSIONS //
		boolean bail = false;

		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			Toast.makeText(this, "Bluetooth LE is required by this app and is not supported on this device.  Try on another device..", Toast.LENGTH_SHORT).show();
			finish();
		}

		// these two lines go together
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())
		{
			Toast.makeText(this, "Bluetooth is OFF.  Turn on Bluetooth and Try Again.", Toast.LENGTH_SHORT).show();
			bail = true;
		}
		else if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
		{
			Toast.makeText(this, "Application needs Location and Nearby Devices Permissions.", Toast.LENGTH_SHORT).show();
			bail = true;
		}

		if (bail)
		{
			PauseMe();
			finish();
		}
		else
		{
			mScanning = true;
			ScanBleDevice();
		}
	}

	/********************************************************
	 /***** SCAN CALLBACK OBJECT ****
	 /********************************************************
	 RETURNS:  Scan Callback
	 /********************************************************/
	private final ScanCallback mLeScanCallback = new ScanCallback()
	{
		// Since we only have one device and service, we call connect here instead of having the user do it
		@SuppressLint("MissingPermission")
		@Override
		public void onScanResult(int callbackType, ScanResult result)
		{
			super.onScanResult(callbackType, result);
			mScanning = false;

			mstrRemoteDeviceName = result.getScanRecord().getDeviceName();
			mstrRemoveDeviceAddress = result.getDevice().getAddress();
			String feedbackText = String.format("Device Address:\t\t%s\r\n\r\nDevice Name:\t\t\t%s", mstrRemoveDeviceAddress, mstrRemoteDeviceName);

			// update the user interface
			//SetScanFeedback("Scan Succeeded.  Full Result: " + result, null, false);
			GiveUserFeedback(feedbackText, "Close", true);
			getSupportActionBar().setSubtitle("Device Found!");

			Log.i(TAG, "Scan Result Record: " + result.getScanRecord().toString());

			// for most bluetooth apps, we'd present a list of matching devices and let them choose and then the service would connect to them
			// however, we are scanning for only one device and will connect to it immediately.
			// todo:  if we find other devices are out there; we can create our own unique service uuid and use that; or use the mac address of our adapter

			if (mProxBLE_Service != null && misServiceBound)
			{
				mProxBLE_Service.Connect(mstrRemoveDeviceAddress);
			}
		}

		@Override
		public void onBatchScanResults(List<ScanResult> results)
		{
			super.onBatchScanResults(results);
			Log.i(TAG, "Scan Batch Results Not Handled");

		}

		@Override
		public void onScanFailed(int errorCode)
		{
			super.onScanFailed(errorCode);
			Log.i(TAG, "Scan Failed Results Not Handled");
		}

	};

	/********************************************************
	 /***** SCAN BLUETOOTH LE DEVICE FOUND ****
	 /********************************************************/
	private void ScanBleDevice()
	{
		// we use these to determine if correct device and if connected
		// todo: if we connect to wrong device, need to add in the specific device address to connect with
		mstrRemoteDeviceName = null;
		mstrRemoveDeviceAddress = null;
		mScanning = false;

		if (mBluetoothLeScanner == null)
			mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

		mScanning = true;
		GiveUserFeedback("Scanning for Devices that Match the Profile", null, false);
		// check for permissions
		if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
		{
			// Permissions not allowed - notify and bail
			Toast.makeText(this, "Bluetooth Scan Permissions Missing.  Set permissions and try again", Toast.LENGTH_LONG).show();
			return;
		}

		// SET A DEFAULT TIMEOUT
		new Handler(Looper.getMainLooper()).postDelayed(() -> {
			if (mScanning)
			{
				GiveUserFeedback("No Devices Found that Match the Profile.", "Scan Again", true);

				mScanning = false;
				mBluetoothLeScanner.stopScan(mLeScanCallback);
				Log.d(TAG, "Handler/Looper fired.  Stopping Scanning.");
				return;
			}
			if (mstrRemoveDeviceAddress == null)
				GiveUserFeedback("Search for Device Timed Out.", "Scan Again", true);
		}, SCAN_PERIOD);

		Log.d(TAG, "Start Scanning for BLE Devices");

		// SCAN FILTERS
		List<ScanFilter> filters = new ArrayList<>();

		ScanFilter filter = new ScanFilter.Builder()
				.setServiceUuid(new ParcelUuid(ProxBLE.ENVIRONMENTAL_SENSING_SERVICE))
				.build();

		filters.add(filter);

		// SCAN SETTINGS
		ScanSettings settings = new ScanSettings.Builder()
				.setLegacy(false)
				.setMatchMode(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
				.setReportDelay(0)
				.build();

		mBluetoothLeScanner.startScan(filters, settings, mLeScanCallback);
	}

	/********************************************************
	 /***** SECTION:  USER INTERFACE STUFF
	 /********************************************************/

	private Button btnChangeAction;
	private TextView tvCenter;
	private TextView tvRight;
	private TextView tvLeft;

	void PopulateViews()
	{
		tvLeft = findViewById(R.id.lblLeft);
		tvRight = findViewById(R.id.lblRight);
		tvCenter = findViewById(R.id.lblCenter);
		btnChangeAction = findViewById(R.id.btnAction);

		// when we start - we set the text to "Scan Again"
		btnChangeAction.setEnabled(false);

		// after we get the device, we change it to "Close"
		// this is the default which is "scan again"
		btnChangeAction.setOnClickListener(v -> ScanBleDevice());
	}

	// this will update all the distanced returned over bluetooth
	@SuppressLint("DefaultLocale")
	void UpdateDistances(Byte left, Byte center, Byte right)
	{
		tvLeft.setText(String.format(  "Left Distance  \n\t\t\t%dcm",left));

		tvCenter.setText(String.format("Center Distance \n\t\t\t%dcm",center));

		tvRight.setText(String.format( "Right Distance \n\t\t\t%dcm",right));
	}
	// will give user feedback for various settings;
	// this will only update the subtitle and user feedback
	void GiveUserFeedback(String labelText, String buttonText, boolean isbuttonEnabled)
	{
		runOnUiThread(() -> {
			// todo: put strings in resource
			btnChangeAction.setEnabled(isbuttonEnabled);

			// we don't want to change values if the new values are null
			if (buttonText != null)
			{
				btnChangeAction.setText(buttonText);
				if (buttonText.equals("Close"))
				{
					btnChangeAction.setOnClickListener(v -> {
						finish();
						System.exit(0);
					});
				}
			}
			if (labelText != null)
			{
				getSupportActionBar().setSubtitle(labelText);
			}
		});
	}

	void PauseMe()
	{
		try
		{
			Thread.sleep(2000);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if (mProxBLE_Service != null)
		{
			mProxBLE_Service.disconnect();
			mProxBLE_Service.close();
			unbindService(mServiceConnection);
			mProxBLE_Service = null;
		}
		mProx_DataClass.stopToneThread();
	}
}