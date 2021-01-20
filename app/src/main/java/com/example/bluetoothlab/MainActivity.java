package com.example.bluetoothlab;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.aware.DiscoverySession;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    final static int REQUEST_ENABLE_BT = 0;
    Spinner pairedDevicesSpinner;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothServerSocket mServerSocket;
    BluetoothDevice targetDevice;
    ToggleButton roleToggleButton;
    Button connectionButton, sendButton;
    TextInputEditText editText;
    ListView historyListView;
    final static UUID MY_UUID = UUID.fromString("37e80028-577d-11eb-ae93-0242ac130002");
    String role = "";
    String targetDeviceDesc = "";
    String msgToSend = "";
    ArrayList<String> messages;
    ArrayAdapter<String> adapter;
    Handler myHandler, buttonHandler;
    ReaderWriterThread reader;
    ServerThread server;
    ClientThread client;


    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChat";



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pairedDevicesSpinner = (Spinner) findViewById(R.id.pairedDevicesSpinner);
        roleToggleButton = (ToggleButton) findViewById(R.id.roleToggleButton);
        connectionButton = (Button) findViewById(R.id.connectionButton);
        sendButton = (Button) findViewById(R.id.sendButton);
        editText = (TextInputEditText) findViewById(R.id.editText);

        historyListView = (ListView) findViewById(R.id.historyListView);
        messages = new ArrayList<>();

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, messages);
        historyListView.setAdapter(adapter);

        mBluetoothAdapter =
                BluetoothAdapter.getDefaultAdapter();

        checkBluetoothIsOn();
        fillPairedDevicesList();


        myHandler = new Handler(){
            @Override
            public void handleMessage(Message status){
                String myMessage = status.obj.toString();
                System.out.println("Handler: I got th message " + myMessage);

                adapter.add(myMessage);
            }
        };

        buttonHandler = new Handler(){
            @Override
            public void handleMessage(Message status){
                sendButton.setEnabled(true);
            }
        };

        connectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                role = (String) roleToggleButton.getText();
                roleToggleButton.setEnabled(false);
                pairedDevicesSpinner.setEnabled(false);
                connectionButton.setEnabled(false);
                connect();
            }
        });

        sendButton.setEnabled(false);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                msgToSend = editText.getText().toString();
                reader.write(msgToSend);
                editText.setText("");
            }
        });

    }

    protected void checkBluetoothIsOn(){
        if (mBluetoothAdapter == null) {
            // Urządzenie nie wspiera technologii Bluetooth
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Your device does not support bluetooth", Toast.LENGTH_SHORT);
            toast.show();
        }
        else if (!mBluetoothAdapter.isEnabled()) {
            // Poproś użytkownika o zgodę na włączenie Bluetooth
            Intent enableBtIntent = new
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

    }

    protected void fillPairedDevicesList(){

        Set<BluetoothDevice> pairedDevices =
                mBluetoothAdapter.getBondedDevices();

        ArrayList<String> pairedDevicesList = new ArrayList<String>();

        System.out.println("paired devices count is " + String.valueOf(pairedDevices.size()));

        for (BluetoothDevice device : pairedDevices) {
            pairedDevicesList.add(device.getName() + "\n[" +
                    device.getAddress() + "]");
            System.out.println(device.getName() + "\n[" +
                    device.getAddress() + "]");
        }

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, pairedDevicesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pairedDevicesSpinner.setAdapter(adapter);

        pairedDevicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

                targetDeviceDesc = arg0.getItemAtPosition(arg2).toString();

                String mac =  targetDeviceDesc.substring(targetDeviceDesc.indexOf('[') +1, targetDeviceDesc.indexOf(']'));
                for (BluetoothDevice device : pairedDevices) {
                     if(device.getAddress().toString().equals(mac.toString())) {
                         targetDevice = device;
                     }
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {}

        });

    }

    protected void connect(){

        if(role.equalsIgnoreCase("Server")){
            System.out.println("Server started to connect");
            server = new ServerThread();
            server.start();
        }
        else if (role.equalsIgnoreCase("Client")){
            System.out.println("Client started to connect");
            client = new ClientThread(targetDevice);
            client.start();
        }

    }



    private class ServerThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public ServerThread() {
            BluetoothServerSocket tmp = null;
            // Create a new listening server socket
            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("NAME", MY_UUID);
            } catch (IOException e) {
                System.out.println("server listen() failed");
            }
            mmServerSocket = tmp;
        }
        public void run() {
            BluetoothSocket socket = null;
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                    System.out.println("listening");
                } catch (Exception e) {
                    System.out.println("Server exception: " + e.getMessage());
                    break;
                }
                if(socket != null){
                    //start to listen and write messages
                    connect(socket);
                    break;
                }

            }
        }
        public void cancel() {
             try {
                mmServerSocket.close();
            } catch (IOException e) {
                System.out.println("close() of server failed");
            }
        }
    }

    void connect(BluetoothSocket socket){
        if(server != null){
            server.cancel();
        }
        Message msg = Message.obtain();
        buttonHandler.sendMessage(msg);
        reader = new ReaderWriterThread(socket);
        reader.start();
    }

    private class ClientThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice mmDevice;

        public ClientThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                System.out.println("client create() failed");
            }
            socket = tmp;
        }
        public void run() {
            System.out.println("BEGIN mConnectThread");
            setName("ConnectThread");
            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();
            // Make a connection to the BluetoothSocket

                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket.connect();
                    System.out.println("Client connected successfully to the server!");
                    //start to listen and sen messages
                    connect(socket);
                    //break;
                } catch (IOException e) {
                    // Close the socket
                    try {
                        socket.close();
                    } catch (IOException e2) {
                        System.out.println("unable to close() socket during connection failure");
                    }

                    return;
                }

        }
        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("close() of connect socket failed");
            }
        }
    }

    private class ReaderWriterThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        String response;
        public ReaderWriterThread(BluetoothSocket socket_) {
            System.out.println("create ReadThread");
            socket = socket_;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();

            } catch (IOException e) {
                System.out.println("temp sockets not created");
            }
            inputStream = tmpIn;
            outputStream = tmpOut;
        }
        public void run() {
            System.out.println("BEGIN mReadThread");
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    int bytes;

                    // Read from the InputStream
                    bytes = inputStream.read(buffer);
                    String str = new String(buffer, StandardCharsets.UTF_8);
                    Message clientMessage = Message.obtain();
                    clientMessage.obj = "Another: " + str;
                    System.out.println("Reader: " + str);
                    myHandler.sendMessage(clientMessage);

                } catch (IOException e) {
                    System.out.println("read disconnected\n" + e.getMessage());
                    break;
                }
            }
        }

        public void write(String msg){

            try{
                byte[] buffer = msg.getBytes(StandardCharsets.UTF_8);
                System.out.println("I started to write the message " + msg);
                outputStream.write(buffer);
                Message clientMessage = Message.obtain();
                clientMessage.obj = "Me: " + msgToSend;
                myHandler.sendMessage(clientMessage);
            } catch (IOException e) {
                System.out.println("Exception during write");
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("close() of connect socket failed");
            }
        }
    }
}