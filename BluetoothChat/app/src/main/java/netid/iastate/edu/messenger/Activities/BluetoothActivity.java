package netid.iastate.edu.messenger.Activities;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import netid.iastate.edu.messenger.Models.ChatModel;
import netid.iastate.edu.messenger.R;

public class BluetoothActivity extends AppCompatActivity {

    /** text view that displays the status of the BT connection */
    private TextView status;
    /** Button user clicks in order to show BT devices to connect to */
    private Button btnConnect;
    /** list that is populated with the two people chatting back and forth */
    private ListView listView;
    /** dialog used for showing BT device options to choose from to the user */
    private Dialog dialog;
    /** this is where the user enters their message into */
    private EditText inputField;
    /** Array adapter for putting chat messages sent and received into the list view */
    private ArrayAdapter<String> chatAdapter;
    /** array list of messages sent and received */
    private ArrayList<String> chatMessages;
    /** the devices BT adapter used for BT connections */
    private BluetoothAdapter bluetoothAdapter;

    //these final variables are used in handleMessage of the Handler to see why the handler was used and perform the resulting action of the call
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;

    /** static variable used to get the device name attribute of the connectingDevice */
    public static final String DEVICE_OBJECT = "device_name";

    /** request code used for startActivityForResult and onActivityResult */
    private static final int SPEECH_REQUEST_CODE = 0;
    /** request code used for startActivityForResult and onActivityResult */
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    /** this class performs the connections, reads, and writes for the BT messaging */
    private ChatModel chatModel;
    /** this is the device that is being talked to */
    private BluetoothDevice connectingDevice;
    /** array adapter that helps display previously discovered devices in a list to the user when they have clicked on the connect button */
    private ArrayAdapter<String> discoveredDevicesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_bluetooth);
        findViewsByIds();

        //check device support bluetooth or not
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }

        //show bluetooth devices dialog when click connect button
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPickDialog();
            }
        });

        //set chat adapter
        chatMessages = new ArrayList<>();
        chatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatMessages);
        listView.setAdapter(chatAdapter);
    }

    /**
     * Click event handler for Voice To Text button click.
     * @param view the instance of the voice to text button
     */
    public void voiceToTextClicked(View view){
        // -  create and use an intent for speech to text
        // Create an intent that can start the Speech Recognizer activity
        // Start the activity, the intent will be populated with the speech text
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        //startActivity(intent);
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }


    /**
     * This handler is used when Messages are sent to it. It checks if the BT connection state has
     * changed, if a write just happened from this device, if a read just happened from this device,
     * a connection just happened to another BT device, and if a Toast should be displayed to the user
     */
    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatModel.STATE_CONNECTED:
                            setStatus("Connected to: " + connectingDevice.getName());
                            btnConnect.setEnabled(false);
                            break;
                        case ChatModel.STATE_CONNECTING:
                            setStatus("Connecting...");
                            btnConnect.setEnabled(false);
                            break;
                        case ChatModel.STATE_LISTEN:
                        case ChatModel.STATE_NONE:
                            setStatus("Not connected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;

                    String writeMessage = new String(writeBuf);
                    chatMessages.add("Me: " + writeMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf, 0, msg.arg1);
                    chatMessages.add(connectingDevice.getName() + ":  " + readMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    /**
     * Creates a dialog with a list of discovered bluetooth devices.
     */
    private void showPickDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        //Initializing bluetooth adapters
        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        //locate listviews and attach the adapters
        ListView listView = dialog.findViewById(R.id.pairedDeviceList);
        ListView listView2 = dialog.findViewById(R.id.discoveredDeviceList);
        listView.setAdapter(pairedDevicesAdapter);
        listView2.setAdapter(discoveredDevicesAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryFinishReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesAdapter.add("No devices have been paired");
        }

        //Handling listview item click event
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }

        });

        listView2.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    /**
     * This method sets the status text view on the screen to display the current BT connection status
     * @param s is the String that is passed to be displayed that tells the current state of the BT connection
     */
    private void setStatus(String s) {
        status.setText(s);
    }

    /**
     * This method is called when a device to connect to has been decided. It cancels its BT
     * discovery to save power, and start a chatModel connection with the device associated
     * with the passed in address
     * @param deviceAddress address of the device to connect to
     */
    private void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        chatModel.connect(device);
    }

    /**
     * Find all of the views by ID and stores them into global variables for use throughout the class
     */
    private void findViewsByIds() {
        status = findViewById(R.id.status);
        btnConnect = findViewById(R.id.btn_connect);
        listView = findViewById(R.id.list);
        inputField = findViewById(R.id.input_layout);
    }

    /**
     * Button click event handler for Send with Bluetooth
     * @param view the Send with Bluetooth button
     */
    public void sendWithBT(View view){
        //checks to see if the user has entered any text to send
        if (inputField.getText().toString().equals("")) {
            Toast.makeText(BluetoothActivity.this, "Please input some text", Toast.LENGTH_SHORT).show();
        } else {
            //sends the inputField text and then resets the inputField to an empty string
            sendMessage(inputField.getText().toString());
            inputField.setText("");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK) {
                    chatModel = new ChatModel(handler);
                } else {
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
                // - use the SPEECH_REQUEST_CODE to check if a speech to text result has been returned,
                // if it has then get the result text and put it into the inputField
            case SPEECH_REQUEST_CODE:
                if(Activity.RESULT_OK == resultCode){
                    List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String spokenText = results.get(0);
                    inputField.setText(spokenText);
                }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Sends a string via bluetooth using the ChatModel
     * @param message the message to send
     */
    private void sendMessage(String message) {
        //check to see if the chat model is still connected to another device
        if (chatModel.getState() != ChatModel.STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }
        //if there is a message to send, then send it
        if (message.length() > 0) {
            byte[] send = message.getBytes();
            chatModel.write(send);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        //if BT is not enabled on the device, then request that the user enables it
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            //BT is enabled on the device, so create a new chatModel instance
            chatModel = new ChatModel(handler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //if we have a chatModel and the chat model has been stopped, then start it
        if (chatModel != null) {
            if (chatModel.getState() == ChatModel.STATE_NONE) {
                chatModel.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // if we have a chat model, then stop it
        if(chatModel != null) {
            if (chatModel.getState() == ChatModel.STATE_CONNECTED) {
                chatModel.stop();
            }
        }
    }

    /**
     * This method is used once scanning for BT devices finished or a device is discovered
     */
    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {//Remote device discovered
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {//The local Bluetooth adapter has finished the device discovery process.
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add("No devices found");
                }
            }
        }
    };
}