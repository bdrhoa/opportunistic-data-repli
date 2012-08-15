package xxx.crackleware.andbtcouchdbrepli;

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
        
public class MainActivity extends Activity
{
    // static WebView mWebView = null;
    
    // @Override
    // public void onCreate(Bundle savedInstanceState)
    // {
    //     super.onCreate(savedInstanceState);
    //     //setContentView(R.layout.main);
        
    //     getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

    //     if (mWebView == null) {
    //         mWebView = new WebView(this);
        
    //         mWebView.setWebChromeClient(new WebChromeClient() {
    //                 public boolean onConsoleMessage(ConsoleMessage cm) {
    //                     Log.d("awebmusplay", cm.sourceId()+":"+cm.lineNumber()+": "+cm.message());
    //                     return true;
    //                 }
    //                 public void onCloseWindow (WebView window) {
    //                     //finish();
    //                     Process.killProcess(Process.myPid());
    //                 }
    //             });

    //         new java.io.File("/mnt/sdcard/awebmusplay").mkdirs();
            
    //         WebSettings websettings = mWebView.getSettings();
            
	//         // websettings.setAllowFileAccess(true);
	//         websettings.setAppCacheEnabled(true);
	//         websettings.setAppCacheMaxSize(10*1024*1024);
	//         websettings.setAppCachePath("/mnt/sdcard/awebmusplay");
	//         // websettings.setCacheMode(WebSettings.LOAD_NORMAL);
	//         websettings.setDatabaseEnabled(true);
	//         websettings.setDomStorageEnabled(true);
	//         websettings.setJavaScriptEnabled(true);
	//         websettings.setLoadsImagesAutomatically(true);
	//         // websettings.setPluginState(WebSettings.PluginState.OFF);
	//         // websettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
	//         // websettings.setSaveFormData(false);
	//         // websettings.setSavePassword(false);
	//         // websettings.setSupportMultipleWindows(false);
	//         // websettings.setSupportZoom(false);

    //         websettings.setDatabasePath("/mnt/sdcard/awebmusplay");
         
    //         mWebView.loadUrl("http://10.0.0.90:81/xfr/playlists/3.html");
    //         // mWebView.loadUrl("http://10.0.0.90:81/xfr/playlists/t.html");
    //     }

    //     setContentView(mWebView);
    // }

    final int LOCAL_COUCHDB_PORT = 5985;
    final int SOCKET_BUFFER_SIZE = 128;

    final String SERVICE_NAME = "CouchDBReplication";
    final UUID SERVICE_UUID = UUID.fromString("51981A19-5962-4A28-AD0F-2CE1654E16A2");
    //final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //final UUID SERVICE_UUID = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB");

    final int SYNC_PERIOD = 1800*1000; // msec

    final String DBNAME = "phy-f2f-acc-db";
    
    Handler mHandler = new Handler();
    
    BluetoothAdapter mBluetoothAdapter;
    BluetoothServerSocket mBluetoothServerSocket;
    
    Thread mServerThread;

    // BluetoothSocket mRemoteCouchDBProxySocket;
    String mRemoteBTAddr;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d("andbtcouchdbrepli", "onCreate: called");

