package dr.kompass.hwrbluetoothle;

/*
* Dieser einfache Code stellt die Bluetooth-Verbindung zu Relais der chinesischen Firma DSD Tech
* @see http://www.dsdtech-global.com/2018/04/bluetooth-relay-module.html
* her und steuert das Ein- und Ausschalten des Relais (auf Kanal 1).
* */

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.List;
import java.util.UUID;

public class BLEActivity extends AppCompatActivity {

    private static final int ENABLE_BLUETOOTH = 1;


    private static final String TAG = BLEActivity.class.getSimpleName();
    private static final int REQUEST_ACCESS_COARSE_LOCATION = 321;

    private boolean mConnected;

    private Handler handler= new Handler();
    private Runnable runnableRelaySwichOff = new Runnable() {
        @Override
        public void run() {
            relaySwitchOff();
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(BLEActivity.this,
                    getString(R.string.error, errorCode), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            findRelay(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                findRelay(result);
            }
        }
    };


    public final static UUID UUID_NOTIFY = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");

    private byte[] bytesOn = new byte[]{(byte) 0xA0, (byte) 0x01, (byte) 0x01, (byte) 0xA2};// "A00101A2"
    private byte[] bytesOff = new byte[]{(byte) 0xA0, (byte) 0x01, (byte) 0x00, (byte) 0xA1};// "A00100A1"

    private TextView textView;
    private ToggleButton toggleButton;
    private Switch aSwitch;
    private Button buttonKurzerTon;
    private Button buttonLangerTon;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice deviceDSDRelay;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, final int status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(textView.getText() + "Service discovered:" + status + " ");
                }
            });
            Log.d(TAG,"Service discovered:" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                findService(gatt.getServices());
            }
        }

        public void findService(List<BluetoothGattService> gattServices) {
            for (BluetoothGattService gattService : gattServices) {
                if(gattService.getUuid().toString().equalsIgnoreCase(UUID_SERVICE.toString())) {
                    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                        if(gattCharacteristic.getUuid().toString().equalsIgnoreCase(UUID_NOTIFY.toString())) {
                            mNotifyCharacteristic = gattCharacteristic;
                            gatt.setCharacteristicNotification(mNotifyCharacteristic, true);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    textView.setText(textView.getText() + "Service und Charakteristik gefunden. ");
                                    aSwitch.setEnabled(true);// 'Schalter jetzt erst aktivieren!
                                    buttonKurzerTon.setEnabled(true);
                                    buttonLangerTon.setEnabled(true);
                                }
                            });

                            Log.d(TAG,"Service und Charakteristik gefunden.");

                        }
                    }
                }
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            mConnected = false;

            if (status == BluetoothGatt.GATT_FAILURE) {  // Andrew Lunsford, Bluetooth Low Energy on Android Part 1 https://www.bignerdranch.com/blog/bluetooth-low-energy-part-1/
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText("Verbindung zu 'DSD Relay' unterbrochen (GATT_FAILURE).");
//                resetButtons();
                }
                });
                Log.d(TAG,"Verbindung zu 'DSD Relay' unterbrochen (GATT_FAILURE).");
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText("Verbindung zu 'DSD Relay' unterbrochen (NOT GATT_SUCCESS).");
                        resetButtons();
                        toggleButton.setEnabled(false);
                    }
                });
                Log.d(TAG,"Verbindung zu 'DSD Relay' unterbrochen (NOT GATT_SUCCESS).");

                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(textView.getText() + "Mit 'DSD Relay' verbunden. Suche Service und Charakteristik.");
                    }
                });
                Log.d(TAG,"Mit 'DSD Relay' verbunden. Suche Service und Charakteristik.");
                mConnected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText("Verbindung zu 'DSD Relay' beendet.");
//                resetButtons();
                    }
                });
                Log.d(TAG,"Verbindung zu 'DSD Relay' beendet.");
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble);

        textView = findViewById(R.id.textView);
        textView = findViewById(R.id.textView);
        toggleButton = findViewById(R.id.toggleButton);
        aSwitch = findViewById(R.id.switch1);
        buttonKurzerTon =findViewById(R.id.button_kurzer_ton);
        buttonLangerTon =findViewById(R.id.button_langer_ton);

        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                textView.setText(textView.getText() + "Connected: " + mConnected);
                Log.d(TAG,"Connected: " + mConnected);
                if (isChecked) {
                    relaySwitchOn();
                } else {
                    relaySwitchOff();
                }
            }
        });

        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    connectRelay();
                } else {
                    disconnectRelay();
                }
            }
        });

        buttonKurzerTon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                relaySwitchOn();
                handler.postDelayed(runnableRelaySwichOff, 600);
            }
        });

        buttonLangerTon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                relaySwitchOn();
                handler.postDelayed(runnableRelaySwichOff, 1200);
            }
        });
    }



    @Override
    protected void onStart() {
        super.onStart();
        bluetoothAdapter = null;
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            textView.setText("Um Bluetooth und Relais nutzen zu könnten, müssen Sie den Zugriff auf den Standort erlauben.");
            requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_COARSE_LOCATION);
        } else {
            checkBluetoothEnabled();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        if (bluetoothAdapter != null) {
            scan(false);
        }
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
        }
    }


