package com.alpha.tvcontrol;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Starts the service and shows the address to open on the phone. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startForegroundService(new Intent(this, KeyService.class));

        TextView text = new TextView(this);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(28);
        text.setTextColor(Color.WHITE);
        text.setBackgroundColor(Color.BLACK);
        text.setText("Control Web activo\n\nEn tu celular abre:\nhttp://" + localIp() + ":" + KeyService.PORT
                + "\n\n(pulsa Atrás para salir)");
        setContentView(text);
    }

    private String localIp() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ip = wifi == null ? 0 : wifi.getConnectionInfo().getIpAddress();
        if (ip == 0) return "<IP de la caja>";
        return (ip & 0xff) + "." + (ip >> 8 & 0xff) + "." + (ip >> 16 & 0xff) + "." + (ip >> 24 & 0xff);
    }
}
