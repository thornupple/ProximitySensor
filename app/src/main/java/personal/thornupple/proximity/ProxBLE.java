package personal.thornupple.proximity;


import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.UUID;


public class ProxBLE extends Service
{

	// these strings are rather random - they just allow the main app to know what the status is/are
	public final static String ACTION_GATT_CONNECTED = "thornupple.prox.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "thornupple.prox.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "thornupple.prox.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE = "thornupple.prox.ACTION_DATA_AVAILABLE";
	public final static String EXTRA_DATA = "thornupple.prox.EXTRA_DATA";

	// this is used in the subscription in the setCharacteristicNotification function. Can't find much documentation so this is one of those
	// it works in the samples, it works here, so just use it
	public final static UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	// we COULd generate our own UUID for service and characteristic and that would obviate the potential problem of the client picking up
	// other devices that broadcast the following.  But, this is so unlikely, then why bother?  Just use these from the BT SIG
	public final static UUID ENVIRONMENTAL_SENSING_SERVICE = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb");
	private final static UUID LOCATION_AND_SPEED_CHARACTERISTIC = UUID.fromString("00002a67-0000-1000-8000-00805f9b34fb");

	// for logging information
	private static final String TAG = ProxBLE.class.getSimpleName ();

	// module level stuff
	private final IBinder mBinder = new LocalBinder ();
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt mBluetoothGatt;
	private BluetoothGattCharacteristic mProxBluetoothGattCharacteristic;

