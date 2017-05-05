package com.aimu.datacollector.datacollector;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.widget.Toast;

import com.aimu.datacollector.ui.MainActivity;
import com.aimu.datacollector.wrapper.DataWrapper;
import com.aimu.datacollector.wrapper.TimeVector3;
import com.aimu.datacollector.wrapper.WiFiData;
import com.aimu.datacollector.wrapper.WiFiDataList;

import java.io.File;
import java.security.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class SensorHandlerService extends Service implements SensorEventListener
{
    public static SensorHandlerService instance;

    private CollectParameter collectParameter;
    private DataWrapper dataWrapper = null;

    private WifiManager wifiManager = null;
    private boolean isWifiScanning = false;
    private ArrayList<WiFiDataList> wiFiDataList = null;
    private Timer wifiTimer;

    private SensorManager manager;

    public SensorHandlerService()
    {
        instance = this;
    }

    public static SensorHandlerService instance()
    {
        return instance;
    }

    /**
     * 用收集参数cp开始进行数据收集
     * @param cp 收集参数
     */
    public void Start(CollectParameter cp)
    {
        this.dataWrapper = new DataWrapper();
        this.dataWrapper.timeStamp = System.currentTimeMillis();
        this.wiFiDataList = new ArrayList<>();

        this.collectParameter = cp;
        this.dataWrapper.floor = cp.floor;
        this.dataWrapper.mapPoint = cp.mapLocation;

        //开启wifi定时扫描
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        wifiTimer = new Timer();
        wifiTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                //加一个同步机制
                if (isWifiScanning == false)
                {
                    isWifiScanning = true;
                    //wifiManager.startScan();
                    List<ScanResult> lsr = wifiManager.getScanResults();
                    if (lsr == null)
                    {
                        Toast.makeText(MainActivity.instance, "wifi未打开！", Toast.LENGTH_LONG).show();
                    }
                    else
                    {
                        WiFiDataList wdl = new WiFiDataList();
                        wdl.time = System.currentTimeMillis();
                        for (ScanResult sr : lsr)
                        {
                            WiFiData wd = new WiFiData(sr.SSID, sr.BSSID, sr.level);
                            wdl.wiFiDataList.add(wd);
                        }
                        wiFiDataList.add(wdl);
                    }
                    isWifiScanning = false;
                }
            }
        //每隔wifiDelay毫秒，进行一次扫描
        }, 0, collectParameter.wifiDelay);

        //开启传感器数据收集
        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (collectParameter.hasAccSensor)
            manager.registerListener(this, manager.getDefaultSensor(collectParameter.accSensorType), collectParameter.accSensorDelay);
        if (collectParameter.hasGyroSensor)
            manager.registerListener(this, manager.getDefaultSensor(collectParameter.gyroSensorType), collectParameter.gyroSensorDelay);
        if (collectParameter.hasMagSensor)
            manager.registerListener(this, manager.getDefaultSensor(collectParameter.magSensorType), collectParameter.magSensorDelay);
    }

    /**
     * 完成数据收集并进行处理和保存
     */
    public void FinishCollect()
    {
        wifiTimer.cancel();

        manager.unregisterListener(this, manager.getDefaultSensor(collectParameter.accSensorType));
        manager.unregisterListener(this, manager.getDefaultSensor(collectParameter.gyroSensorType));
        manager.unregisterListener(this, manager.getDefaultSensor(collectParameter.magSensorType));

        this.dataWrapper.wifiArray = this.wiFiDataList;

        //用机器名和时间作为文件名
        String machineName = android.os.Build.MODEL.replace(" ", "");
        SimpleDateFormat df = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        File outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), machineName + "_" + df.format(new Date()) + ".txt");

        //开一个异步操作来存文件
        new LoggingAsyncTask().execute(this.dataWrapper, outputFile);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        int sensorType = event.sensor.getType();

        float[] raw = event.values.clone();
        TimeVector3 tv3 = new TimeVector3(event.timestamp, raw[0], raw[1], raw[2]);

        if (sensorType == collectParameter.accSensorType)
        {
            dataWrapper.accArray.add(tv3);
        }
        else if (sensorType == collectParameter.gyroSensorType)
        {
            dataWrapper.gyroArray.add(tv3);
        }
        else if (sensorType == collectParameter.magSensorType)
        {
            dataWrapper.magArray.add(tv3);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }
}
