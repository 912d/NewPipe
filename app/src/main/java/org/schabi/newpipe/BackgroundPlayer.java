package org.schabi.newpipe;

import android.app.IntentService;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;
import android.os.Process;

import java.io.IOException;

/**
 * Created by Adam Howard on 08/11/15.
 *
 * Copyright (c) Adam Howard <achdisposable1@gmail.com> 2015
 *
 * BackgroundPlayer.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

/**Plays the audio stream of videos in the background. */
public class BackgroundPlayer extends Service /*implements MediaPlayer.OnPreparedListener*/ {

    private static final String TAG = BackgroundPlayer.class.toString();
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;

    public BackgroundPlayer() {
        super();
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        super.onCreate();
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Playing in background", Toast.LENGTH_SHORT).show();//todo:translation string

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, don't restart
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding yet, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        //Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
        mServiceLooper.quit();
    }

    protected void onHandleIntent(Intent intent) {
        String source = intent.getDataString();

        if (intent.getAction().equals(ACTION_PLAY)) {
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.prepareAsync(); // prepare async to not block main thread
        }


        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);//cpu lock
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(source);
            mediaPlayer.prepare(); //IntentService already puts us in a separate worker thread,
            //so calling the blocking prepare() method should be ok
        } catch (IOException ioe) {
            ioe.printStackTrace();
            //can't really do anything useful without a file to play; exit early
            return;
        }

        WifiManager wifiMgr = ((WifiManager)getSystemService(Context.WIFI_SERVICE));
        WifiManager.WifiLock wifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);

        mediaPlayer.setOnCompletionListener(new EndListener(wifiLock));//listen for end of video
/*
        //get audio focus
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // could not get audio focus.
        }
*/
        wifiLock.acquire();
        //mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.start();

        String videoTitle = intent.getStringExtra("title");

        Notification noti = new NotificationCompat.Builder(this)
                .setPriority(Notification.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setContentTitle(videoTitle)
                .setContentText("NewPipe is playing in the background")//todo: add translatable string
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(TAG.hashCode(), noti);
    }
/*
    private class ListenerThread extends Thread implements AudioManager.OnAudioFocusChangeListener {

    }

    @Override
    public void onAudioFocusChange(int focusChange) {

    }*/

    private class EndListener implements MediaPlayer.OnCompletionListener {
        private WifiManager.WifiLock wl;
        public EndListener(WifiManager.WifiLock wifiLock) {
            this.wl = wifiLock;
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
            wl.release();
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent)msg.obj);
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelfResult(msg.arg1);
        }
    }
}