	// since this is the first occurrence of the suppression of this permission, let's touch on why...
	// when the app starts, we check the permissions from the Main Activity code and if the permissions
	// are missing, we just alert the user and let them set them manually.  If this were a commercial
	// we would do this programmatically
	/*******************************************
	 /***** INITIALIZE ****
	/*******************************************/
	@SuppressLint("MissingPermission")
	public void Initialize()
	{
		Log.d(TAG, "Entered Initialize in ProxBLE service.");

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBluetoothAdapter == null)
		{
			Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
		}
	}

	/*******************************************
	 /***** CONNECT ****
	/*******************************************/
	@SuppressLint("MissingPermission")
	public void Connect(String deviceAddress)
	{
		Log.d(TAG, "Entered Connect in ProxBLE service.");
		if (deviceAddress == null || mBluetoothAdapter == null)
		{
			Log.e(TAG, "Either the device Address or Adapter are null, Cannot continue.");
			return;
		}

		// from the docs:
		/* 	Once the BluetoothService is initialized, it can connect to the BLE device.
			The activity needs to send the device address to the service so it can initiate the connection.
			The service will first call getRemoteDevice() on the BluetoothAdapter to access the device.
			If the adapter is unable to find a device with that address, getRemoteDevice() throws an IllegalArgumentException.
		 */
		try
		{
			final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
			mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		}
		catch (IllegalArgumentException exception)
		{
			Log.w(TAG, "Device not found with provided address.");
		}
	}


	/*******************************************
	***** BLUETOOTH GATT CALLBACK ****
	/*******************************************/
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback ()
	{

		/***************************************************
		/***** BLUETOOTH GATT: CONNECTION STATE CHANGED****
		/**************************************************/
		@SuppressLint("MissingPermission")
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
		{
			Log.i(TAG, "onConnectionChanged.");

			if (newState == BluetoothProfile.STATE_CONNECTED)
			{
				Log.i(TAG, "Connected to GATT server.");
				broadcastUpdate(ACTION_GATT_CONNECTED);
				mBluetoothGatt.discoverServices();
			}
			else if (newState == BluetoothProfile.STATE_DISCONNECTED)
			{
				Log.i(TAG, "Disconnected from GATT server.");
				broadcastUpdate(ACTION_GATT_DISCONNECTED);
			}
		}
		/***************************************************
		/***** BLUETOOTH GATT: ON SERVICE DISCOVERED ****
		/**************************************************/
		@SuppressLint("MissingPermission")
		@Override
		public void onServicesDiscovered (BluetoothGatt gatt, int status)
		{
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}

			if (status == BluetoothGatt.GATT_SUCCESS)
			{
				Log.w (TAG, "Services Discovered, connecting to Proximity  service...");

				BluetoothGattService environmentalSensingService = gatt.getService (ENVIRONMENTAL_SENSING_SERVICE);

				mProxBluetoothGattCharacteristic = environmentalSensingService.getCharacteristic (LOCATION_AND_SPEED_CHARACTERISTIC);

				gatt.readCharacteristic (mProxBluetoothGattCharacteristic);
			}
		}

		/***************************************************
		/***** BLUETOOTH GATT: ON CHARACTERISTIC READ****
		/****************************************************************************************************************
		* ok, this is now deprecated but still works and the docs are missing on how to implement the new api, so just
		* leave it as is
		****************************************************************************************************************/
		@Override
		public void onCharacteristicRead (BluetoothGatt gatt,
										  BluetoothGattCharacteristic characteristic,
										  int status)
		{
			if (status == BluetoothGatt.GATT_SUCCESS)
			{
				broadcastUpdate( characteristic);
			}
		}

		/*******************************************************
		/***** BLUETOOTH GATT: ON CHARACTERISTIC CHANGED *****
		/****************************************************************************************************************
		 * ok, this is now deprecated but still works and the docs are missing on how to implement the new api, so just
		 * leave it as is
		 ****************************************************************************************************************/
		@Override
		public void onCharacteristicChanged (BluetoothGatt gatt,
											 BluetoothGattCharacteristic characteristic)
		{
			broadcastUpdate (characteristic);
		}
	};
	/******** END BLUETOOTH GATT CALLBACK FUNCTIONS *****

	/*******************************************
	/***** SET CHARACTERISTIC NOTIFICATION  ****
	*******************************************
	this is just a function in this class; but "subscribes to notifications from the
	bluetooth gatt service running on the remote server... in this case the microcontroller
	/*******************************************/
	@SuppressLint("MissingPermission")
	public void setCharacteristicNotification()
	{
		if (mBluetoothGatt == null)
		{
			Log.w(TAG, "BluetoothGatt not initialized");
			return;
		}
		mBluetoothGatt.setCharacteristicNotification(mProxBluetoothGattCharacteristic, true);

		BluetoothGattDescriptor descriptor = mProxBluetoothGattCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG); // not sure where this comes from but the sample used it so let's try it

		descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

		mBluetoothGatt.writeDescriptor(descriptor);
	}

	/*******************************************
	/***** BROADCAST UPDATE  ****
	*******************************************
	* OVERLOADED
	*******************************************/
	private void broadcastUpdate(final String action)
	{
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
	}
	/*******************************************
	/***** BROADCAST UPDATE  ****
	*******************************************
	*  OVERLOADED
	*******************************************/
	@SuppressLint("MissingPermission")
	private void broadcastUpdate(final BluetoothGattCharacteristic characteristic)
	{
		final Intent intent = new Intent(ProxBLE.ACTION_DATA_AVAILABLE);

		if (LOCATION_AND_SPEED_CHARACTERISTIC.equals(characteristic.getUuid()))
		{
			final byte[] data = characteristic.getValue();
			intent.putExtra(EXTRA_DATA,data);
			sendBroadcast(intent);
			return;
		}
		sendBroadcast(intent);
	}

	/*******************************************
	/***** CLASS:  LOCAL BINDER  ****
	*******************************************/
	public class LocalBinder extends Binder
	{
		ProxBLE getService()
		{
			return ProxBLE.this;
		}
	}


	/*******************************************
	/***** ON BIND  ****
 	*******************************************/
	@Nullable
	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	/*******************************************
	/***** ON UN BIND  ****
	*******************************************/
	@Override
	public boolean onUnbind (Intent intent)
	{
		// After using a given device, you should make sure that BluetoothGatt.close() is called
		// such that resources are cleaned up properly.  In this particular example, close() is
		// invoked when the UI is disconnected from the Service.
		close ();
		return super.onUnbind (intent);
	}
	/*******************************************
 	/***** ON CLOSE  ****
	*******************************************/
	@SuppressLint("MissingPermission")
	void close ()
	{
		if (mBluetoothGatt == null)
		{
			return;
		}
		mBluetoothGatt.close ();
		mBluetoothGatt = null;
	}
	/*******************************************
	/***** ON DISCONNECT  ****
	***********************************************************************************************
	* Disconnects an existing connection or cancel a pending connection. The disconnection result
	* is reported asynchronously through the callback.
	***********************************************************************************************/
	@SuppressLint("MissingPermission")
	public void disconnect ()
	{
		if (mBluetoothAdapter == null || mBluetoothGatt == null)
		{
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}
}
