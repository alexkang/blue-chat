package com.alexkang.btchatroom;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Alex on 7/25/2014.
 */
public class MessageBox {

    private String sender;
    private String message;
    private Date time;

    private boolean self;

    public MessageBox(String sender, String message, Date time, boolean self) {
        this.sender = sender;
        this.message = message;
        this.time = time;
        this.self = self;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public String getTime() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("hh:mm");
        return dateFormatter.format(time);
    }

    public boolean isSelf() {
        return self;
    }

}
