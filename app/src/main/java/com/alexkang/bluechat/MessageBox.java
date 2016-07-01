package com.alexkang.bluechat;

import android.graphics.Bitmap;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MessageBox {

    private String sender;
    private String message;
    private Bitmap image;
    private Date time;

    private boolean self;
    private boolean isImage;

    public MessageBox(String sender, String message, Date time, boolean self) {
        this.sender = sender;
        this.message = message;
        this.time = time;
        this.self = self;
        this.isImage = false;
    }

    public MessageBox(String sender, Bitmap image, Date time, boolean self) {
        this(sender, "", time, self);
        this.image = image;
        this.isImage = true;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public Bitmap getImage() {
        return image;
    }

    public String getTime() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("hh:mm", Locale.getDefault());
        return dateFormatter.format(time);
    }

    public boolean isSelf() {
        return self;
    }

    public boolean isImage() {
        return isImage;
    }

}