        if (mServerThread == null) {
            mServerThread = new Thread(new Runnable() { public void run() {
                try {
                    Looper.prepare();
                    
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    Log.d("andbtcouchdbrepli", "mServerThread: "+mBluetoothAdapter.toString());
                    
                    mBluetoothServerSocket =
                        mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
                    Log.d("andbtcouchdbrepli", "mServerThread: "+mBluetoothServerSocket.toString());

                    if (1==1)
                    new Thread(new Runnable() { public void run() {
                        while (true) {
                            try {
                                Log.d("andbtcouchdbrepli", "mServerThread: btsock accept wait");
                                BluetoothSocket btsock = mBluetoothServerSocket.accept();
                                Log.d("andbtcouchdbrepli", "mServerThread: accepted btsock="+btsock.toString());

                                Socket tcpsock = new Socket("127.0.0.1", LOCAL_COUCHDB_PORT);
                                Log.d("andbtcouchdbrepli", "mServerThread: connected tcpsock="+tcpsock.toString());

                                startCommThreads("i'm bluetooth server", btsock, tcpsock);
                            } catch (Exception e) {
                                Log.d("andbtcouchdbrepli", "mServerThread: btsock accept", e);
                            }
                        }
                    }}).start();

                    if (1==1)
                    mHandler.postDelayed(new Runnable() { public void run() {
                        try {
                            final ServerSocket tcpsrvsock = new ServerSocket(20001, 5, InetAddress.getByName("127.0.0.1"));
                            Log.d("andbtcouchdbrepli", "mServerThread: listen tcpsrvsock="+tcpsrvsock.toString());
                            tcpsrvsock.setSoTimeout(30000);

                            new Thread(new Runnable() { public void run() {
                                while (true) {
                                    try {
                                        Log.d("andbtcouchdbrepli", "mServerThread: tcpsrvsock accept wait");
                                        Socket tcpsock = tcpsrvsock.accept();
                                        Log.d("andbtcouchdbrepli", "mServerThread: tcpsrvsock accepted tcpsock="+tcpsock.toString());

                                        BluetoothDevice btdev = mBluetoothAdapter.getRemoteDevice(mRemoteBTAddr);
                                
                                        final BluetoothSocket btsock = btdev.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);
                                        Thread t = new Thread(new Runnable() { public void run() {
                                            try {
                                                btsock.connect();
                                                Log.d("andbtcouchdbrepli", "mServerThread: connected btsock="+btsock.toString());
                                            } catch (Exception e) {
                                                Log.d("andbtcouchdbrepli", "btsock.connect", e);
                                            }
                                        }});
                                        t.start();
                                        t.join(10000);
                                        t.interrupt();
                                        
                                        startCommThreads("i'm bluetooth client", btsock, tcpsock);
                                    } catch (Exception e) {
                                        Log.d("andbtcouchdbrepli", "bt->local tcp proxy", e);
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
                                    
                                    Log.d("andbtcouchdbrepli", "mServerThread: sync with "+btaddr);
                                    mRemoteBTAddr = btaddr;

                                    HttpClient httpclient = new DefaultHttpClient();
                                    HttpPost httppost = new HttpPost("http://localhost:"+LOCAL_COUCHDB_PORT+"/_replicate");
                                    httppost.setHeader("Content-Type", "application/json");
                                    String s = "{\"source\": \""+DBNAME+"\", \"target\": \"http://localhost:20001/"+DBNAME+"\", \"create_target\": true} ";
                                    Log.d("andbtcouchdbrepli", "request to local couchdb: "+s);
                                    httppost.setEntity(new StringEntity(s));
                                    HttpResponse response = httpclient.execute(httppost);
                                    byte[] d = new byte[1024];
                                    int br = response.getEntity().getContent().read(d);
                                    Log.d("andbtcouchdbrepli", "response from local couchdb: "+new String(d, 0, br));

                                    mRemoteBTAddr = null;

                                } catch (Exception e) {
                                    Log.d("andbtcouchdbrepli", "mServerThread: error while sync with "+btaddr, e);
                                }
                            }
                        } catch (Exception e) {
                            Log.d("andbtcouchdbrepli", "mServerThread: sync error", e);
                        }
                        mHandler.postDelayed(this, SYNC_PERIOD);
                    } }, 3000);
                    
                    Looper.loop();
                    Log.d("andbtcouchdbrepli", "mServerThread: exit");
                    
                } catch (Exception e) {
                    Log.d("andbtcouchdbrepli", "mServerThread", e);
                }
            }});
            mServerThread.start();
        }
                
    }

    protected void startCommThreads(final String xfrctx, final BluetoothSocket btsock, final Socket tcpsock)
    {
        Thread remote2LocalThread = new Thread(new Runnable() { public void run() {
            try {
                byte[] d = new byte[SOCKET_BUFFER_SIZE];
                while (true) {
                    int br = btsock.getInputStream().read(d);
                    //Log.d("andbtcouchdbrepli", "remote2LocalThread: br="+br);
                    if (br <= 0)
                        break;
                    tcpsock.getOutputStream().write(d, 0, br);
                    tcpsock.getOutputStream().flush();
                }
            } catch (Exception e) {
                Log.d("andbtcouchdbrepli", "remote2LocalThread read-write loop", e);
            }
            try { btsock.close(); } catch (Exception e) { Log.d("andbtcouchdbrepli", "btsock.close", e); }
            try { tcpsock.close(); } catch (Exception e) { Log.d("andbtcouchdbrepli", "tcpsock.close", e); }
            Log.d("andbtcouchdbrepli", "remote2LocalThread: closed ("+xfrctx+")");
        }});
        remote2LocalThread.start();

        Thread local2RemoteThread = new Thread(new Runnable() { public void run() {
            try {
                byte[] d = new byte[SOCKET_BUFFER_SIZE];
                while (true) {
                    int br = tcpsock.getInputStream().read(d);
                    //Log.d("andbtcouchdbrepli", "local2RemoteThread: br="+br);
                    if (br <= 0)
                        break;
                    btsock.getOutputStream().write(d, 0, br);
                    btsock.getOutputStream().flush();
                }
            } catch (Exception e) {
                Log.d("andbtcouchdbrepli", "local2RemoteThread read-write loop", e);
            }
            try { btsock.close(); } catch (Exception e) { Log.d("andbtcouchdbrepli", "btsock.close", e); }
            try { tcpsock.close(); } catch (Exception e) { Log.d("andbtcouchdbrepli", "tcpsock.close", e); }
            Log.d("andbtcouchdbrepli", "local2RemoteThread: closed ("+xfrctx+")");
        }});
        local2RemoteThread.start();
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
