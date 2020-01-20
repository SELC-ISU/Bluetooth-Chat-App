package netid.iastate.edu.messenger.Models;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import netid.iastate.edu.messenger.Activities.BluetoothActivity;

import static android.content.ContentValues.TAG;

public class ChatModel {
    private static final String APP_NAME = "388MessengerApp";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter bluetoothAdapter;
    /** this is assigned from a passed in handler from the activity class so callbacks to the UI can occur */
    private final Handler handler;
    /** thread used for accepting BT connections on */
    private AcceptThread acceptThread;
    /** thread used for connecting to other BT devices on */
    private ConnectThread connectThread;
    /** thread used for communicating with other BT devices on */
    private ReadWriteThread connectedThread;
    /** the state of the bluetooth connection from the static values below */
    private int state;

    /** this state is for when the chatModel has been stopped */
    public static final int STATE_NONE = 0;
    /** this state is for when the chatModel is looking for devices to connect with */
    public static final int STATE_LISTEN = 1;
    /** this state is for when the chatModel has started initializing a connection with another BT device */
    public static final int STATE_CONNECTING = 2;
    /** this state is for when the chatModel has established a connection with another BT device */
    public static final int STATE_CONNECTED = 3;

    /**
     * Constructor
     *
     * @param handler passed in from the activity class to be called when certain events happen, so the UI can be updated appropriately
     */
    public ChatModel(Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;

        this.handler = handler;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state one of the static STATE_ variables defined in this class
     */
    private synchronized void setState(int state) {
        this.state = state;

        handler.obtainMessage(BluetoothActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * get current connection state
     * @return the connection state as one of the STATE_ values defined above
     */
    public synchronized int getState() {
        return state;
    }

    /**
     * start service
     */
    public synchronized void start() {
        // Cancel any thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any running threads
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_LISTEN);
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    /**
     * initiate connection to remote device
     * @param device the device to connect this chat session to
     */
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * manage Bluetooth connection
     *
     * @param socket the bluetooth socket for chat communications
     * @param device the remote bluetooth device
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ReadWriteThread(socket);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = handler.obtainMessage(BluetoothActivity.MESSAGE_DEVICE_OBJECT);
        Bundle bundle = new Bundle();
        bundle.putParcelable(BluetoothActivity.DEVICE_OBJECT, device);
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * stop all threads
     */
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Send byte array to the connected bluetooth device
     *
     * @param out the byte array to send
     */
    public void write(byte[] out) {
        ReadWriteThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED)
                return;
            r = connectedThread;
        }
        r.write(out);
    }

    /**
     * This function is called when a bluetooth connection fails. It notifies the user of the problem.
     */
    private void connectionFailed() {
        Message msg = handler.obtainMessage(BluetoothActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // Start the service over to restart listening mode
        ChatModel.this.start();
    }

    /**
     * This function is called when a bluetooth connection is list. It notifies the user of the event.
     */
    private void connectionLost() {
        Message msg = handler.obtainMessage(BluetoothActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // Start the service over to restart listening mode
        ChatModel.this.start();
    }

    /**
     * runs while listening for incoming connections
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        /**
         * constructor
         */
        AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                // use bluetoothAdapter to create a listenUsingInsecureRfcommWithServiceRecord using
                // APP_NAME and MY_UUID and assign the result to the temporary bluetooth socket
                //create a insecure listening BT socket
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            // now assign the temporary bluetooth socket to the bluetooth server socket
            serverSocket = tmp;
        }

        @Override
        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket;
            while (state != STATE_CONNECTED) {
                try {
                    // listen for connections by calling the server socket's accept() method,
                    // and assign the returned BluetoothSocket to socket
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (ChatModel.this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate
                                // new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                }
            }
        }

        /**
         * closes the serverSocket
         */
        void cancel() {
            // - close the serverSocket, will need to be surrounded with a try/catch
            try {
                serverSocket.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * runs while attempting to make an outgoing connection
     */
    private class ConnectThread extends Thread {
        /** The bluetooth socket to the remote device */
        private final BluetoothSocket socket;
        /** The remote device */
        private final BluetoothDevice device;

        /**
         * Constructor
         *
         * @param device the remote device
         */
        ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            this.device = device;

            try {
                //use device to create a RfcommSocketToServiceRecord using MY_UUID and assign
                // the result to the temporary bluetooth socket
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            //: now assign the temporary bluetooth socket to our private final BluetoothSocket
            socket = tmp;
        }

        @Override
        public void run() {
            setName("ConnectThread");

            // - Always cancel the bluetooth adapter discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();
            // Make a connection to the BluetoothSocket
            try {
                socket.connect();//try to connect() the socket
            } catch (IOException e) {
                try {
                    socket.close();//try to close() the socket
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (ChatModel.this) {
                connectThread = null;
            }

            // Start the connected thread
            connected(socket, device);
        }

        /**
         * Closes the connection to the remote device
         */
        void cancel() {
            // close() the socket, will need to be surrounded with a try/catch
            try{
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * runs during a connection with a remote device
     */
    private class ReadWriteThread extends Thread {
        /** The bluetooth socket to the remove device */
        private final BluetoothSocket bluetoothSocket;
        /** The incoming data stream from the remote device */
        private final InputStream inputStream;
        /** The outgoing data stream to the remote device */
        private final OutputStream outputStream;

        /**
         * Constructor
         *
         * @param socket the bluetooth socket to the remote device
         */
        ReadWriteThread(BluetoothSocket socket) {
            this.bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                // use the passed in socket, get the input stream and the output stream and assign them to tmpIn and tmpOut respectively
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // assign the temporary input stream to inputStream
            inputStream = tmpIn;
            //  assign the temporary output stream to outputStream
            outputStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];//stores the stream
            int bytes;

            // Keep listening to the InputStream
            while (true) {
                try {
                    // Read from the InputStream by passing in the buffer and assigning the result to the bytes variable
                    bytes = inputStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    handler.obtainMessage(BluetoothActivity.MESSAGE_READ, bytes, -1,
                            buffer).sendToTarget();
                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    ChatModel.this.start();
                    break;
                }
            }
        }

        /**
         * write to OutputStream
         *
         * @param buffer the byte array to send to the remote device
         */
        void write(byte[] buffer) {
            try {
                // - write the passed in buffer message to outputStream
                outputStream.write(buffer);
                handler.obtainMessage(BluetoothActivity.MESSAGE_WRITE, -1, -1,
                        buffer).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Closes the bluetooth socket
         */
        void cancel() {
            // close() the bluetoothSocket, will need to be surrounded with a try/catch
            try{
                bluetoothSocket.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }
}
