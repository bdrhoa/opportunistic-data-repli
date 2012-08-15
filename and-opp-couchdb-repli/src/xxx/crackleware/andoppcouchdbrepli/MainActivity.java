package xxx.crackleware.andoppcouchdbrepli;

import android.app.Activity;
import android.os.Bundle;

import android.webkit.WebView;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebSettings;

import android.os.Process;

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

import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import java.util.Date;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import android.net.wifi.WifiManager;
import android.net.DhcpInfo;
import android.content.Context;

import java.util.Arrays;
import java.util.Random;
    
public class MainActivity extends Activity
{
    final int LOCAL_COUCHDB_PORT = 5985;
    final int BT_SOCKET_BUFFER_SIZE = 128;

    final String SERVICE_NAME = "CouchDBReplication";
    final UUID SERVICE_UUID = UUID.fromString("51981A19-5962-4A28-AD0F-2CE1654E16A2");
    //final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //final UUID SERVICE_UUID = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB");

    final int SYNC_PERIOD = 30*60*1000; // msec
    final int BCAST_PERIOD = 10*60*1000; // msec
    // final int BCAST_PERIOD = 100; // msec

    final String DBNAME = "phy-f2f-acc-db";
    
    Handler mHandler;
    Handler mHandler2;
    
    BluetoothAdapter mBluetoothAdapter;
    BluetoothServerSocket mBluetoothServerSocket;
    
    Thread mServerThread;

    // BluetoothSocket mRemoteCouchDBProxySocket;
    String mRemoteBTAddr;

    ScrollView scrlOutput;
    TextView tvOutput;
    int mTermLineCount = 0;

    protected void output(final String s)
    {
        Log.d("andoppcouchdbrepli", s);
        runOnUiThread(new Runnable() {
                public void run() {
                    mTermLineCount++;
                    if (mTermLineCount >= 3000) {
                        mTermLineCount = 0;
                        tvOutput.setText("");
                    }
                    tvOutput.append(s+"\n");
                    scrlOutput.fullScroll(View.FOCUS_DOWN);
                }
            });
    }

    String getCouchDBURL() throws IOException
    {
        return String.format("http://%s:%d", getIPAddress().toString().substring(1), LOCAL_COUCHDB_PORT);
    }

    String doHttpPost(String url, String reqdata) throws IOException, java.io.UnsupportedEncodingException
    {
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(url);
        httppost.setHeader("Content-Type", "application/json");
        output(String.format("%s: request to %s: %s",
                             new Date(), url, reqdata));
        httppost.setEntity(new StringEntity(reqdata));
        HttpResponse response = httpclient.execute(httppost);
        byte[] d = new byte[32*1024];
        int br = response.getEntity().getContent().read(d);
        String respdata = new String(d, 0, br);
        output(String.format("%s: response from %s: %s",
                             new Date(), url, respdata));
        return respdata;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d("andoppcouchdbrepli", "onCreate: called");
        
        scrlOutput = (ScrollView)findViewById(R.id.scrlOutput);
        tvOutput = (TextView)findViewById(R.id.tvOutput);

        if (mHandler2 == null)
            mHandler2 = new Handler();

        if (1==0)
        mHandler2.postDelayed(new Runnable() { public void run() {
            try {
                output(new Date().toString());
            } catch (Exception e) {
                Log.d("andoppcouchdbrepli", "mHandler2", e);
            }
            mHandler2.postDelayed(this, 200);
        } }, 1);

        if (1==1)
        if (mServerThread == null) {
            mServerThread = new Thread(new Runnable() { public void run() {
                try {
                    Looper.prepare();

                    mHandler = new Handler();

                    // LAN
                    initBroadcast();
                    if (1==1) {
                        mHandler.postDelayed(new Runnable() { public void run() {
                            try {
                                String s = String.format("%s %s\n", SERVICE_NAME, getCouchDBURL());
                                sendBroadcast(s.getBytes());
                                output(String.format("sent bcast: '%s'", s.trim()));
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
                                    output("received bcast: couchDBURL='"+couchDBURL+"'");
                                    if (!couchDBURL.equals(getCouchDBURL())) {
                                        Thread.sleep(rnd.nextInt(10000));
                                        doHttpPost("http://localhost:"+LOCAL_COUCHDB_PORT+"/_replicate",
                                                   "{\"source\": \""+DBNAME+"\", \"target\": \""+couchDBURL+"/"+DBNAME+"\", \"create_target\": true} ");
                                    }
                                } catch (Exception e) {
                                    Log.d("andoppcouchdbrepli", "bcast receiver", e);
                                }
                            }
                        }}).start();
                    }

                    // bluetooth
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    Log.d("andoppcouchdbrepli", "mServerThread: "+mBluetoothAdapter.toString());
                    
                    mBluetoothServerSocket =
                        mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
                    Log.d("andoppcouchdbrepli", "mServerThread: "+mBluetoothServerSocket.toString());

                    if (1==1)
                    new Thread(new Runnable() { public void run() {
                        while (true) {
                            try {
                                Log.d("andoppcouchdbrepli", "mServerThread: btsock accept wait");
                                BluetoothSocket btsock = mBluetoothServerSocket.accept();
                                Log.d("andoppcouchdbrepli", "mServerThread: accepted btsock="+btsock.toString());

                                Socket tcpsock = new Socket("127.0.0.1", LOCAL_COUCHDB_PORT);
                                Log.d("andoppcouchdbrepli", "mServerThread: connected tcpsock="+tcpsock.toString());

                                startCommThreads("i'm bluetooth server", btsock, tcpsock);
                                
                                output(String.format("%s: bt client accepted: %s (%s)",
                                                     new Date(),
                                                     btsock.getRemoteDevice().getName(),
                                                     btsock.getRemoteDevice().getAddress()));
                            } catch (Exception e) {
                                Log.d("andoppcouchdbrepli", "mServerThread: btsock accept", e);
                            }
                        }
                    }}).start();

                    if (1==1)
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
                                                output(String.format("%s: connected to bt node: %s (%s)",
                                                                     new Date(),
                                                                     btsock.getRemoteDevice().getName(),
                                                                     btsock.getRemoteDevice().getAddress()));
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

                            // String[] btaddrs = new String[]{
                            //     "D8:B1:00:18:AA:80", // p-vr
                            // };
                            for (Object obj : btaddrs) {
                                String btaddr = (String)obj;
                                try {
                                    if (btaddr.equals(mBluetoothAdapter.getAddress()))
                                        continue;
                                    
                                    output(String.format("%s: sync with bt node: (%s)",
                                                         new Date(), btaddr));
                                    
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
                    
                    Looper.loop();
                    Log.d("andoppcouchdbrepli", "mServerThread: exit");
                    
                } catch (Exception e) {
                    Log.d("andoppcouchdbrepli", "mServerThread", e);
                }
            }});
            mServerThread.start();
        }
                
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
        output(String.format("my ip addr: %s", getIPAddress()));
        output(String.format("bcast addr: %s", getBroadcastAddress()));
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

    @Override
    public void onPause()
    {
        setContentView(R.layout.main);
        super.onPause();
    }
    
    @Override
    public void onResume()
    {
        super.onResume();
        // setContentView(mWebView);
    }
    
}
