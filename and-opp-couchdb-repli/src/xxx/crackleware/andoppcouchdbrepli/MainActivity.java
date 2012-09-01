package xxx.crackleware.andoppcouchdbrepli;

import android.app.Activity;
import android.os.Bundle;

import android.util.Log;

import android.os.Process;

import android.os.Handler;

import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import java.util.Date;

import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import android.content.Context;

public class MainActivity extends Activity
{
    Handler mHandler2;

    ScrollView scrlOutput;
    TextView tvOutput;
    int mTermLineCount = 0;

    protected void outputToTextView(final String s)
    {
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

    protected void output(final String s)
    {
        Log.d("andoppcouchdbrepli", s);
        outputToTextView(s);
    }

    private final BroadcastReceiver mServiceLogReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(ReplicationService.SERVICE_LOG.equals(intent.getAction())) {
                String s = intent.getExtras().getString("log");
                outputToTextView(s);
            }
        }
    };

    @Override
    protected void onResume()
    {
        Log.d("andoppcouchdbrepli", "onResume: called");

        registerReceiver(mServiceLogReceiver, new IntentFilter(ReplicationService.SERVICE_LOG));

        super.onRestart();
    }
    
    @Override
    protected void onPause()
    {
        Log.d("andoppcouchdbrepli", "onPause: called");
        
        unregisterReceiver(mServiceLogReceiver);
        
        super.onPause();
    }
    
    boolean mServiceStarted = false;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d("andoppcouchdbrepli", "onCreate: called");
        
        scrlOutput = (ScrollView)findViewById(R.id.scrlOutput);
        tvOutput = (TextView)findViewById(R.id.tvOutput);

        if (!mServiceStarted) {
            mServiceStarted = true;
            Context context = getApplicationContext();
            Intent svc_intent = new Intent(context, ReplicationService.class);
            context.startService(svc_intent);
            Log.d("andoppcouchdbrepli", "onCreate: ReplicationService started");
        }

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

    }

    @Override
    public void onDestroy() {
        Log.d("andoppcouchdbrepli", "onDestroy: called");
        super.onDestroy();
    }

}
