package com.ficosa.ble_master;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import static java.lang.Math.abs;

import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    public static final String TAG = "BLE CMS";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private int mState = UART_PROFILE_DISCONNECTED;
    private int delay_between_ble_comms_ms=75;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private ListView messageListView;
    private ArrayAdapter<String> listAdapter;
    private Button btnConnectDisconnect,btnSend, btn_L,btn_R,btn_zoom_mas,btn_zoom_menos, btn_default;
    private EditText edtMessage;
    private String device_address_1;
    private String device_address_2;
    private ImageView imageViewTouchPad;
    private ImageView imageViewJoystick;
    private SeekBar seekBar_sensitivity_joystick;
    private TextView textView_sensitivity_joystick;


    private boolean joystick_pressed = false;
    private AsyncTask asyncTask = null;
    private int pressed_x;
    private int pressed_y;

    /* Background task for joystick */
    /**********************************************************************************************/
    public void startTask_process_joystick() {
        asyncTask = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                if (joystick_pressed) {
                    while (joystick_pressed) {
                        try {
                            Thread.sleep(delay_between_ble_comms_ms);

                                    int local_x;
                                    int local_y;
                                    /* Limit the maximun movement of the joystick */

                                    if (pressed_x >  imageViewTouchPad.getWidth()-100)
                                    {
                                        local_x =  imageViewTouchPad.getWidth()-100;
                                    }
                                    else if (pressed_x <  100)
                                    {
                                        local_x =  100;
                                    }
                                    else
                                    {
                                        local_x=pressed_x;
                                    }

                                    if (pressed_y >  imageViewTouchPad.getHeight()-100)
                                    {
                                        local_y =  imageViewTouchPad.getHeight()-100;
                                    }

                                    else if (pressed_y <  100)
                                    {
                                        local_y =  100;
                                    }
                                    else
                                    {
                                        local_y=pressed_y;
                                    }

                                    /* Update position of Joystick imageView*/
                                    imageViewJoystick.setX(local_x-imageViewJoystick.getRight()/2);
                                    imageViewJoystick.setY(local_y-imageViewJoystick.getBottom()/2);


                                    /* Data from Android joystick*/
                                    int max_x=imageViewTouchPad.getRight();
                                    int max_y=imageViewTouchPad.getBottom();
                                    int min_x=imageViewTouchPad.getMinimumHeight();
                                    int min_y=imageViewTouchPad.getMinimumWidth();

                                    /* Data for calibration with real Feather joystick */
                                    short calibrated_min_x=5;
                                    short calibrated_max_x=1023;
                                    short calibrated_min_y=5;
                                    short calibrated_max_y=1023;
                                    short mean_zone_min=510;
                                    short mean_zone=510;
                                    short mean_zone_max=540;

                                    /* Format command */
                                    short pos_x=0;
                                    short neg_x=0;
                                    short pos_y=0;
                                    short neg_y=0;

                                    /* Calibration */
                                    double m_x= ((double) (calibrated_max_x - calibrated_min_x)) / ((double) (max_x - min_x));
                                    double b_x = calibrated_min_x - (m_x * ((double) min_x));//vertical shift
                                    double calibrated_x=m_x*local_x + b_x;

                                    double m_y= ((double) (calibrated_max_y - calibrated_min_y)) / ((double) (max_y - min_y));
                                    double b_y = calibrated_min_y - (m_y * ((double) min_y));//vertical shift
                                    double calibrated_y=m_y*local_y + b_y;


                                    if (calibrated_x>mean_zone_max)
                                    {
                                        pos_x=(short)((int)calibrated_x-mean_zone);

                                    }
                                    if (calibrated_x<mean_zone_min)
                                    {
                                        neg_x=(short)(mean_zone-(short)calibrated_x);

                                    }
                                    if (calibrated_y>mean_zone_max)
                                    {
                                        pos_y=(short)((short)calibrated_y-mean_zone);

                                    }
                                    if (calibrated_y<mean_zone_min)
                                    {
                                        neg_y=(short)(mean_zone-(short)calibrated_y);

                                    }

                                    /* Send command */
                                    send_ble_command(pos_x,neg_x,pos_y,neg_y,(short)0,(short)0,(short)0);



                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                imageViewJoystick.setX(0);
                imageViewJoystick.setY(0);
                return null;
            }

        }.execute();
    }



    /* onCreate */
    /**********************************************************************************************/
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        messageListView = (ListView) findViewById(R.id.listMessage);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        messageListView.setAdapter(listAdapter);
        messageListView.setDivider(null);
        btnConnectDisconnect=(Button) findViewById(R.id.btn_connect);
        btn_default=(Button) findViewById(R.id.btn_default);
        btnSend=(Button) findViewById(R.id.sendButton);
        btn_L=(Button)findViewById(R.id.btn_L);
        btn_R=(Button)findViewById(R.id.btn_R);
        btn_zoom_mas=(Button)findViewById(R.id.btn_zoom_mas);
        btn_zoom_menos=(Button)findViewById(R.id.btn_zoom_menos);
        imageViewTouchPad = (ImageView) findViewById(R.id.imageViewTouchPad);
        imageViewJoystick = (ImageView) findViewById(R.id.imageViewJoystick);
        edtMessage = (EditText) findViewById(R.id.sendText);
        seekBar_sensitivity_joystick=(SeekBar) findViewById(R.id.seekBar_sensitivity_joystick);;
        textView_sensitivity_joystick=(TextView) findViewById(R.id.textView_sensitivity_joystick);;
        textView_sensitivity_joystick.setText("75 ms");
        seekBar_sensitivity_joystick.setProgress(75);
        seekBar_sensitivity_joystick.setMax(300);


        service_init();




        /* SeekBar sensitivity joystick Left */
        /**********************************************************************************************/
        seekBar_sensitivity_joystick.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 0;

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
                textView_sensitivity_joystick.setText(Integer.toString(progress) + " ms");
                delay_between_ble_comms_ms = progress;
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        /* Button Left */
        /**********************************************************************************************/
        btn_L.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View v) {
             if((!device_address_1.equals(""))&&(!device_address_1.equals("-"))) {

                 mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device_address_1);
                 mService.connect(device_address_1);
             }
            }
         });

        /* Button Right */
        /**********************************************************************************************/
        btn_R.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if((!device_address_2.equals(""))&&(!device_address_2.equals("-"))) {

                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device_address_2);
                    mService.connect(device_address_2);
                }
            }
        });

        /* Button Zoom mas */
        /**********************************************************************************************/
        btn_zoom_mas.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send_ble_command((short)0,(short)0,(short)0,(short)0,(short)1,(short)0,(short)0);
            }
        });


        /* Button Default */
        /**********************************************************************************************/
        btn_default.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send_ble_command((short)0,(short)0,(short)0,(short)0,(short)0,(short)0,(short)1);
            }
        });

        /* Button Zoom menos */
        /**********************************************************************************************/
        btn_zoom_menos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send_ble_command((short)0,(short)0,(short)0,(short)0,(short)0,(short)1,(short)0);
            }
        });


        /* Joystick  */
        /* *********************************************************************************************/
        imageViewTouchPad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                /* Get touch position in coordinates from this imageView*/
                pressed_x = (int) event.getX();
                pressed_y = (int) event.getY();

          switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                            joystick_pressed  = true;
                            startTask_process_joystick();
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {

                        if ((pressed_x < 0)||(pressed_x > imageViewTouchPad.getWidth())||
                                (pressed_y < 0)||(pressed_y > imageViewTouchPad.getHeight())) {
                            joystick_pressed  = false;
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        joystick_pressed  = false;
                        return true;
                    }
                }

                return true;
            }
        });




        /* Button Enable/Scan/Disconnect */
        /**********************************************************************************************/
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (btnConnectDisconnect.getText().equals("Scan")) {
                    Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                    startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                } else if (btnConnectDisconnect.getText().equals("Disconnect")) {
                    if (mDevice != null) {
                        mBtAdapter.disable();
                        btnConnectDisconnect.setText("Enable");
                        btn_L.setEnabled(false);
                        btn_R.setEnabled(false);
                        btn_default.setEnabled(false);
                        btn_zoom_mas.setEnabled(false);
                        btn_zoom_menos.setEnabled(false);
                    }
                } else if (btnConnectDisconnect.getText().equals("Enable")) {

                    if (!mBtAdapter.isEnabled()) {
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    }
                    btnConnectDisconnect.setText("Scan");
                }

            }
        });
        /* Button Send */
        /**********************************************************************************************/
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	EditText editText = (EditText) findViewById(R.id.sendText);
            	String message = editText.getText().toString();

                send_ble_string_command(message);

            }
        });
     
        // Set initial UI state
        
    }

    /* UART service connected/disconnected */
    /**********************************************************************************************/
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
        		mService = ((UartService.LocalBinder) rawBinder).getService();
        		if (!mService.initialize()) {
                    finish();
                }

        }

        public void onServiceDisconnected(ComponentName classname) {
       ////     mService.disconnect(mDevice);
        		mService = null;
        }
    };

    /* Handler  */
    /**********************************************************************************************/

    private Handler mHandler = new Handler() {
        @Override
        
        //Handler events that received from UART service 
        public void handleMessage(Message msg) {

        }
    };

    /* Broadcast Receiver  */
    /**********************************************************************************************/
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final Intent mIntent = intent;

            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
            	 runOnUiThread(new Runnable() {
                     public void run() {
                         	String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                             btnConnectDisconnect.setText("Disconnect");
                             edtMessage.setEnabled(true);
                             btnSend.setEnabled(true);
                          //   ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                             listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                             mState = UART_PROFILE_CONNECTED;
                     }
            	 });
            }

            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
            	 runOnUiThread(new Runnable() {
                     public void run() {
                    	 	 String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            // btnConnectDisconnect.setText("Enable");
                             edtMessage.setEnabled(false);
                             btnSend.setEnabled(false);
                           //  ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                             listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                             mState = UART_PROFILE_DISCONNECTED;
                             mService.close();
                            //setUiState();
                     }
                 });
            }

            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
             	 mService.enableTXNotification();
            }
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
              
                 final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                 runOnUiThread(new Runnable() {
                     public void run() {
                         try {
                         	String text = new String(txValue, "UTF-8");
                         	String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        	 	listAdapter.add("["+currentDateTimeString+"] RX: "+text);
                        	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                        	
                         } catch (Exception e) {
                         }
                     }
                 });
             }
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
            	showMessage("Device doesn't support UART. Disconnecting");
            	mService.disconnect();
            }
            
            
        }
    };

    /* service_init  */
    /**********************************************************************************************/
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
  
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    /* makeGattUpdateIntentFilter */
    /**********************************************************************************************/
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    /* onStart */
    /**********************************************************************************************/
    @Override
    public void onStart() {
        super.onStart();
    }

    /* onDestroy */
    /**********************************************************************************************/
    @Override
    public void onDestroy() {
    	 super.onDestroy();
        
        try {
        	LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
        } 
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
       
    }

    /* onStop */
    /**********************************************************************************************/
    @Override
    protected void onStop() {
        super.onStop();
    }

    /* onPause */
    /**********************************************************************************************/
    @Override
    protected void onPause() {
        super.onPause();
    }

    /* onRestart */
    /**********************************************************************************************/
    @Override
    protected void onRestart() {
        super.onRestart();
    }

    /* onResume */
    /**********************************************************************************************/
    @Override
    public void onResume() {
        super.onResume();
       /* if (!mBtAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }*/
 
    }
    /* onConfigurationChanged */
    /**********************************************************************************************/
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /* onActivityResult */
    /**********************************************************************************************/
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

        case REQUEST_SELECT_DEVICE:

            if (resultCode == Activity.RESULT_OK && data != null) {

                device_address_1=data.getStringExtra("DEVICE_1_ADDRESS");
                device_address_2=data.getStringExtra("DEVICE_2_ADDRESS");

                if(device_address_1.equals("-"))
                {
                    btn_L.setEnabled(false);
                   }
                else
                {
                    btn_L.setEnabled(true);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device_address_1);
                    mService.connect(device_address_1);
                }

                if(device_address_2.equals("-"))
                {
                    btn_R.setEnabled(false);
                }
                else
                {
                    btn_R.setEnabled(true);
                }
                if(device_address_1.equals("-")&&device_address_2.equals("-"))
                {
                    btn_zoom_mas.setEnabled(false);
                    btn_zoom_menos.setEnabled(false);
                    btn_default.setEnabled(false);

                }
                else
                {
                    btn_zoom_mas.setEnabled(true);
                    btn_zoom_menos.setEnabled(true);
                    btn_default.setEnabled(true);
                }

            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

            } else {
                // User did not enable Bluetooth or an error occurred
                Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
        default:
            break;
        }
    }

    /* onCheckedChanged */
    /**********************************************************************************************/
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
       
    }

    /* showMessage */
    /**********************************************************************************************/
    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
  
    }
    /* onBackPressed */
    /**********************************************************************************************/
    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("BLE's running in background.");
        }
        else {
            new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.popup_title)
            .setMessage(R.string.popup_message)
            .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
   	                finish();
                }
            })
            .setNegativeButton(R.string.popup_no, null)
            .show();
        }
    }

    /* send_ble_command */
    /**********************************************************************************************/
    void send_ble_command(short pos_x,short neg_x,short pos_y, short neg_y,short btn_zoom_mas, short btn_zoom_menos,short btn_default) {

        byte[] bytes_pos_x;
        byte[] bytes_neg_x;
        byte[] bytes_pos_y;
        byte[] bytes_neg_y;
        byte command[];

        try {

            bytes_pos_x =ShortToByteArray(pos_x);
            bytes_neg_x = ShortToByteArray(neg_x);
            bytes_pos_y = ShortToByteArray(pos_y);
            bytes_neg_y = ShortToByteArray(neg_y);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            outputStream.write( bytes_pos_x );
            outputStream.write( bytes_neg_x );
            outputStream.write( bytes_pos_y );
            outputStream.write( bytes_neg_y );
            outputStream.write( (byte)btn_zoom_mas );
            outputStream.write( (byte)btn_zoom_menos );
            outputStream.write( (byte)btn_default );
            outputStream.write( (byte)58);
            command= outputStream.toByteArray();


            mService.writeRXCharacteristic(command);
            //Update the log with time stamp
            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            listAdapter.add("[" + currentDateTimeString + "] TX: " + byteArrayToHex(command));
            messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
            edtMessage.setText("");

        } catch (Exception  e) {
            e.printStackTrace();
        }

    }
    /* send_ble_string_command */
    /**********************************************************************************************/
    void send_ble_string_command(String message) {

        byte[] value;
        try {
            //send data to service
            value = message.getBytes("UTF-8");
            mService.writeRXCharacteristic(value);
            //Update the log with time stamp
            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            listAdapter.add("[" + currentDateTimeString + "] TX: " + message);
            messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
            edtMessage.setText("");

        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    /* ShortToByteArray */
    /**********************************************************************************************/
    public static final byte[] ShortToByteArray(short value) {
        return new byte[] {
                (byte)(value >>> 8),
                (byte)value};
    }
    /* byteArrayToHex */
    /**********************************************************************************************/
    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
