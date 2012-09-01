package xxx.crackleware.andoppcouchdbrepli;

import java.io.IOException;
import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.os.Process;
import android.content.Context;
import android.widget.Toast;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import android.os.PowerManager;

import com.couchbase.android.ICouchbaseDelegate;
import com.couchbase.android.CouchbaseMobile;
import android.content.ServiceConnection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import java.util.UUID;

import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;

import android.os.Handler;

import android.os.Looper;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpResponse;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import android.net.wifi.WifiManager;
import android.net.DhcpInfo;

import java.util.Arrays;
import java.util.Random;

import java.util.Date;

public class ReplicationService extends IntentService {
    public static final String SERVICE_LOG = "andoppcouchdbrepli-service-log";
    
    void log(String fmt, Object... args)
    {
        String s = String.format("ReplicationService: "+fmt, args);
        Log.d("andoppcouchdbrepli", s);
    }

    void sendLogToActivity(String fmt, Object... args)
    {
        log(fmt, args);
        String s = String.format("ReplicationService: "+fmt, args);
        Intent i = new Intent(SERVICE_LOG);
        i.putExtra("log", s);
        sendBroadcast(i);
    }

    private CouchbaseMobile mCouchbaseMobile;
    private ServiceConnection mCouchServiceConnection;

    boolean mCouchbaseStarted = false;

    private final ICouchbaseDelegate mCouchbaseDelegate = new ICouchbaseDelegate() {
        @Override
        public void couchbaseStarted(String host, int port) {
            sendLogToActivity("mCouchbaseDelegate.couchbaseStarted: host='%s', port=%d", host, port);
		    try {
				mCouchbaseMobile.installDatabase("mobilefuton.couch");
                mCouchbaseStarted = true;
			} catch (IOException e) {
                Log.d("andoppcouchdbrepli", "installDatabase mobilefuton", e);
			}
        }
    
        @Override
        public void exit(String error) {
            log("mCouchbaseDelegate.exit: error='%s'", error);
        }
    };

    public void startCouchbase() {
        mCouchbaseMobile = new CouchbaseMobile(getBaseContext(), mCouchbaseDelegate);
		try {
			mCouchbaseMobile.copyIniFile("couchdb-config.ini");
		} catch (IOException e) {
            Log.d("andoppcouchdbrepli", "copyIniFile", e);
		}
        mCouchServiceConnection = mCouchbaseMobile.startCouchbase();
    }
    
    public ReplicationService() {
        super("ReplicationService");
    }
  
    @Override
    public void onCreate() {
        log("onCreate called");
        super.onCreate();
        
        startForeground(R.string.app_name, new Notification());
        
        //acquireWakeLock();
    }
  
    @Override
    public void onDestroy() {
        log("onDestroy called");
        super.onDestroy();
    }

    final int NOTIFY_LAST_SUCCESSFUL_REPLICATION_ID = 1;
    final int NOTIFY_LAST_FAILED_REPLICATION_ID = 2;
    final int NOTIFY_LAST_LAN_REPLICATION_ID = 3;
    final int NOTIFY_LAST_BLUETOOTH_REPLICATION_ID = 4;

    @Override 
    public void onHandleIntent(Intent i)
    {
        try {
            startCouchbase();
            while (!mCouchbaseStarted) {
                log("waiting for Couchbase");
                Thread.sleep(3000);
            }
            mainLoop();
        } catch (Exception e) {
            log("onHandleIntent", e);
            showNotification(NOTIFY_LAST_FAILED_REPLICATION_ID, "Fatal error, stopping service.");
        }
     }

    public void showNotification(int id, String text)
    {
        //Toast.makeText(this, s, Toast.LENGTH_SHORT).show();

        String title = "CouchDB Replicator";
        
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager)getSystemService(ns);

