package com.mohsenoid.androidthingsble

import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
import android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Bundle
import android.os.ParcelUuid
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity() {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private lateinit var gattServer: BluetoothGattServer
    private val registeredDevices = ArrayList<BluetoothDevice>()
    private var currentCounterValue by Delegates.observable(0) { _, _, newValue ->
        runOnUiThread { tvCounter.text = newValue.toString() }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Timber.i("LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            Timber.w("LE Advertise Failed: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            if (CHARACTERISTIC_COUNTER_UUID == characteristic.uuid) {
                val value = currentCounterValue.toByteArray()
                gattServer.sendResponse(device, requestId, GATT_SUCCESS, 0, value)
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            if (CHARACTERISTIC_INTERACTOR_UUID == characteristic.uuid) {
                currentCounterValue++
                notifyRegisteredDevices()
            }
        }

        private fun notifyRegisteredDevices() {
            val characteristic = gattServer
                    .getService(SERVICE_UUID)
                    .getCharacteristic(CHARACTERISTIC_COUNTER_UUID)

            for (device in registeredDevices) {
                val value = currentCounterValue.toByteArray()
                characteristic.value = value
                gattServer.notifyCharacteristicChanged(device, characteristic, false)
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (DESCRIPTOR_CONFIG_UUID == descriptor.uuid) {
                if (Arrays.equals(ENABLE_NOTIFICATION_VALUE, value)) {
                    registeredDevices.add(device)
                } else if (Arrays.equals(DISABLE_NOTIFICATION_VALUE, value)) {
                    registeredDevices.remove(device)
                }

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        context = applicationContext

        setupTimber()

        setupBLE()

        startBLEService()
    }

    private fun setupTimber() {
        Timber.plant(object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement): String {
                // adding file name and line number link to logs
                return String.format(Locale.US, "%s(%s:%d)", super.createStackElementTag(element), element.fileName, element.lineNumber)
            }
        })
    }

    private fun setupBLE() {
        // The BluetoothAdapter is required for any and all Bluetooth activity.
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
                .apply {
                    name = "Things"
                }

        // Some advertising settings. We don't set an advertising timeout
        // since our device is always connected to AC power.
        val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

        // Defines which service to advertise.
        val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

        // Starts advertising.
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun startBLEService() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                .apply {
                    addService(createService())
                }
    }

    private fun createService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_UUID, SERVICE_TYPE_PRIMARY)

        // Counter characteristic (read-only, supports subscriptions)
        val counter = BluetoothGattCharacteristic(CHARACTERISTIC_COUNTER_UUID, PROPERTY_READ or PROPERTY_NOTIFY, PERMISSION_READ)
        val counterDescriptor = BluetoothGattDescriptor(DESCRIPTOR_CONFIG_UUID, PERMISSION_READ or PERMISSION_WRITE)
        counter.addDescriptor(counterDescriptor)

        // Interactor characteristic
        val interactor = BluetoothGattCharacteristic(CHARACTERISTIC_INTERACTOR_UUID, PROPERTY_WRITE_NO_RESPONSE, PERMISSION_WRITE)

        service.addCharacteristic(counter)
        service.addCharacteristic(interactor)
        return service
    }

    fun Int.toByteArray() = ByteArray(0).plus(toByte())

    companion object {
        private val SERVICE_UUID = UUID.fromString("795090c7-420d-4048-a24e-18e60180e23c")
        private val CHARACTERISTIC_COUNTER_UUID = UUID.fromString("31517c58-66bf-470c-b662-e352a6c80cba")
        private val CHARACTERISTIC_INTERACTOR_UUID = UUID.fromString("0b89d2d4-0ea6-4141-86bb-0c5fb91ab14a")

        private val DESCRIPTOR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
