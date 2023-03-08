package com.example.solocarry.model;

import android.net.Uri;

import java.util.ArrayList;

public class Code {
    private int score;
    private float latitude;
    private float longitude;
    private int hashCode;
    private Uri photo;

    public Code() {}

    public Code(int hashCode) {
        this.hashCode = hashCode;
        this.score = 0;
        this.latitude = 0.0F;
        this.longitude = 0.0F;
        this.photo = null;
    }

    public int getHashCode () {return hashCode;}

    public void updateScore(int newVal) {
        this.score = newVal;
    }


    public int getScore(){
        return this.score;
    }
    public void setLocation(float lat, float lon) {
        this.longitude = lon;
        this.latitude = lat;
    }



    public void changeLongitude(float longitude) {
        this.longitude = longitude;
    }

    public void changeLatitude(float latitude) {
        this.latitude = latitude;
    }

    public float getLatitude() {

        return latitude;
    }

    public float getLongitude() {return longitude;}

    public void setPhoto(Uri photo) {
        this.photo = photo;
    }

    public Uri getPhoto() {
        return photo;
    }
}
