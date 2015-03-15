package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] remotes = {"11108","11112","11116","11120","11124"};
    static int counter = 0;
    static final int SERVER_PORT = 10000;
    static int numLiveNodes = 5;

    private int myId=0;
    private int msgSq=1;
    private int maxProp =0;
    private final Vector<Message> pList = new Vector<>();
    private String failTrack = null;
    private ReentrantLock lock = new ReentrantLock(true);


    public int idFromPort(int port){
        return ((port/4)-2)%5;
    }
    public synchronized Message getMessageWithMessage(Message msg){
        for (Message tempObj : pList) {
            if (tempObj.getMessageId() == msg.getMessageId()) {
                if (tempObj.getProcessId() == msg.getProcessId()) {
                    return tempObj;
                }
            }
        }
        return null;
    }
    public synchronized void writeToDisk(String strReceived){
        TextView remoteTextView = (TextView) findViewById(R.id.textView1);
        remoteTextView.append("\n\t\t\t\t"+strReceived + "\n");

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        Uri uri = uriBuilder.build();

        ContentValues contentValues = new ContentValues();
        contentValues.put("key",String.valueOf(counter));
        contentValues.put("value",strReceived);
        getContentResolver().insert(uri, contentValues);
        counter++;
    }
    public synchronized void cleanup(Message msgObj) {
        Log.d("Cleanup",msgObj.stringify());
        numLiveNodes--;
        String failedNode = msgObj.getMessage();
        failTrack = failedNode;
        synchronized (pList){
            Iterator<Message> iter = pList.iterator();
            while (iter.hasNext()) {
                Message msg = iter.next();
                /* Change affects only undeliverable messages*/
                if (!msg.isDeliver()) {
                    /* Remove the message if it is from the node */
                    if (msg.getProcessId() == Integer.parseInt(failedNode)) {
                        Log.d("Cleanup delete",msg.stringify());
                        iter.remove();
                    } else if (!(msg.getRepliesReceived().contains(failedNode))) {
                        /* Adjust list if waiting for receipt from node */
                        if (msg.getRepliesReceived().size() == numLiveNodes) {
                            Log.d("Cleanup decide",msg.stringify());
                            sendDecision(msg);
                        }
                    }
                }
            }
        }

    }
    public synchronized void sendMessage(int port, String msgToSend){


        if (failTrack != null){
            int failedNodeId = Integer.parseInt(failTrack);
            if (idFromPort(port) == failedNodeId){
                Log.d("Fail alert",failTrack);
                return;
            }
        }
        try {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),port);
            socket.setSoTimeout(1000);
            OutputStream os = socket.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os);
            BufferedWriter bw = new BufferedWriter(osw);
            bw.write(msgToSend);
            bw.newLine();
            bw.flush();
            socket.close();
        }catch(SocketException se){
            Message failMsg = new Message(msgToSend);
            failMsg.setMessage(Integer.toString(idFromPort(port)));
            cleanup(failMsg);
        }catch (IOException eio){
            Message failMsg = new Message(msgToSend);
            failMsg.setMessage(Integer.toString(idFromPort(port)));
            cleanup(failMsg);
            eio.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public synchronized void sendDecision(Message ownObj){
        if (ownObj.getRepliesReceived().size() == numLiveNodes){
            String decisionString = Integer.toString(ownObj.getMaxProp())+"-"+
                    Integer.toString(ownObj.getProposedBy());
            Message decObj = new Message(ownObj.getMessageId(),ownObj.getProcessId(),
                    Message.MessageType.DECISION,decisionString);
            for (int i=0;i<5;i++){
                Log.d("Sending",decObj.stringify());
                sendMessage(Integer.parseInt(remotes[i]), decObj.stringify());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /** Get own port **/
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        myId = idFromPort(Integer.parseInt(myPort));

        /** Send on click **/
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editor = (EditText) findViewById(R.id.editText1);
                String msg = editor.getText().toString();

                editor.setText("");
                if (msg.length()>0){
                    TextView myMsgTextView = (TextView) findViewById(R.id.textView1);
                    myMsgTextView.append("\t" + msg);

                    /** Create client task and send */
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                }

            }
        });

        /** Create server socket and listen */
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            Log.e(TAG, "Failed to create server socket");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ClientTask extends AsyncTask <String , Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = msgs[0];
            Message msgObj;
            final int messageId;
            final int msgProcessId;
            synchronized (this){
                msgObj = new Message(msgSq, myId, Message.MessageType.MSG, msgToSend);
                messageId = msgSq;
                msgProcessId = myId;
                msgSq++;
                pList.add(msgObj);
            }
            Log.d("Sending Msg",msgObj.stringify());

            for (int i=0; i<5;i++){
                final String processId = Integer.toString(i);
                sendMessage(Integer.parseInt(remotes[i]), msgObj.stringify());

                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        synchronized (pList){
                            for (int i=0;i<pList.size();i++) {
                                Message tempObj = pList.get(i);
                                if ((tempObj.getMessageId() == messageId)
                                        && (tempObj.getProcessId() == msgProcessId)
                                        && !(processId.equals(Integer.toString(myId)))
                                        ) {
                                    if (!tempObj.getRepliesReceived().contains(processId)){
                                        Log.d("Timer","failed propose");
                                        Message failObj = new Message(tempObj.stringify());
                                        failObj.setMessage(processId);
                                        cleanup(failObj);
                                    }
                                }
                            }
                        }
                    }
                },4400);
            }
            return null;
        }

    }

    private class ServerTask extends  AsyncTask <ServerSocket, String, Void>{

        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            while (true){
                try{
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(1000);
                    InputStreamReader isw = new InputStreamReader(socket.getInputStream());
                    BufferedReader br = new BufferedReader(isw);
                    String msgStr = br.readLine();
                    Message msgObj = new Message(msgStr);
                    switch (msgObj.getMessageType()){
                        case MSG:{
                            handleMessage(msgObj);
                            break;
                        }
                        case PROPOSAL:{
                            handleProposal(msgObj);
                            break;
                        }
                        case DECISION:{
                            handleDecision(msgObj);
                            break;
                        }
                        default:
                            break;
                    }
                    socket.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }

        public synchronized void handleMessage(Message msgObj){
            Log.d("Received Message",msgObj.stringify());
            maxProp++;
            Message ownObj = getMessageWithMessage(msgObj);
            synchronized (pList){
                if (ownObj == null){
                    pList.add(msgObj);
                }
            }
            final int messageId = msgObj.getMessageId();
            final int msgProcessId = msgObj.getProcessId();

            String proposalString = Integer.toString(maxProp)+"-"+Integer.toString(myId);
            Message propMsg = new Message(messageId, msgProcessId, Message.MessageType.PROPOSAL,
                    proposalString);
            sendMessage(Integer.parseInt(remotes[msgProcessId]), propMsg.stringify());

            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (pList){
                        for (int i=0;i<pList.size();i++) {
                            Message tempObj = pList.get(i);
                            if ((tempObj.getMessageId() == messageId)
                                    && (tempObj.getProcessId() == msgProcessId)
                                    && msgProcessId != myId) {
                                Log.d("Timer","Object "+tempObj.getMessageId()+
                                        " "+tempObj.getProcessId()+
                                        " exists at "+i);
                                if (!tempObj.isDeliver()){
                                    Log.d("Timer","failed deilver of "+tempObj.getMessageId()+" "+tempObj.getProcessId());
                                    Message failObj = new Message(tempObj.stringify());
                                    failObj.setMessage(Integer.toString(msgProcessId));
                                    cleanup(failObj);
                                }
                            }
                        }
                    }
                }
            },8800);
        }

        public synchronized void handleProposal(Message msgObj){
            Message ownObj = getMessageWithMessage(msgObj);
            Log.d("Received proposal",msgObj.stringify());
            if (ownObj == null){
                Log.d("Null alert",msgObj.stringify());
                Log.d("Null alert",pList.toString());
                return;
            }
            String[] proposal = msgObj.getMessage().split("-");
            ownObj.getRepliesReceived().add(proposal[1]);
            int propNum = Integer.parseInt(proposal[0]);
            if (propNum>ownObj.getMaxProp()){
                ownObj.setMaxProp(propNum);
                ownObj.setProposedBy(Integer.parseInt(proposal[1]));
            }
            sendDecision(ownObj);
        }

        public void handleDecision(Message msgObj){
            Log.d("Null",msgObj.stringify());
            String[] decisionStr = msgObj.getMessage().split("-");
            int maxPropDec = Integer.parseInt(decisionStr[0]);

            lock.lock();
            if (maxPropDec > maxProp) {
                maxProp = maxPropDec;
            }


            int maxProposedBy = Integer.parseInt(decisionStr[1]);
            Message ownObj = getMessageWithMessage(msgObj);
            if (ownObj == null){
                Log.d("Null",msgObj.stringify());
                Log.d("Null",pList.toString());
                return;
            }
            ownObj.setMaxProp(maxPropDec);
            ownObj.setProposedBy(maxProposedBy);
            ownObj.setDeliver(true);
            lock.unlock();

            synchronized (pList){
                Collections.sort(pList);
                ListIterator<Message> iter = pList.listIterator();
                while (iter.hasNext()){
                    Message delivObj = iter.next();
                    if (delivObj.isDeliver()){
                        Log.d("Removing",delivObj.stringify());
                        iter.remove();
                        publishProgress(delivObj.getMessage());
                    }else{
                        break;
                    }
                }
            }

        }

        protected void onProgressUpdate(String...strings) {
            String strReceived = strings[0].trim();
            writeToDisk(strReceived);
        }

    }
}

