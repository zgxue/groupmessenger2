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
import android.util.Pair;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IllegalFormatCodePointException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    //begin xue *****************************************************************************//
    static final String TAG = GroupMessengerActivity.class.getSimpleName();


    static final String[] REMOTE_PORTS = new String[]{"11108","11112","11116","11120","11124"};
//    static final String[] REMOTE_PORTS = new String[]{"11108"};
//    static final String[] REMOTE_PORTS = new String[]{"11108","11112"};


    static final int SERVER_PORT = 10000;
    static final int TIMEOUT = 1400;

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

//    private HashMap<String, Socket> groupSockets;

    private String selfMachineName;  //these two can combine to generate the UniqueId of msg, such as 5554_2.
//    private HashMap<String, Boolean> machineStatus = new HashMap<String, Boolean>(); //initial with all 1, and 0 if one machine is down.
    private static AtomicBoolean[] machineStatus = new AtomicBoolean[]{
        new AtomicBoolean(true),
        new AtomicBoolean(true),
        new AtomicBoolean(true),
        new AtomicBoolean(true),
        new AtomicBoolean(true)
    };

    private HashMap<String, Integer> fifoSequences = new HashMap<String, Integer>();// <#p1, #p2, #p3, #p4, #p5>
    private Integer maxAgreedSeq = 0;  //maximum observed agreed sequence
    private Integer maxProposedSeq = 0; //maximum proposed sequence by myself

    private LinkedList<Integer[]> fifoQueue = new LinkedList<Integer[]>();
    private LinkedList<TOQueueItem> totalQueue = new LinkedList<TOQueueItem>();

//    private Integer counter = 0;
//    private Integer siProposedSeq  = 0;
    private static AtomicInteger counter = new AtomicInteger(0);
    private static AtomicInteger siProposedSeq  = new AtomicInteger(0);




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

        //Calculate the port number that this AVD listens on.
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        //initialization
        selfMachineName = myPort;




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

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);

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

        int sequenceInDB = 0;
//        HashMap<String, Socket> machineSockets = new HashMap<String, Socket>();
//        Socket[] machineSockets = {null, null, null, null, null};

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];

            try {
                //test for timeoutException
                // use concurrent threadpool to avoid too many thread created in the same time

                ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
//                ExecutorService fixedThreadPool = Executors.newFixedThreadPool(30);

                while(true) {
                    Socket socket_sv = serverSocket.accept();

//                    fixedThreadPool.execute(new AsServer(socket_sv));
                    cachedThreadPool.execute(new AsServer(socket_sv));
//                    Thread t0 = new Thread(new AsServer(socket_sv));
//                    t0.start();

                }
            } catch (Exception e) {
                Log.e(TAG, "error bufferedReader"+e.getMessage());
            }
            return null;
        }

        class AsServer implements Runnable{
            private Socket socket_accepted;
            String socketRemotePort;

            AsServer(Socket sckt){
                this.socket_accepted = sckt;
                socketRemotePort = "";
            }

            public void run() {
                String[] cntnt;
                Scanner scanner;
                try{
//                    socket_accepted.setSoTimeout(TIMEOUT);
                    scanner = new Scanner(socket_accepted.getInputStream());
                    String tmp;
                    long startTime = System.currentTimeMillis();
                    while ((System.currentTimeMillis() - startTime) < TIMEOUT){
                        if (scanner.hasNext())
                            break;

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e3) {
                            e3.printStackTrace();
                        }
                    }
                    if (scanner.hasNext()) {
                        tmp = scanner.nextLine();
                        cntnt = tmp.split(" ", 3);
                    }else{
                        try{
                            socket_accepted.close();
                        }catch(IOException e2){
                            Log.e(TAG, e2.getMessage());
                        }
                        return;
                    }

//                    socket_accepted.setSoTimeout(0);

                }catch (SocketTimeoutException e){
                    try{
                        socket_accepted.close();
                    }catch(IOException e2){
                        Log.e(TAG, e2.getMessage());
                    }
                    return;
                } catch (Exception e){
                    Log.e(TAG, e.getMessage());
                    return;
                }
                logPrint("[Svr] Just got first MSG");

                socketRemotePort = cntnt[1];
//                    machineSockets.put(socketRemotePort, socket_accepted);
//                    machineSockets[getIndexbyStrPort(socketRemotePort)] = socket_accepted;
                Boolean d1Bool = BDeliver1(cntnt[0], cntnt[1], cntnt[2]);
                if (!d1Bool)
                    return;

                try {
//                    socket_accepted.setSoTimeout(TIMEOUT);
                    scanner = new Scanner(socket_accepted.getInputStream());
                    String tmp;
                    long startTime = System.currentTimeMillis();
                    while ((System.currentTimeMillis() - startTime) < TIMEOUT){
                        if (scanner.hasNext())
                            break;

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e3) {
                            e3.printStackTrace();
                        }
                    }
                    if (scanner.hasNext()){
                        tmp = scanner.nextLine();
                        cntnt = tmp.split(" ");
//                    socket_accepted.setSoTimeout(0);
                    } else{
                        logPrint("Timeout!!!!!!!!!!!");
                        setFalseStatustoMachinebyName(socketRemotePort);
                        cleanUp(socketRemotePort);

                        try{
                            socket_accepted.close();
                        }catch(IOException ee){
                            Log.e(TAG, ee.getMessage());
                        }
                        return;
                    }

                } catch (SocketTimeoutException e){
                    Log.e(TAG, "There is a SocketTimeOutException!"+e.getMessage());

                    setFalseStatustoMachinebyName(socketRemotePort);
                    cleanUp(socketRemotePort);

                    try{
                        socket_accepted.close();
                    }catch(IOException ee){
                        Log.e(TAG, ee.getMessage());
                    }
                    return;
                } catch (Exception e){
                    Log.e(TAG, e.getMessage());
                    return;
                }

                logPrint("[Srv] Just got the agreed Seq.");

                BDeliver2(cntnt[0], cntnt[1], cntnt[2], cntnt[3]);
