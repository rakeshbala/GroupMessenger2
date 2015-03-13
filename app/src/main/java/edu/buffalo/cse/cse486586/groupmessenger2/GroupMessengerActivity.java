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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

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
    private ArrayList<Message> pList;
    private String failTrack = null;

    public void sendMessage(int port, String msgToSend){


        if (failTrack != null){
            if (idFromPort(port) == Integer.parseInt(failTrack)){
                return;
            }
        }
        try {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),port);

            OutputStream os = socket.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os);
            BufferedWriter bw = new BufferedWriter(osw);
            bw.write(msgToSend);
            bw.newLine();
            bw.flush();
            socket.close();
        }catch (IOException eio){
            eio.printStackTrace();
        }

    }


    private void sendFail(String failedNode){
        Message failMsg = new Message(-1,myId, Message.MessageType.FAILED,failedNode);
        for (int i=0;i<5;i++){
            /* failTrack not set yet . Don't send to failed node */
            if (i == Integer.parseInt(failedNode)) continue;
            sendMessage(Integer.parseInt(remotes[i]), failMsg.stringify());
        }
    }

    private Message getMessageWithMessage(Message msg){
        for (Message tempObj : pList) {
            if (tempObj.getMessageId() == msg.getMessageId()) {
                if (tempObj.getProcessId() == msg.getProcessId()) {
                    return tempObj;
                }
            }
        }
        return null;
    }

    private int idFromPort(int port){
        return ((port/4)-2)%5;
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
        pList = new ArrayList<>();

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
            Message msgObj = new Message(msgSq, myId, Message.MessageType.MSG, msgToSend);
            pList.add(msgObj);
            for (int i=0; i<5;i++){
                sendMessage(Integer.parseInt(remotes[i]),
                        msgObj.stringify());
            }
            msgSq++;
            return null;
        }
    }


    private class ServerTask extends  AsyncTask <ServerSocket, String, Void>{

        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try{
                while(true){
                    Socket socket = serverSocket.accept();
                    InputStreamReader isw = new InputStreamReader(socket.getInputStream());
                    BufferedReader br = new BufferedReader(isw);
                    String msgStr = br.readLine();
                    Message msgObj = new Message(msgStr);
                    switch (msgObj.getMessageType()){
                        case MSG:{
                            sendProposed(msgObj);
                            break;
                        }
                        case PROPOSAL:{
                            sendDecided(msgObj);
                            break;
                        }
                        case DECISION:{
                            decideAndDeliver(msgObj);
                            break;
                        }
                        default:
                            break;
                    }
//                    publishProgress(msgStr);
                    socket.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }


            return null;
        }

        public synchronized void cleanup(Message msgObj) {
            numLiveNodes--;
            String failedNode = msgObj.getMessage();
            failTrack = failedNode;
            Iterator<Message> iter = pList.iterator();
            while (iter.hasNext()) {
                Message msg = iter.next();
                /* Change affects only undeliverable messages*/
                if (!msg.isDeliver()) {
                    /* Remove the message if it is from the node */
                    if (msg.getProcessId() == Integer.parseInt(failedNode)) {
                        iter.remove();
                    } else if (!(msg.getRepliesReceived().contains(failedNode))) {
                        /* Adjust list if waiting for receipt from node */
                        if (msg.getRepliesReceived().size() == numLiveNodes) {
                            msg.setDeliver(true);
                        }
                    }
                }
            }
            /** Deliver if any new delivarable at head */
            Iterator<Message> iter1 = pList.iterator();
            while (iter1.hasNext()) {
                Message delivObj = iter1.next();
                if (delivObj.isDeliver()) {
                    publishProgress(delivObj.getMessage());
                    iter1.remove();
                } else {
                    break;
                }
            }
        }

        protected void onProgressUpdate(String...strings) {
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append("\n\t\t\t\t"+strReceived + "\n");
            writeToDisk(strReceived);
        }
        public synchronized void sendProposed(Message msgObj){
            maxProp++;
            Message ownObj = getMessageWithMessage(msgObj);
            if (ownObj == null){
                pList.add(msgObj);
            }
            String proposalString = Integer.toString(maxProp)+"-"+
                    Integer.toString(myId);
            Message propMsg = new Message(msgObj.getMessageId(),
                    msgObj.getProcessId(), Message.MessageType.PROPOSAL,
                    proposalString);
            sendMessage(Integer.parseInt(remotes[msgObj.getProcessId()]),
                    propMsg.stringify());
        }
        public synchronized void sendDecided(Message msgObj){
            Message ownObj = getMessageWithMessage(msgObj);
            String[] proposal = msgObj.getMessage().split("-");
            ownObj.getRepliesReceived().add(proposal[1]);

            int propNum = Integer.parseInt(proposal[0]);
            if (propNum>ownObj.getMaxProp()){
                ownObj.setMaxProp(propNum);
                ownObj.setProposedBy(Integer.parseInt(proposal[1]));
            }
            if (ownObj.getRepliesReceived().size() == numLiveNodes){
                String decisionString = Integer.toString(ownObj.getMaxProp())+"-"+
                        Integer.toString(ownObj.getProposedBy());
                Message decObj = new Message(msgObj.getMessageId(),
                        msgObj.getProcessId(),
                        Message.MessageType.DECISION,decisionString);
                for (int i=0;i<5;i++){
                    sendMessage(Integer.parseInt(remotes[i]),
                            decObj.stringify());
                }
            }
        }
        public synchronized void decideAndDeliver(Message msgObj){
            String[] decisionStr = msgObj.getMessage().split("-");
            int maxPropDec = Integer.parseInt(decisionStr[0]);
            if(maxPropDec > maxProp){
                maxProp = maxPropDec;
            }

            int maxProposedBy = Integer.parseInt(decisionStr[1]);
            Message ownObj = getMessageWithMessage(msgObj);
            ownObj.setMaxProp(maxPropDec);
            ownObj.setProposedBy(maxProposedBy);
            ownObj.setDeliver(true);
            Collections.sort(pList);

            Iterator<Message> iter = pList.iterator();
            while (iter.hasNext()){
                Message delivObj = iter.next();
                if (delivObj.isDeliver()){
                    publishProgress(delivObj.getMessage());
                    iter.remove();
                }else{
                    break;
                }
            }
        }

        public synchronized void writeToDisk(String strReceived){
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
            uriBuilder.scheme("content");
            Uri uri = uriBuilder.build();

            ContentValues contentValues = new ContentValues();
            contentValues.put("key",String.valueOf(counter));
            contentValues.put("value",strReceived);
            getContentResolver().insert(uri,contentValues);
            counter++;
        }
    }


}

