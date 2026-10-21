package com.alpha.tvcontrol;

import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Keyboard with no keys: text typed on the phone page arrives over HTTP and is mirrored into the
 * focused field as composing text, so the TV shows it live and accents or emoji work.
 */
public class PhoneIme extends InputMethodService {
    private static volatile PhoneIme instance;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private volatile boolean inputActive;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        TextView bar = new TextView(this);
        bar.setText("Escribe desde tu celular  ·  Control TV → Teclado");
        bar.setTextSize(18);
        bar.setTextColor(Color.WHITE);
        bar.setBackgroundColor(0xE0111214);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, 18, 0, 18);
        return bar;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        inputActive = attribute != null && attribute.inputType != EditorInfo.TYPE_NULL;
    }

    @Override
    public void onFinishInput() {
        inputActive = false;
        super.onFinishInput();
    }

    /** True while a text field on the TV has focus. */
    static boolean active() {
        PhoneIme ime = instance;
        return ime != null && ime.inputActive;
    }

    /** Replaces the text being typed with the phone box's current value. */
    static boolean setText(String text) {
        return run(ime -> {
            InputConnection ic = ime.getCurrentInputConnection();
            if (ic != null) ic.setComposingText(text, 1);
        });
    }

    /** Commits the text and runs the field's action (search, go, next...). */
    static boolean submit() {
        return run(ime -> {
            InputConnection ic = ime.getCurrentInputConnection();
            if (ic == null) return;
            ic.finishComposingText();
            EditorInfo info = ime.getCurrentInputEditorInfo();
            int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
            } else {
                ime.sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER);
            }
        });
    }

    /** Deletes one character before the cursor, for text that was already committed. */
    static boolean backspace() {
        return run(ime -> {
            InputConnection ic = ime.getCurrentInputConnection();
            if (ic != null) ic.deleteSurroundingText(1, 0);
        });
    }

    private interface Action { void apply(PhoneIme ime); }

    private static boolean run(Action action) {
        PhoneIme ime = instance;
        if (ime == null) return false;
        CountDownLatch done = new CountDownLatch(1);
        MAIN.post(() -> {
            try { action.apply(ime); } finally { done.countDown(); }
        });
        try {
            return done.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
}