//                    machineSockets.remove(socketRemotePort);

                try{
                    socket_accepted.close();
                }catch(IOException ee){
                    Log.e(TAG, ee.getMessage());
                }


            }
            private boolean BDeliver1(String mid, String jProc, String msg){
//            siProposedSeq += 1;
                siProposedSeq.incrementAndGet();

                String toSendStr = mid + " " + siProposedSeq.toString();

//            Socket toSendSocket = machineSockets[getIndexbyStrPort(jProc)];
                try{
                    OutputStream outputStream = socket_accepted.getOutputStream();
                    outputStream.write(toSendStr.getBytes());
                    outputStream.flush();
                    totalQueue.add(new TOQueueItem(mid, jProc, siProposedSeq.toString(), selfMachineName, false, msg));
                    organizeTotalQueue();
                    return true;
                } catch (IOException e){
                    Log.e(TAG, e.getMessage());
                    setFalseStatustoMachinebyName(jProc);
                    try{
                        socket_accepted.close();
                    }catch (IOException e2){
                        Log.e(TAG, e2.getMessage());
                    }
                    return false;
                }
            }

            private void BDeliver2(String mid, String iProc, String sk, String kProc){


                try{
                    Integer skInteger = Integer.parseInt(sk);
//                siProposedSeq = (siProposedSeq > skInteger)? siProposedSeq : skInteger;

                    if (skInteger > siProposedSeq.get()){
                        siProposedSeq.set(skInteger);
                    }
                    for (int i = 0; i < totalQueue.size(); i++) {
                        TOQueueItem item = totalQueue.get(i);
                        if (item.mID.equals(mid) && item.jProcSentMsg.equals(iProc)) {
                            //change the proposed sequence number to sk
                            item.sProposedSeq = sk;

                            //change process that suggested sequence number to k
                            item.kProcProposing = kProc;

                            //change undeliverable to deliverable
                            item.status = true;

//                            totalQueue.set(i, item);
//                            organizeTotalQueue();
                            setTotalQueue(i, item);
                            break;
                        }
                    }

                }catch (Exception e){
                    Log.e(TAG, e.getMessage());
                }
            }

        }

        @Override
        protected void onProgressUpdate(String...strings) {

            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(strings[1] + "\n");

            ContentValues contentValues = new ContentValues();
            contentValues.put(KEY_FIELD, String.valueOf(sequenceInDB));
            contentValues.put(VALUE_FIELD, strings[1]);
            contentResolver.insert(uri,contentValues);
            sequenceInDB += 1;
        }


        private synchronized void organizeTotalQueue(){  // previously there is not synchronized.
            Collections.sort(totalQueue, new ComparatorTOQueueItem());
            while (!totalQueue.isEmpty() && totalQueue.peekFirst().status){
                publishProgress(totalQueue.peekFirst().sProposedSeq, totalQueue.peekFirst().msg);
                totalQueue.removeFirst();
            }
        }

        private synchronized void setTotalQueue(int i, TOQueueItem item){
            totalQueue.set(i, item);
            organizeTotalQueue();
        }

        public synchronized void cleanUp(String machineCrashed){
            ListIterator<TOQueueItem> it = totalQueue.listIterator();
            while (it.hasNext()){
                TOQueueItem item = it.next();
                if (item.jProcSentMsg.equals(machineCrashed)){
                    it.remove();
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////    ClientTask      /////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////

    public class ClientTask extends AsyncTask<String, Void, Void> {

//        Socket[] socketsSV = new Socket[REMOTE_PORTS.length];
        Socket[] socketsSV;
        ArrayList<Pair<String, String>> pairProposedSeqList = new ArrayList<Pair<String, String>>();

        @Override
        protected Void doInBackground(String... msgs) {

//            counter += 1;
            counter.incrementAndGet();
            socketsSV = BMulticastMSG(counter.toString(), selfMachineName, msgs[0]);
            logPrint("just finish BmulticastMSG");

            //recieve information regarding all 5or4 proposed sequences
            for (int i = 0; i < socketsSV.length; i++) {
                if (!getMachineStatusByIndex(i) || socketsSV[i] == null){
                    logPrint("Jump because of the socket==null");
                    continue;
                }
                Socket socket_each = socketsSV[i];

                try {

                    socket_each.setSoTimeout(TIMEOUT);

                    Scanner scanner = new Scanner(socket_each.getInputStream());
                    logPrint("Start to process of socket "+i+".... Waiting scanner.hasNext....");

                    long startTime = System.currentTimeMillis();
                    while ((System.currentTimeMillis() - startTime) < TIMEOUT){
                        if (scanner.hasNext())
                            break;

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e3) {
                            e3.printStackTrace();
                        }
                    }
                    if (scanner.hasNext()){
                        logPrint("Scanner got something");
                        String[] cntnt = scanner.nextLine().split(" ");

                        pairProposedSeqList.add(new Pair<String, String>(cntnt[1], REMOTE_PORTS[i]));
                    }else{
                        logPrint("Scanner Timeout!");
                        setFalseStatustoMachinebyIndex(i);
                        try{ // close the socket that waiting too long.
                            if (socketsSV[i] != null){
                                socketsSV[i].close();
                            }
                        }catch (IOException e2){
                            Log.e(TAG, e2.getMessage());
                        }
                        socketsSV[i] = null;
                    }

                    socket_each.setSoTimeout(0);


                } catch (SocketTimeoutException e){
                    logPrint("Catch SocketTimeoutException loop["+i+"]");
                    Log.e(TAG, e.getMessage());
                    setFalseStatustoMachinebyIndex(i);
                    try{ // close the socket that waiting too long.
                        if (socketsSV[i] != null){
                            socketsSV[i].close();
                        }
                    }catch (IOException e2){
                        Log.e(TAG, e2.getMessage());
                    }
                    socketsSV[i] = null;

                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }

            logPrint("[Client] just finish collect feedbacks");

            //find the highest sequence number
            Integer skmax = 0;
            String kProc = "";
            for (Pair<String, String> pair_each:pairProposedSeqList){
                Integer newSk = Integer.parseInt(pair_each.first);
                if (newSk > skmax){
                    skmax = newSk;
                    kProc = pair_each.second;
                }else if(newSk.equals(skmax) &&  pair_each.second.compareTo(kProc) < 0){
                    kProc = pair_each.second;
                }
            }
            logPrint("[Client] just finish picking up the max sequence proposed");

            BMulticastAgreedSeq(counter.toString(), selfMachineName, skmax.toString(), kProc);
            logPrint("[Client] just finish multicast the agreed seq");

            // close all sockets
            for (int i = 0; i < socketsSV.length; i++) {
                if (socketsSV[i] == null){
                    continue;
                }
                try {
                    socketsSV[i].close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }

            return null;
        }

        private Socket[] BMulticastMSG(String cnteri, String proci, String content){
            Socket[] retSockets = {null, null, null, null, null};
            String strToSend = cnteri + " " + proci + " " + content;

            for (int i = 0; i < REMOTE_PORTS.length; i++) {
                if (!getMachineStatusByIndex(i)){
                    continue;
                }
                try {
                    String portToSend = REMOTE_PORTS[i];

                    Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(portToSend));

                    retSockets[i] = socket0;

//                    socket0.setSoTimeout(TIMEOUT);

                    // send messages
                    OutputStream outputStream;
                    outputStream = socket0.getOutputStream();
                    outputStream.write((strToSend).getBytes());
                    outputStream.flush();

                }catch (SocketException e){
                    Log.e(TAG, "Client: Cannot newly connect to remote macheine"+e.getMessage());
                    setFalseStatustoMachinebyIndex(i);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException"+e.getMessage());
                }
            }

            return retSockets;
        }

        private void BMulticastAgreedSeq(String mid, String proci, String sk, String prock){

            String strToSend = mid + " " + proci + " " + sk + " " + prock;

            for (int i = 0; i < socketsSV.length; i++) {
                if (!getMachineStatusByIndex(i) || socketsSV[i] == null){
                    continue;
                }
                OutputStream outputStream;
                try {
                    outputStream = socketsSV[i].getOutputStream();
                    outputStream.write(strToSend.getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    Log.e(TAG, "BMulticastAgreedSeq send error"+e.getMessage());
//                    setFalseStatustoMachinebyIndex(i);
                }finally {
                    try{
                        if(socketsSV[i] != null){
                            socketsSV[i].close();
                        }
                    }catch(IOException e2){
                        Log.e(TAG, e2.getMessage());
                    }
                }
            }
        }

    }

    /////////////////////////  Class for parameters delivery    ///////////////////////////////
/*    public class ParaClient{
        String msgUniqueID;  // like "5554_1"
        String msg;
        HashMap<String, Socket> groupSockets;
        HashMap<String, Boolean> machineStatus;

        public ParaClient(String msgUniqueID, String msg, HashMap<String, Socket> groupSockets, HashMap<String, Boolean> machineStatus){
            this.msgUniqueID = msgUniqueID;
            this.msg = msg;
            this.groupSockets = groupSockets;
            this.machineStatus = machineStatus;
        }
    }*/

    public class TOQueueItem{
        String mID;
        String jProcSentMsg;
        String sProposedSeq;
        String kProcProposing;
        Boolean status;
        String msg;

        public TOQueueItem(String mID, String jProcSentMsg, String sProposedSeq, String kProcProposing, Boolean status,String msg){
            this.mID = mID;
            this.jProcSentMsg = jProcSentMsg;
            this.sProposedSeq = sProposedSeq;
            this.kProcProposing = kProcProposing;
            this.status = status;
            this.msg = msg;
        }
    }
    public class ComparatorTOQueueItem implements Comparator<TOQueueItem>{
        // How to use:
        // Comparator<User> cmp = new ComparatorUser();
        //Collections.sort(userlist, cmp);
        @Override
        public int compare(TOQueueItem lhs, TOQueueItem rhs) {
            int flag = Integer.parseInt(lhs.sProposedSeq) - (Integer.parseInt(rhs.sProposedSeq));
            if (flag == 0){
                return lhs.kProcProposing.compareTo(rhs.kProcProposing);
            }
            return flag;
        }
    }

    public void logPrint(String information){
        Log.e(TAG, information);
    }

/*    public synchronized boolean getMachineStatusByIndex(int index){
        return machineStatus.get(REMOTE_PORTS[index]);
    }
    public synchronized boolean getMachineStatusByName(String name){
        return machineStatus.get(name);
    }
    public synchronized void setFalseStatustoMachinebyIndex(int index){
        machineStatus.remove(REMOTE_PORTS[index]);
        machineStatus.put(REMOTE_PORTS[index], false);
    }
    public synchronized void setFalseStatustoMachinebyName(String name){
        machineStatus.remove(name);
        machineStatus.put(name, false);
    }*/
    public boolean getMachineStatusByIndex(int index){
        return machineStatus[index].get();
    }
    public boolean getMachineStatusByName(String name){
        return machineStatus[(Integer.parseInt(name) - 11108)/4].get();
    }
    public void setFalseStatustoMachinebyIndex(int index){
        machineStatus[index].set(false);
    }
    public void setFalseStatustoMachinebyName(String name){
        machineStatus[(Integer.parseInt(name) - 11108)/4].set(false);
    }
    public int getIndexbyStrPort(String name){
        return (Integer.parseInt(name) - 11108)/4;
    }




    ///end *******************************************************///

}
