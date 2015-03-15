package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.Comparator;
import java.util.HashSet;

/**
 * Created by rakesh on 3/11/15.
 */

public class Message implements Comparable<Message>{
    private int messageId;
    private int processId;
    private String message;
    private MessageType messageType;
    private boolean deliver;
    private int maxProp;
    private int proposedBy;
    private HashSet<String> repliesReceived;

    public Message() {
        this.messageId = 0;
        this.processId = 0;
        this.message = "";
        this.deliver = false;
        this.maxProp = 0;
        this.proposedBy = 0;
        this.repliesReceived = new HashSet<>();
    }

    public Message(String serialString) {
        String [] attribs = serialString.split(";");
        this.messageId = Integer.parseInt(attribs[0]);
        this.processId = Integer.parseInt(attribs[1]) ;
        this.messageType = MessageType.valueOf(attribs[2]);
        this.message = attribs[3];
        this.deliver = false;
        this.maxProp = 0;
        this.proposedBy = 0;
        this.repliesReceived = new HashSet<>();
    }

    public Message(int messageId, int processId, MessageType messageType, String message) {
        this.messageId = messageId;
        this.processId = processId;
        this.messageType = messageType;
        this.message = message;
        this.deliver = false;
        this.maxProp = 0;
        this.proposedBy = 0;
        this.repliesReceived = new HashSet<>();
    }

    @Override
    public int compareTo(Message rhs) {
        if (this.getMaxProp() < rhs.getMaxProp()){
            return  -1;
        }else if(this.getMaxProp() > rhs.getMaxProp()){
            return 1;
        }else{
            if (this.getProposedBy() > rhs.getProposedBy()){
                return -1;
            }else if (this.getProposedBy() < rhs.getProposedBy()) {
                return 1;
            }else {
                return 0;
            }
        }
    }

    public enum MessageType {
        MSG, PROPOSAL, DECISION
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getProcessId() {
        return processId;
    }

    public void setProcessId(int processId) {
        this.processId = processId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public int getMaxProp() {
        return maxProp;
    }

    public void setMaxProp(int maxProp) {
        this.maxProp = maxProp;
    }

    public boolean isDeliver() {
        return deliver;
    }

    public void setDeliver(boolean deliver) {
        this.deliver = deliver;
    }

    public int getProposedBy() {
        return proposedBy;
    }

    public void setProposedBy(int proposedBy) {
        this.proposedBy = proposedBy;
    }

    public HashSet<String> getRepliesReceived() {
        return repliesReceived;
    }

    public void setRepliesReceived(HashSet<String> repliesReceived) {
        this.repliesReceived = repliesReceived;
    }

    public String stringify(){
        String stringRep = this.messageId+";"+
                this.processId+";"+
                this.messageType.name()+";"+
                this.message;
        return stringRep;
    }

    @Override
    public String toString() {
        String stringRep = this.maxProp+"-"+
                this.processId;
        return stringRep;
    }
}
