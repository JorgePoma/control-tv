package com.alpha.tvcontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Small HTTP server on the local network. The phone opens the page at "/" and each button press
 * hits "/k?c=<linux keycode>&v=<1 down|0 up>". Keys are written to the IR receiver's evdev node, so
 * Android treats them exactly like the physical remote (OK = KEY_REPLY = DPAD_CENTER on this box).
 */
public class KeyService extends Service {
    static final int PORT = 8080;
    private static final String DEVICE = "/dev/input/event0";
    private static final String CHANNEL = "control";

    // Only the keys mapped in /vendor/usr/keylayout/ff1b0030_pwm.kl that the remote page uses.
    private static final Set<Integer> ALLOWED = new HashSet<>(Arrays.asList(
            103, 108, 105, 106, 232, 158, 102, 139, 115, 114, 113, 164, 14,
            2, 3, 4, 5, 6, 7, 8, 9, 10, 11));

    private static final Set<String> ICONS = new HashSet<>(Arrays.asList(
            "/icon-192.png", "/icon-512.png", "/apple-touch-icon.png"));

    private ServerSocket server;
    private FileOutputStream device;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Control Web", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Control Web activo")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();
        startForeground(1, n);

        Thread t = new Thread(this::serve, "control-http");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        try { if (device != null) device.close(); } catch (IOException ignored) { }
        super.onDestroy();
    }

    private void serve() {
        try {
            server = new ServerSocket(PORT);
            while (!server.isClosed()) {
                Socket s = server.accept();
                try {
                    handle(s);
                } catch (IOException ignored) {
                } finally {
                    try { s.close(); } catch (IOException ignored) { }
                }
            }
        } catch (IOException ignored) {
            stopSelf();
        }
    }

    private void handle(Socket s) throws IOException {
        s.setSoTimeout(3000);
        InetAddress peer = s.getInetAddress();
        if (!(peer.isSiteLocalAddress() || peer.isLoopbackAddress() || peer.isLinkLocalAddress())) return;

        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
        String line = in.readLine();
        if (line == null) return;
        // Drain the headers: closing with unread input resets the connection and the client loses the reply.
        for (String h = in.readLine(); h != null && !h.isEmpty(); h = in.readLine()) { }
        String[] parts = line.split(" ");
        if (parts.length < 2 || !parts[0].equals("GET")) {
            respond(s, "405 Method Not Allowed", "text/plain", new byte[0]);
            return;
        }
        String path = parts[1];

        if (path.equals("/")) {
            respond(s, "200 OK", "text/html; charset=utf-8", Page.HTML.getBytes(StandardCharsets.UTF_8));
        } else if (path.equals("/manifest.webmanifest")) {
            respond(s, "200 OK", "application/manifest+json", Page.MANIFEST.getBytes(StandardCharsets.UTF_8));
        } else if (ICONS.contains(path)) {
            respond(s, "200 OK", "image/png", asset(path.substring(1)));
        } else if (path.equals("/ime")) {
            respond(s, "200 OK", "application/json", ("{\"active\":" + PhoneIme.active() + "}").getBytes(StandardCharsets.US_ASCII));
        } else if (path.startsWith("/txt?")) {
            String query = path.substring(5);
            boolean ok;
            if (query.equals("done=1")) ok = PhoneIme.submit();
            else if (query.equals("del=1")) ok = PhoneIme.backspace();
            else if (query.startsWith("v=")) ok = PhoneIme.setText(java.net.URLDecoder.decode(query.substring(2), "UTF-8"));
            else ok = false;
            respond(s, ok ? "204 No Content" : "409 Conflict", "text/plain", new byte[0]);
        } else if (path.equals("/cur")) {
            String json = "{\"available\":" + CursorService.available() + ",\"on\":" + CursorService.isShown() + "}";
            respond(s, "200 OK", "application/json", json.getBytes(StandardCharsets.US_ASCII));
        } else if (path.startsWith("/cur?")) {
            boolean ok;
            if (path.equals("/cur?on=1")) ok = CursorService.setShown(true);
            else if (path.equals("/cur?on=0")) ok = CursorService.setShown(false);
            else if (path.equals("/cur?click=1")) ok = CursorService.click();
            else {
                int dx = intParam(path, "dx"), dy = intParam(path, "dy");
                ok = path.contains("dx=") && path.contains("dy=")
                        && dx >= -1 && dx <= 1 && dy >= -1 && dy <= 1 && CursorService.steer(dx, dy);
            }
            respond(s, ok ? "204 No Content" : "409 Conflict", "text/plain", new byte[0]);
        } else if (path.startsWith("/k?")) {
            int code = intParam(path, "c");
            int value = intParam(path, "v");
            if (!ALLOWED.contains(code) || (value != 0 && value != 1)) {
                respond(s, "400 Bad Request", "text/plain", new byte[0]);
                return;
            }
            boolean ok = sendKey(code, value);
            respond(s, ok ? "204 No Content" : "500 Internal Server Error", "text/plain", new byte[0]);
        } else {
            respond(s, "404 Not Found", "text/plain", new byte[0]);
        }
    }

    private byte[] asset(String name) throws IOException {
        try (java.io.InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static int intParam(String path, String name) {
        for (String kv : path.substring(path.indexOf('?') + 1).split("&")) {
            if (kv.startsWith(name + "=")) {
                try { return Integer.parseInt(kv.substring(name.length() + 1)); } catch (NumberFormatException e) { return -1; }
            }
        }
        return -1;
    }

    private static void respond(Socket s, String status, String type, byte[] body) throws IOException {
        OutputStream out = s.getOutputStream();
        String head = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    /** Writes EV_KEY then EV_SYN as struct input_event, sized for this process's ABI. */
    private synchronized boolean sendKey(int code, int value) {
        try {
            if (device == null) device = new FileOutputStream(DEVICE);
            device.write(event(1, code, value));
            device.write(event(0, 0, 0));
            device.flush();
            return true;
        } catch (IOException e) {
            try { if (device != null) device.close(); } catch (IOException ignored) { }
            device = null;
            return false;
        }
    }

    private static byte[] event(int type, int code, int value) {
        int timeSize = Process.is64Bit() ? 16 : 8;
        ByteBuffer b = ByteBuffer.allocate(timeSize + 8).order(ByteOrder.LITTLE_ENDIAN);
        b.position(timeSize);
        b.putShort((short) type).putShort((short) code).putInt(value);
        return b.array();
    }
}
