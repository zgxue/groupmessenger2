package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    //begin xue *****************************************************************************//
    static final String TAG = GroupMessengerActivity.class.getSimpleName();

    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";


    static final int SERVER_PORT = 10000;

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    private ContentResolver contentResolver;
    private Uri uri;

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    ///end ************************************************************************************///
    ///begin new local variables***************************************************************///






    //end ************************************************************************************///

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */

        //begin xue *****************************************************************************//

        //assign var
        contentResolver = getContentResolver();
        uri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
//        sequence = 0;


        //Calculate the port number that this AVD listens on.
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        // new a ServerTask()
        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        }catch (IOException e){
            Log.e(TAG, "Can't create a ServerSocket");
        }
        ///end *********************************************************************************///

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        //begin xue *****************************************************************************//
        final EditText editText = (EditText) findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText("");

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, REMOTE_PORT0);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, REMOTE_PORT1);
//                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, REMOTE_PORT2);
//                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, REMOTE_PORT3);
//                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, REMOTE_PORT4);
                //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);


            }
        });
        ///end *********************************************************************************///

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////    ServerTask      /////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////


    /// begin xue **************************//
    public class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        int sequence = 0;

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];
            try {
                //test for timeoutException
//                serverSocket.setSoTimeout(10000);

                BufferedReader bufferedReader = null;

                while(true) {
                    Socket socket_sv = serverSocket.accept();

                    socket_sv.setSoTimeout(500);

                    bufferedReader = new BufferedReader(new InputStreamReader(socket_sv.getInputStream()));

                    String content;
                    while ((content = bufferedReader.readLine()) != null) {
                        this.publishProgress(content);
                    }
                    socket_sv.close();
                }
            }catch (SocketTimeoutException e){
                e.getMessage();
                Log.e(TAG, "There is a SocketTimeOutException!");

            }
            catch (Exception e) {
                Log.e(TAG, "pass bufferedReader.readline to onProgressUpdate");
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String...strings) {

            String strReceived = strings[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(strReceived + "\n");

            ContentValues contentValues = new ContentValues();
            contentValues.put(KEY_FIELD, Integer.toString(sequence));
            contentValues.put(VALUE_FIELD, strReceived);
            contentResolver.insert(uri,contentValues);

            sequence = sequence +1;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////    ClientTask      /////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////

    public class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {

//                Log.e(TAG,"I AM IN Client doInBackground");
                String msgToSend = msgs[0];
                String portToSend = msgs[1];

//                Log.e(TAG, "portTdSend: " + portToSend);
                Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(portToSend));

                // send messages
                OutputStream outputStream;
                try{
                    outputStream = socket0.getOutputStream();

//                    Thread.sleep(1000);

                    outputStream.write(msgToSend.getBytes());
                }catch(Exception e){
                    Log.e(TAG, "outputstream write");
                }

                socket0.close();

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
    ///end *******************************************************///

}