// Bluetooth bereit?
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        textView.setText("...");
        if ((requestCode == REQUEST_ACCESS_COARSE_LOCATION) &&
                (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            checkBluetoothEnabled();
        } else {
            Toast.makeText(this, "Das Relais für das Signalhorn wird nicht genutzt, weil Bluetooth mangels Berechtigung zum Zugriff auf den Standtort nicht funktioniert.",
                    Toast.LENGTH_LONG).show();

            textView.setText("...");


        }
    }

    private void checkBluetoothEnabled() {
        if (isBluetoothEnabled()) scan(true);
    }

    private boolean isBluetoothEnabled() {
        boolean enabled = false;
        final BluetoothManager m = getSystemService(BluetoothManager.class);
        if (m != null) {
            bluetoothAdapter = m.getAdapter();
            if (bluetoothAdapter != null) {
                enabled = bluetoothAdapter.isEnabled();
                if (!enabled) {
                    textView.setText("Um das Relais für das Signalhorn steuern zu könnten, muss Bluetooth eingeschaltet sein.");
                    /**
                     * Meier, Reto. Android App-Entwicklung: Die Gebrauchsanleitung für Programmierer (German Edition) (Kindle-Positionen18677-18678). Wiley. Kindle-Version.
                     * Listing 16-2: Enabling Bluetooth
                     */
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intent, ENABLE_BLUETOOTH);
                }
            } else{
                Toast.makeText(this, "Ihr Smartphone unterstützt Bluetooth leider nicht.", Toast.LENGTH_LONG).show();

            }
        }
        return enabled;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENABLE_BLUETOOTH)
            textView.setText("...");
            if (resultCode == RESULT_OK) {
                // Bluetooth has been enabled, initialize the UI.
                scan(true);
            } else {
                Toast.makeText(this, "Das Relais für das Signalhorn wird nicht genutzt, weil Bluetooth nicht eingeschaltet worden ist.", Toast.LENGTH_LONG).show();

            }
    }


// DSD Relay suchen
    private void scan(boolean onOff) {
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (onOff) {
            scanner.startScan(scanCallback);
            textView.setText(textView.getText() + "Suche nach 'DSD Relay' ist gestartet. ");
            Log.d(TAG,"Suche nach 'DSD Relay' ist gestartet.");
        } else {
            scanner.stopScan(scanCallback);
            textView.setText(textView.getText() + "Suche nach 'DSD Relay' wird beendet.");
            Log.d(TAG,"Suche nach 'DSD Relay' wird beendet.");
        }

    }



    private void findRelay(ScanResult result) {
        String name = result.getDevice().getName();
        if (name != null) {
            if (name.equals("DSD Relay")) {
                textView.setText(textView.getText() + "'DSD Relay' gefunden. ");
                Log.d(TAG,"'DSD Relay' gefunden.");

                // Verbindung zu DSD Relay ist möglich
                deviceDSDRelay = result.getDevice();
                toggleButton.setEnabled(true);

                // Scan beenden
                scan(false); //erst, wenn die Verbindung hergestellt ist?
            }
        }
    }



// Verbindung zu DSD Relay herstellen oder beenden
    private void connectRelay() {
        if (deviceDSDRelay != null) {
            gatt = deviceDSDRelay.connectGatt(this,true, gattCallback);
//            aSwitch.setEnabled(true);  Fehler, wenn zu früh geschaltet wird, weil Verbindung noch nicht steht
            scan(false);
        }
    }

    private void disconnectRelay() {
        resetButtons();
        if (gatt != null) {
            relaySwitchOff();
            gatt.disconnect();
            gatt.close();
            scan(true);
        }
    }

    private void resetButtons() {
        aSwitch.setEnabled(false);
        aSwitch.setChecked(false);
        buttonKurzerTon.setEnabled(false);
        buttonLangerTon.setEnabled(false);
//        toggleButton.setEnabled(false);
        toggleButton.setChecked(false);
    }


// DSD Relay schalten
    private void relaySwitchOn() {
        mNotifyCharacteristic.setValue(bytesOn);
        gatt.writeCharacteristic(mNotifyCharacteristic);
    }

    private void relaySwitchOff() {
        mNotifyCharacteristic.setValue(bytesOff);
        gatt.writeCharacteristic(mNotifyCharacteristic);
    }
}