        int icon = R.drawable.icon;
        CharSequence tickerText = text;
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);

        Context context = getApplicationContext();
        CharSequence contentTitle = title;
        CharSequence contentText = text;
        Intent notificationIntent = new Intent(this, ReplicationService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        // notification.defaults |= Notification.DEFAULT_VIBRATE;
        // notification.flags |= Notification.FLAG_AUTO_CANCEL;
    
        mNotificationManager.notify(id, notification);
    }

    public PowerManager.WakeLock mWakeLock;
    
    public void acquireWakeLock()
    {
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "vogotomo");
        mWakeLock.acquire();
        log("acquireWakeLock: done");
    }

    // NETWORKING AND REPLICATION //
    
    final int LOCAL_COUCHDB_PORT = 44631;
    final int BT_SOCKET_BUFFER_SIZE = 128;

    final String SERVICE_NAME = "CouchDBReplication";
    final UUID SERVICE_UUID = UUID.fromString("51981A19-5962-4A28-AD0F-2CE1654E16A2");
    //final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //final UUID SERVICE_UUID = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB");

    final int SYNC_PERIOD = 30*60*1000; // msec
    final int BCAST_PERIOD = 10*60*1000; // msec
    // final int BCAST_PERIOD = 100; // msec

    final String DBNAME = "phy-f2f-acc-db";
    
    BluetoothAdapter mBluetoothAdapter;
    BluetoothServerSocket mBluetoothServerSocket;
    
    // BluetoothSocket mRemoteCouchDBProxySocket;
    String mRemoteBTAddr;

   
    String getCouchDBURL() throws IOException
    {
        return String.format("http://%s:%d", getIPAddress().toString().substring(1), LOCAL_COUCHDB_PORT);
    }

    String doHttpPost(String url, String reqdata) throws IOException, java.io.UnsupportedEncodingException
    {
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(url);
        httppost.setHeader("Content-Type", "application/json");
        sendLogToActivity("%s: request to %s: %s",
                          new Date(), url, reqdata);
        httppost.setEntity(new StringEntity(reqdata));
        HttpResponse response = httpclient.execute(httppost);
        byte[] d = new byte[32*1024];
        int br = response.getEntity().getContent().read(d);
        String respdata = new String(d, 0, br);
        sendLogToActivity("%s: response from %s: %s",
                          new Date(), url, respdata);
        return respdata;
    }

    Handler mHandler;
    
    void mainLoop()
    {
        try {
            //Looper.prepare();

            mHandler = new Handler();

            if (1==1)
                try {
                    startLAN();
                } catch (Exception e) {
                    Log.d("andoppcouchdbrepli", "ReplicationService: startLAN", e);
                }

            if (1==1)
                try {
                    startBluetooth();
                } catch (Exception e) {
                    Log.d("andoppcouchdbrepli", "ReplicationService: startBluetooth", e);
                }

            Looper.loop();
            
            Log.d("andoppcouchdbrepli", "ReplicationService: mainLoop: exit");

        } catch (Exception e) {
            Log.d("andoppcouchdbrepli", "ReplicationService: mainLoop", e);
        }
    }

    void startLAN() throws IOException
    {
        initBroadcast();
        mHandler.postDelayed(new Runnable() { public void run() {
            try {
                String s = String.format("%s %s\n", SERVICE_NAME, getCouchDBURL());
                sendBroadcast(s.getBytes());
                sendLogToActivity("sent bcast: '%s'", s.trim());
            } catch (Exception e) {
                Log.d("andoppcouchdbrepli", "bcast sender", e);
            }
            mHandler.postDelayed(this, BCAST_PERIOD);
        } }, 5000);

        new Thread(new Runnable() { public void run() {
            Random rnd = new Random();
            while (true) {
                try {
                    String[] parts = new String(recvBroadcast()).split("[ \n]");
                    String serviceName = parts[0];
                    String couchDBURL = parts[1];
                    String logs = "received bcast: couchDBURL='"+couchDBURL+"'";
                    sendLogToActivity(logs);
                    if (!couchDBURL.equals(getCouchDBURL())) {
                        Thread.sleep(rnd.nextInt(10000));
                        doHttpPost("http://localhost:"+LOCAL_COUCHDB_PORT+"/_replicate",
                                   "{\"source\": \""+DBNAME+"\", \"target\": \""+couchDBURL+"/"+DBNAME+"\", \"create_target\": true} ");
                        showNotification(NOTIFY_LAST_LAN_REPLICATION_ID, logs);
                    }
                } catch (Exception e) {
                    Log.d("andoppcouchdbrepli", "bcast receiver", e);
                }
            }
        }}).start();
    }

    void startBluetooth() throws IOException
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Log.d("andoppcouchdbrepli", "mServerThread: "+mBluetoothAdapter.toString());

        mBluetoothServerSocket =
            mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
        Log.d("andoppcouchdbrepli", "mServerThread: "+mBluetoothServerSocket.toString());

        new Thread(new Runnable() { public void run() {
            while (true) {
                try {
                    Log.d("andoppcouchdbrepli", "mServerThread: btsock accept wait");
                    BluetoothSocket btsock = mBluetoothServerSocket.accept();
                    Log.d("andoppcouchdbrepli", "mServerThread: accepted btsock="+btsock.toString());

                    Socket tcpsock = new Socket("127.0.0.1", LOCAL_COUCHDB_PORT);
                    Log.d("andoppcouchdbrepli", "mServerThread: connected tcpsock="+tcpsock.toString());

                    startCommThreads("i'm bluetooth server", btsock, tcpsock);

                    String logs = String.format("%s: bt client accepted: %s (%s)",
                                                new Date(),
                                                btsock.getRemoteDevice().getName(),
                                                btsock.getRemoteDevice().getAddress());
                    sendLogToActivity(logs);
                    showNotification(NOTIFY_LAST_BLUETOOTH_REPLICATION_ID, logs);
                } catch (Exception e) {
                    Log.d("andoppcouchdbrepli", "mServerThread: btsock accept", e);
                }
            }
        }}).start();

        mHandler.postDelayed(new Runnable() { public void run() {
            try {
                final ServerSocket tcpsrvsock = new ServerSocket(20001, 5, InetAddress.getByName("127.0.0.1"));
                Log.d("andoppcouchdbrepli", "mServerThread: listen tcpsrvsock="+tcpsrvsock.toString());
                tcpsrvsock.setSoTimeout(30000);

                new Thread(new Runnable() { public void run() {
                    while (true) {
                        try {
                            Log.d("andoppcouchdbrepli", "mServerThread: tcpsrvsock accept wait");
                            Socket tcpsock = tcpsrvsock.accept();
                            Log.d("andoppcouchdbrepli", "mServerThread: tcpsrvsock accepted tcpsock="+tcpsock.toString());

                            BluetoothDevice btdev = mBluetoothAdapter.getRemoteDevice(mRemoteBTAddr);

                            final BluetoothSocket btsock = btdev.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);
                            Thread t = new Thread(new Runnable() { public void run() {
                                try {
                                    btsock.connect();
                                    Log.d("andoppcouchdbrepli", "mServerThread: connected btsock="+btsock.toString());
                                    String logs = String.format("%s: connected to bt node: %s (%s)",
                                                                new Date(),
                                                                btsock.getRemoteDevice().getName(),
                                                                btsock.getRemoteDevice().getAddress());
                                    sendLogToActivity(logs);
                                    showNotification(NOTIFY_LAST_BLUETOOTH_REPLICATION_ID, logs);
                                } catch (Exception e) {
                                    Log.d("andoppcouchdbrepli", "btsock.connect", e);
                                }
                            }});
                            t.start();
                            t.join(10000);
                            t.interrupt();

                            startCommThreads("i'm bluetooth client", btsock, tcpsock);

                        } catch (Exception e) {
                            Log.d("andoppcouchdbrepli", "bt->local tcp proxy", e);
                        }
                    }
                }}).start();

                String line;
                ArrayList btaddrs = new ArrayList();

                BufferedReader in = new BufferedReader(new FileReader("/mnt/sdcard/couchdb-repli-known_nodes.txt"));
                if (!in.ready())
                    throw new IOException();
                while ((line = in.readLine()) != null)  {
                    String[] parts = line.split(" ");
                    if (parts[0].charAt(0) != '#')
                        btaddrs.add(parts[0]);
                }
                in.close();

                for (Object obj : btaddrs) {
                    String btaddr = (String)obj;
                    try {
                        if (btaddr.equals(mBluetoothAdapter.getAddress()))
                            continue;

                        sendLogToActivity("%s: sync with bt node: (%s)",
                                          new Date(), btaddr);

                        mRemoteBTAddr = btaddr;

                        doHttpPost("http://localhost:"+LOCAL_COUCHDB_PORT+"/_replicate",
                                   "{\"source\": \""+DBNAME+"\", \"target\": \"http://localhost:20001/"+DBNAME+"\", \"create_target\": true} ");

                        mRemoteBTAddr = null;

                    } catch (Exception e) {
                        Log.d("andoppcouchdbrepli", "mServerThread: error while sync with "+btaddr, e);
                    }
                }
            } catch (Exception e) {
                Log.d("andoppcouchdbrepli", "mServerThread: sync error", e);
            }
            mHandler.postDelayed(this, SYNC_PERIOD);
        } }, 3000);
    }

    protected void startCommThreads(final String xfrctx, final BluetoothSocket btsock, final Socket tcpsock)
    {
        Thread remote2LocalThread = new Thread(new Runnable() { public void run() {
            try {
                byte[] d = new byte[BT_SOCKET_BUFFER_SIZE];
                while (true) {
                    int br = btsock.getInputStream().read(d);
                    //Log.d("andoppcouchdbrepli", "remote2LocalThread: br="+br);
                    if (br <= 0)
                        break;
                    tcpsock.getOutputStream().write(d, 0, br);
                    tcpsock.getOutputStream().flush();
                }
            } catch (Exception e) {
                Log.d("andoppcouchdbrepli", "remote2LocalThread read-write loop", e);
            }
            try { btsock.close(); } catch (Exception e) { Log.d("andoppcouchdbrepli", "btsock.close", e); }
            try { tcpsock.close(); } catch (Exception e) { Log.d("andoppcouchdbrepli", "tcpsock.close", e); }
            Log.d("andoppcouchdbrepli", "remote2LocalThread: closed ("+xfrctx+")");
        }});
        remote2LocalThread.start();

        Thread local2RemoteThread = new Thread(new Runnable() { public void run() {
            try {
                byte[] d = new byte[BT_SOCKET_BUFFER_SIZE];
                while (true) {
                    int br = tcpsock.getInputStream().read(d);
                    //Log.d("andoppcouchdbrepli", "local2RemoteThread: br="+br);
                    if (br <= 0)
                        break;
                    btsock.getOutputStream().write(d, 0, br);
                    btsock.getOutputStream().flush();
                }
            } catch (Exception e) {
                Log.d("andoppcouchdbrepli", "local2RemoteThread read-write loop", e);
            }
            try { btsock.close(); } catch (Exception e) { Log.d("andoppcouchdbrepli", "btsock.close", e); }
            try { tcpsock.close(); } catch (Exception e) { Log.d("andoppcouchdbrepli", "tcpsock.close", e); }
            Log.d("andoppcouchdbrepli", "local2RemoteThread: closed ("+xfrctx+")");
        }});
        local2RemoteThread.start();
    }


    // from http://code.google.com/p/boxeeremote/wiki/AndroidUDP
    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        // handle null somehow

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    InetAddress getIPAddress() throws IOException {
        WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        // handle null somehow

        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((dhcp.ipAddress >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    final int BCAST_PORT = 44444;
    final int BCAST_DISCOVERY_PORT = 44444;
    DatagramSocket mBcastSocket;

    void initBroadcast() throws IOException
    {
        sendLogToActivity("my ip addr: %s", getIPAddress());
        sendLogToActivity("bcast addr: %s", getBroadcastAddress());
        mBcastSocket = new DatagramSocket(BCAST_PORT);
        mBcastSocket.setBroadcast(true);
        mBcastSocket.setSoTimeout(300*1000);
    }

    void sendBroadcast(byte[] data) throws IOException
    {
        DatagramPacket packet = new DatagramPacket(data, data.length,
                                                   getBroadcastAddress(), BCAST_DISCOVERY_PORT);
        mBcastSocket.send(packet);
    }

    byte[] recvBroadcast() throws IOException
    {
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        mBcastSocket.receive(packet);
        // Log.d("andoppcouchdbrepli", "bcast recv len: "+packet.getLength());
        return Arrays.copyOf(buf, packet.getLength());
    }
    
}
