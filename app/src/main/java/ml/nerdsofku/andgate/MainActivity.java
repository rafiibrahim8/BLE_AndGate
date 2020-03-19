package ml.nerdsofku.andgate;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.util.Log.d;
import static android.util.Log.e;

public class MainActivity extends AppCompatActivity {

    private TextView monitor;
    private Spinner spinner;
    private Button buttonConnect;
    private TextView conStat;
    private BluetoothAdapter bluetoothAdapter;
    private String[] btDevices;
    private String remoteAddr, remoteName;
    private AtomicBoolean isConnected;
    private BluetoothGatt bluetoothGatt;
    private String profileSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.blueUnav, Toast.LENGTH_LONG).show();
            return;
        }
        isConnected = new AtomicBoolean(false);
        profileSelected = getResources().getStringArray(R.array.profiles)[0];
        initViews();
    }

    private void initViews() {
        monitor = findViewById(R.id.monitor);
        buttonConnect = findViewById(R.id.btnConnectBT);
        conStat = findViewById(R.id.briefBTConnection);
        spinner = findViewById(R.id.spinner);

        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(this,R.array.profiles,android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                profileSelected  = (String) parent.getItemAtPosition(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isConnected.get()) {
                    if (!bluetoothAdapter.isEnabled()) {
                        showBTAlert();
                        return;
                    }
                    showBTSelect();
                } else {
                    initDisconnect();
                }
            }
        });
    }

    private void showBTSelect() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.bt_select_dialog_layout);
        TextView dialogHead = dialog.findViewById(R.id.dialog_head);
        ListView listView = dialog.findViewById(R.id.dialog_listView);
        if(btDevices==null){
            Toast.makeText(this, R.string.noPaired, Toast.LENGTH_LONG).show();
            return;
        }
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, btDevices);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long id) {
                dialog.dismiss();
                connectBluetooth(btDevices[i].substring(0, btDevices[i].length() - 18), btDevices[i].substring(btDevices[i].length() - 17));
            }
        });
        listView.setAdapter(arrayAdapter);
        dialogHead.setText(R.string.selectBT);
        dialog.show();
    }

    private void connectBluetooth(String name, String addr) {
        remoteAddr = addr;
        remoteName = name;
        new ConnectBT().execute();
    }

    private void showBTAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doBluetoothStuffs();
            }
        });
        alertDialog.setNegativeButton("No", null);
        alertDialog.setMessage(R.string.blueNotOnPromt);
    }

    private void doBluetoothStuffs() {
        if (!bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
        }

        if (!bluetoothAdapter.isEnabled()) {
            showBTAlert();
            return;
        }

        Set<BluetoothDevice> pairedDevices;

        pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() < 1) {
            //Toast.makeText(this, R.string.noPaired, Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<String> deviceNames = new ArrayList<>();
        for (BluetoothDevice dev : pairedDevices) {
            deviceNames.add(dev.getName() + "\n" + dev.getAddress());
        }
        Object[] objects = deviceNames.toArray();
        btDevices = Arrays.copyOf(objects, objects.length, String[].class);

    }

    private void initDisconnect() {
        isConnected.set(false);
        bluetoothGatt.disconnect();

    }


    private class ConnectBT extends AsyncTask<Void, Void, Void> {

        private boolean success;
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            success = true;

            progressDialog = ProgressDialog.show(MainActivity.this, getString(R.string.connecting), getString(R.string.please_wait));
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (!isConnected.get()) {
                success=connectBluetoothDevice();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            if (!success) {
                Toast.makeText(getApplicationContext(), R.string.conFailed, Toast.LENGTH_LONG).show();
            } else {

                isConnected.set(true);
                buttonConnect.setText(R.string.conOK);
                conStat.setTextColor(Color.GREEN);
                conStat.setText(R.string.conOkMsg);
                Toast.makeText(getApplicationContext(), R.string.conOK, Toast.LENGTH_SHORT).show();
            }
            progressDialog.dismiss();
        }
    }

    private boolean connectBluetoothDevice() {
        try {
            BluetoothDevice remoteDev = bluetoothAdapter.getRemoteDevice(remoteAddr);
            //bluetoothAdapter.cancelDiscovery();
            bluetoothGatt = remoteDev.connectGatt(this, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if(newState == BluetoothProfile.STATE_CONNECTED){
                        isConnected.set(true);
                        buttonConnect.setText(R.string.conOK);
                        conStat.setTextColor(Color.GREEN);
                        conStat.setText(R.string.conOkMsg);
                        Toast.makeText(getApplicationContext(), R.string.conOK, Toast.LENGTH_SHORT).show();
                    }
                    else if(newState == BluetoothProfile.STATE_CONNECTING){
                        conStat.setTextColor(Color.YELLOW);
                        conStat.setText(R.string.connecting);
                    }
                    else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                        conStat.setText(R.string.btNotConnectedHint);
                        conStat.setTextColor(Color.RED);
                        buttonConnect.setText(R.string.connectBT);
                    }
                    else if(newState == BluetoothProfile.STATE_DISCONNECTING){
                        conStat.setText(R.string.disconnecting);
                        conStat.setTextColor(Color.YELLOW);
                        buttonConnect.setText(R.string.connectBT);
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    if(status == BluetoothGatt.GATT_SUCCESS){
                        toMonitor(characteristic);
                    }
                    else {
                        Toast.makeText(MainActivity.this,getString(R.string.gatt_read_failed),Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return true;
        } catch (Exception ex) {
            d("EXE",ex.getLocalizedMessage());
            return false;
        }
    }

    private void toMonitor(BluetoothGattCharacteristic characteristic) {
        String[] p= getResources().getStringArray(R.array.profiles);
        if(p[0].equalsIgnoreCase(profileSelected)){
            monitor.append("No profile selected.\n");
        }
        else if(p[1].equalsIgnoreCase(profileSelected)){
            monitor.append(new String(characteristic.getValue())+"\n");
        }
        else if(p[2].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0)+"\n");
        }
        else if(p[3].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,0)+"\n");
        }
        else if(p[4].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32,0)+"\n");
        }
        else if(p[5].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8,0)+"\n");
        }
        else if(p[6].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,0)+"\n");
        }
        else if(p[7].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32,0)+"\n");
        }
        else if(p[8].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_FLOAT,0)+"\n");
        }
        else if(p[9].equalsIgnoreCase(profileSelected)){
            monitor.append(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SFLOAT,0)+"\n");
        }
    }
}
