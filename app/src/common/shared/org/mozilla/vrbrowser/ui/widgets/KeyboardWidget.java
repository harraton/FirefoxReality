/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.os.Handler;
import android.os.LocaleList;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStack;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.input.CustomKeyboard;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.keyboards.ItalianKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.FrenchKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.GermanKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.ChineseZhuyinKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.JapaneseKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.KeyboardInterface;
import org.mozilla.vrbrowser.ui.keyboards.RussianKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.KoreanKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.SpanishKeyboard;
import org.mozilla.vrbrowser.ui.views.AutoCompletionView;
import org.mozilla.vrbrowser.ui.views.CustomKeyboardView;
import org.mozilla.vrbrowser.ui.views.LanguageSelectorView;
import org.mozilla.vrbrowser.ui.widgets.dialogs.VoiceSearchWidget;
import org.mozilla.vrbrowser.ui.keyboards.ChinesePinyinKeyboard;
import org.mozilla.vrbrowser.ui.keyboards.EnglishKeyboard;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.ArrayList;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public class KeyboardWidget extends UIWidget implements CustomKeyboardView.OnKeyboardActionListener, AutoCompletionView.Delegate,
        GeckoSession.TextInputDelegate, WidgetManagerDelegate.FocusChangeListener, VoiceSearchWidget.VoiceSearchDelegate, TextWatcher {

    private static final String LOGTAG = "VRB";

    private static int MAX_CHARS_PER_POPUP_LINE = 10;

    private CustomKeyboardView mKeyboardView;
    private CustomKeyboardView mKeyboardNumericView;
    private CustomKeyboardView mPopupKeyboardView;
    private ArrayList<KeyboardInterface> mKeyboards;
    private KeyboardInterface mCurrentKeyboard;
    private CustomKeyboard mDefaultKeyboardSymbols;
    private CustomKeyboard mKeyboardNumeric;
    private Drawable mShiftOnIcon;
    private Drawable mShiftOffIcon;
    private Drawable mCapsLockOnIcon;
    private View mFocusedView;
    private LinearLayout mKeyboardLayout;
    private RelativeLayout mKeyboardContainer;
    private UIWidget mAttachedWindow;
    private InputConnection mInputConnection;
    private EditorInfo mEditorInfo = new EditorInfo();
    private VoiceSearchWidget mVoiceSearchWidget;
    private AutoCompletionView mAutoCompletionView;
    private LanguageSelectorView mLanguageSelectorView;

    private int mKeyWidth;
    private int mKeyboardPopupTopMargin;
    private ImageButton mCloseKeyboardButton;
    private ImageButton mKeyboardMoveButton;
    private boolean mIsLongPress;
    private boolean mIsMultiTap;
    private boolean mIsCapsLock;
    private ImageView mPopupKeyboardLayer;
    private boolean mIsInVoiceInput = false;
    private String mComposingText = "";
    private String mComposingDisplayText = "";
    private boolean mInternalDeleteHint = false;
    private SessionStack mSessionStack;

    private class MoveTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    mWidgetManager.startWidgetMove(KeyboardWidget.this, WidgetManagerDelegate.WIDGET_MOVE_BEHAVIOUR_KEYBOARD);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    mWidgetManager.finishWidgetMove();
                    break;
                default:
                    return false;

            }
            return true;
        }
    }

    private class MoveHoverListener implements OnHoverListener {
        @Override
        public boolean onHover(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    v.setHovered(true);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    v.setHovered(false);
            }
            return false;
        }
    }

    public KeyboardWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public KeyboardWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public KeyboardWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }


    private void initialize(Context aContext) {
        inflate(aContext, R.layout.keyboard, this);

        mWidgetManager.addFocusChangeListener(this);

        mKeyboardView = findViewById(R.id.keyboard);
        mKeyboardNumericView = findViewById(R.id.keyboardNumeric);
        mPopupKeyboardView = findViewById(R.id.popupKeyboard);
        mPopupKeyboardLayer = findViewById(R.id.popupKeyboardLayer);
        mLanguageSelectorView = findViewById(R.id.langSelectorView);
        mKeyboardLayout = findViewById(R.id.keyboardLayout);
        mKeyboardContainer = findViewById(R.id.keyboardContainer);
        mLanguageSelectorView.setDelegate(aItem -> handleLanguageChange((KeyboardInterface) aItem.tag));
        mAutoCompletionView = findViewById(R.id.autoCompletionView);
        mAutoCompletionView.setExtendedHeight((int)(mWidgetPlacement.height * mWidgetPlacement.density));
        mAutoCompletionView.setDelegate(this);

        mKeyboards = new ArrayList<>();
        mKeyboards.add(new EnglishKeyboard(aContext));
        mKeyboards.add(new ItalianKeyboard(aContext));
        mKeyboards.add(new FrenchKeyboard(aContext));
        mKeyboards.add(new GermanKeyboard(aContext));
        mKeyboards.add(new SpanishKeyboard(aContext));
        mKeyboards.add(new RussianKeyboard(aContext));
        mKeyboards.add(new ChinesePinyinKeyboard(aContext));
        mKeyboards.add(new ChineseZhuyinKeyboard(aContext));
        mKeyboards.add(new KoreanKeyboard(aContext));
        mKeyboards.add(new JapaneseKeyboard(aContext));

        mDefaultKeyboardSymbols = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_symbols);
        mKeyboardNumeric = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_numeric);
        setDefaultKeyboard();

        mKeyboardView.setPreviewEnabled(false);
        mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
        mPopupKeyboardView.setPreviewEnabled(false);
        mPopupKeyboardView.setKeyBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_background));
        mPopupKeyboardView.setKeyCapStartBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_capstart_background));
        mPopupKeyboardView.setKeyCapEndBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_capend_background));
        mPopupKeyboardView.setKeySingleStartBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_single_background));
        mPopupKeyboardView.setKeyTextColor(getContext().getColor(R.color.asphalt));
        mPopupKeyboardView.setSelectedForegroundColor(getContext().getColor(R.color.fog));
        mPopupKeyboardView.setForegroundColor(getContext().getColor(R.color.fog));
        mPopupKeyboardView.setKeyboardHoveredPadding(0);
        mPopupKeyboardView.setKeyboardPressedPadding(0);

        mKeyboardNumericView.setPreviewEnabled(false);

        int[] featuredKeys = {
            ' ', Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_DONE, Keyboard.KEYCODE_CANCEL, Keyboard.KEYCODE_MODE_CHANGE,
            CustomKeyboard.KEYCODE_VOICE_INPUT, CustomKeyboard.KEYCODE_SYMBOLS_CHANGE, CustomKeyboard.KEYCODE_LANGUAGE_CHANGE,
        };
        mKeyboardView.setFeaturedKeyBackground(R.drawable.keyboard_featured_key_background, featuredKeys);

        mKeyboardView.setOnKeyListener((view, i, keyEvent) -> false);
        mPopupKeyboardView.setOnKeyListener((view, i, keyEvent) -> false);
        mKeyboardNumericView.setOnKeyListener((view, i, keyEvent) -> false);
        mKeyboardView.setOnKeyboardActionListener(this);
        mPopupKeyboardView.setOnKeyboardActionListener(this);
        mKeyboardNumericView.setOnKeyboardActionListener(this);

        mShiftOnIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_on, getContext().getTheme());
        mShiftOffIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_off, getContext().getTheme());
        mCapsLockOnIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_caps, getContext().getTheme());
        mCloseKeyboardButton = findViewById(R.id.keyboardCloseButton);
        mCloseKeyboardButton.setOnClickListener(v -> dismiss());
        mKeyboardMoveButton = findViewById(R.id.keyboardMoveButton);
        mKeyboardMoveButton.setOnTouchListener(new MoveTouchListener());
        mKeyboardMoveButton.setOnHoverListener(new MoveHoverListener());
        mKeyWidth = getResources().getDimensionPixelSize(R.dimen.keyboard_key_width);
        mKeyboardPopupTopMargin  = getResources().getDimensionPixelSize(R.dimen.keyboard_key_pressed_padding) * 2;

        setOnClickListener(view -> hideOverlays());
        mPopupKeyboardLayer.setOnClickListener(view -> hideOverlays());

        mKeyboardView.setVisibility(View.VISIBLE);
        mKeyboardNumericView.setKeyboard(mKeyboardNumeric);
        hideOverlays();

        mBackHandler = () -> onDismiss();

        mAutoCompletionView = findViewById(R.id.autoCompletionView);
        mAutoCompletionView.setExtendedHeight((int)(mWidgetPlacement.height * mWidgetPlacement.density));
        mAutoCompletionView.setDelegate(this);

        updateCandidates();
    }

    @Override
    public void releaseWidget() {
        detachFromWindow();
        mWidgetManager.removeFocusChangeListener(this);
        mAutoCompletionView.setDelegate(null);
        mAttachedWindow = null;
        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = getKeyboardWidth(WidgetPlacement.dpDimension(context, R.dimen.keyboard_alphabetic_width));
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.keyboard_height);
        aPlacement.height += WidgetPlacement.dpDimension(context, R.dimen.autocompletion_widget_line_height);
        aPlacement.height += WidgetPlacement.dpDimension(context, R.dimen.keyboard_layout_padding);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationX = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_x);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_y) - WidgetPlacement.unitFromMeters(context, R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_z) - WidgetPlacement.unitFromMeters(context, R.dimen.window_world_z);
        aPlacement.rotationAxisX = 1.0f;
        aPlacement.rotation = (float)Math.toRadians(WidgetPlacement.floatDimension(context, R.dimen.keyboard_world_rotation));
        aPlacement.worldWidth = WidgetPlacement.floatDimension(context, R.dimen.keyboard_world_width);
        aPlacement.visible = false;
        aPlacement.cylinder = true;
    }

    @Override
    public void detachFromWindow() {
        if (mSessionStack != null) {
            mSessionStack.removeTextInputListener(this);
            mSessionStack = null;
        }
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (mAttachedWindow == aWindow) {
            return;
        }
        mAttachedWindow = aWindow;
        mWidgetPlacement.parentHandle = aWindow.getHandle();

        mSessionStack = aWindow.getSessionStack();
        if (mSessionStack != null) {
            mSessionStack.addTextInputListener(this);
        }
    }

    private int getKeyboardWidth(float aAlphabeticWidth) {
        float width = aAlphabeticWidth;
        width += WidgetPlacement.dpDimension(getContext(), R.dimen.keyboard_layout_padding) * 2;
        width += WidgetPlacement.dpDimension(getContext(), R.dimen.keyboard_numeric_width);
        width += WidgetPlacement.dpDimension(getContext(), R.dimen.keyboard_key_width); // Close button
        return (int) width;
    }

    private void resetKeyboardLayout() {
        if ((mEditorInfo.inputType & EditorInfo.TYPE_CLASS_NUMBER) == EditorInfo.TYPE_CLASS_NUMBER) {
            mKeyboardView.setKeyboard(getSymbolsKeyboard());
        } else {
            mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
        }
        handleShift(false);
        updateCandidates();
    }

    public void updateFocusedView(View aFocusedView) {
        if (mFocusedView != null && mFocusedView instanceof TextView) {
            ((TextView)mFocusedView).removeTextChangedListener(this);
        }
        mFocusedView = aFocusedView;
        if (aFocusedView != null && aFocusedView.onCheckIsTextEditor()) {
            mInputConnection = aFocusedView.onCreateInputConnection(mEditorInfo);
            resetKeyboardLayout();
            if (mFocusedView != null && mFocusedView instanceof TextView) {
                ((TextView)mFocusedView).addTextChangedListener(this);
            }
        } else {
            cleanComposingText();
            hideOverlays();
            mInputConnection = null;
        }

        boolean showKeyboard = mInputConnection != null;
        boolean keyboardIsVisible = this.getVisibility() == View.VISIBLE;
        if (showKeyboard != keyboardIsVisible) {
            if (showKeyboard) {
                mWidgetManager.pushBackHandler(mBackHandler);
            } else {
                mWidgetManager.popBackHandler(mBackHandler);
                mWidgetManager.keyboardDismissed();
            }
            getPlacement().visible = showKeyboard;
            mWidgetManager.updateWidget(this);
        }

        mCurrentKeyboard.clear();
        updateCandidates();
        updateSpecialKeyLabels();
    }

    public void dismiss() {
        exitVoiceInputMode();
       if (mFocusedView != null && mFocusedView != mAttachedWindow) {
           mFocusedView.clearFocus();
       }
       mWidgetPlacement.visible = false;
       mWidgetManager.updateWidget(this);

       mWidgetManager.popBackHandler(mBackHandler);

       mIsCapsLock = false;
       mIsLongPress = false;
       handleShift(false);
       hideOverlays();
    }

    private void hideOverlays() {
        mPopupKeyboardView.setVisibility(View.GONE);
        mPopupKeyboardLayer.setVisibility(View.GONE);
        mLanguageSelectorView.setVisibility(View.GONE);
    }

    protected void onDismiss() {
        dismiss();
    }

    @Override
    public void onPress(int primaryCode) {
        Log.d("VRB", "Keyboard onPress " + primaryCode);
    }

    @Override
    public void onLongPress(Keyboard.Key popupKey) {
        if (popupKey.popupCharacters != null) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);

            float totalWidth = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getAlphabeticKeyboardWidth());
            totalWidth -= mKeyboardView.getPaddingLeft() + mKeyboardView.getPaddingRight();
            float keyX = popupKey.x + WidgetPlacement.convertDpToPixel(getContext(), mKeyWidth * 0.5f);
            boolean rightAligned = keyX >= totalWidth * 0.5f;
            StringBuilder popupCharacters = new StringBuilder(popupKey.popupCharacters);

            if (rightAligned) {
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.rightMargin = mKeyboardView.getWidth() - popupKey.x - mKeyWidth - mKeyboardNumericView.getPaddingRight();
                if (popupCharacters.length() > MAX_CHARS_PER_POPUP_LINE) {
                    popupCharacters.insert(MAX_CHARS_PER_POPUP_LINE - 1, popupCharacters.charAt(0));
                    popupCharacters.replace(0,1, String.valueOf(popupCharacters.charAt(popupCharacters.length()-1)));
                    popupCharacters.deleteCharAt(popupCharacters.length()-1);
                } else {
                    popupCharacters.reverse();
                }
            } else {
                params.leftMargin = popupKey.x + mKeyboardNumericView.getPaddingLeft();
            }
            params.topMargin = popupKey.y + mKeyboardPopupTopMargin + mKeyboardView.getPaddingTop();

            CustomKeyboard popupKeyboard = new CustomKeyboard(getContext(), popupKey.popupResId,
                    popupCharacters, MAX_CHARS_PER_POPUP_LINE, 0, getContext().getResources().getDimensionPixelSize(R.dimen.keyboard_vertical_gap));
            mPopupKeyboardView.setKeyboard(popupKeyboard);
            mPopupKeyboardView.setLayoutParams(params);
            mPopupKeyboardView.setShifted(mIsCapsLock || mKeyboardView.isShifted());
            mPopupKeyboardView.setVisibility(View.VISIBLE);
            mPopupKeyboardLayer.setVisibility(View.VISIBLE);

            mIsLongPress = true;

        } else if (popupKey.codes[0] == CustomKeyboard.KEYCODE_SHIFT) {
            mIsLongPress = !mIsCapsLock;

        } else if (popupKey.codes[0] == Keyboard.KEYCODE_DELETE) {
            handleBackspace(true);
        }
    }

    public void onMultiTap(Keyboard.Key key) {
        mIsMultiTap = true;
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes, boolean hasPopup) {
        Log.d("VRB", "Keyboard onPress++ " + primaryCode);
        switch (primaryCode) {
            case Keyboard.KEYCODE_MODE_CHANGE:
                handleModeChange();
                break;
            case Keyboard.KEYCODE_SHIFT:
                mIsCapsLock = mIsLongPress || mIsMultiTap;
                handleShift(!mKeyboardView.isShifted());
                break;
            case Keyboard.KEYCODE_DELETE:
                handleBackspace(false);
                break;
            case Keyboard.KEYCODE_DONE:
                handleDone();
                break;
            case CustomKeyboard.KEYCODE_VOICE_INPUT:
                handleVoiceInput();
                break;
            case CustomKeyboard.KEYCODE_LANGUAGE_CHANGE:
                handleGlobeClick();
                break;
            case CustomKeyboard.KEYCODE_EMOJI:
                handleEmojiInput();
                break;
            case ' ':
                handleSpace();
                break;
            default:
                if (!mIsLongPress) {
                    handleKey(primaryCode, keyCodes);
                }

                if (!hasPopup) {
                    hideOverlays();
                }
                break;
        }

        if (!mIsCapsLock && primaryCode != CustomKeyboard.KEYCODE_SHIFT && mPopupKeyboardView.getVisibility() != View.VISIBLE) {
            handleShift(false);
        }

        mIsLongPress = false;
        mIsMultiTap = false;
    }

    @Override
    public void onNoKey() {
       hideOverlays();
    }

    @Override
    public void onText(CharSequence text) {
        handleText(text.toString());
    }

    @Override
    public void swipeLeft() {

    }

    @Override
    public void swipeRight() {

    }

    @Override
    public void swipeDown() {

    }

    @Override
    public void swipeUp() {

    }

    private void setDefaultKeyboard() {
        KeyboardInterface keyboard = getKeyboardForLocale(SettingsStore.getInstance(getContext()).getKeyboardLocale());
        if (keyboard != null) {
            handleLanguageChange(keyboard);
            return;
        }

        // If the user has not selected any keyboard, find the best match from system locales.
        LocaleList localeList = getResources().getConfiguration().getLocales();
        String[] supportedLocales = new String[mKeyboards.size()];
        for (int i = 0; i < mKeyboards.size(); ++i) {
            supportedLocales[i] = mKeyboards.get(i).getLocale().toLanguageTag();
        }
        Locale bestMatch = localeList.getFirstMatch(supportedLocales);
        keyboard = getKeyboardForLocale(bestMatch);
        if (keyboard == null) {
            // Fall back to english.
            keyboard = getKeyboardForLocale(Locale.ENGLISH);
        }
        handleLanguageChange(keyboard);
    }

    private KeyboardInterface getKeyboardForLocale(@Nullable Locale aLocale) {
        if (aLocale == null) {
            return null;
        }
        // Check perfect locale mach
        for (KeyboardInterface keyboard: mKeyboards) {
            if (keyboard.getLocale().equals(aLocale)) {
                return  keyboard;
            }
        }
        // Fall back to language check
        for (KeyboardInterface keyboard: mKeyboards) {
            if (keyboard.getLocale().getLanguage().equalsIgnoreCase(aLocale.getLanguage())) {
                return keyboard;
            }
        }
        return null;
    }

    private CustomKeyboard getSymbolsKeyboard() {
        CustomKeyboard custom = mCurrentKeyboard.getSymbolsKeyboard();
        return  custom != null ? custom : mDefaultKeyboardSymbols;
    }

    private void cleanComposingText() {
        if (mComposingText.length() > 0 && mInputConnection != null) {
            mComposingText = "";
            // Clear composited text when the keyboard is dismissed
            postInputCommand(() -> {
                displayComposingText("", ComposingAction.FINISH);
            });
        }
    }

    private void handleShift(boolean isShifted) {
        CustomKeyboard keyboard = (CustomKeyboard) mKeyboardView.getKeyboard();
        boolean shifted = isShifted;
        int[] shiftIndices = keyboard.getShiftKeyIndices();
        for (int shiftIndex: shiftIndices) {
            if (shiftIndex >= 0) {
                // Update shift icon
                Keyboard.Key key = keyboard.getKeys().get(shiftIndex);
                if (key != null) {
                    if (mIsCapsLock) {
                        key.icon = mCapsLockOnIcon;
                        key.pressed = true;
                    } else {
                        key.icon = shifted ? mShiftOnIcon : mShiftOffIcon;
                        key.pressed = false;
                    }
                }
            }
        }
        mKeyboardView.setShifted(shifted || mIsCapsLock);
    }

    private void handleBackspace(final boolean isLongPress) {
        final InputConnection connection = mInputConnection;
        if (mComposingText.length() > 0) {
            CharSequence selectedText = mInputConnection.getSelectedText(0);
            if (selectedText != null && selectedText.length() > 0) {
                // Clean composing text if selected when backspace is clicked
                mComposingText = "";
            } else {
                mComposingText = mComposingText.substring(0, mComposingText.length() - 1);
                mComposingText = mComposingText.trim();
            }
            updateCandidates();
            return;
        }

        postInputCommand(() -> {
            CharSequence selectedText = connection.getSelectedText(0);
            if (selectedText != null && selectedText.length() > 0) {
                // Delete the selected text
                connection.commitText("", 1);
                return;
            }

            if (isLongPress) {
                CharSequence currentText = connection.getExtractedText(new ExtractedTextRequest(), 0).text;
                CharSequence beforeCursorText = connection.getTextBeforeCursor(currentText.length(), 0);
                CharSequence afterCursorText = connection.getTextAfterCursor(currentText.length(), 0);
                connection.deleteSurroundingText(beforeCursorText.length(), afterCursorText.length());
            } else {
                if (mCurrentKeyboard.usesTextOverride()) {
                    String beforeText = getTextBeforeCursor(connection);
                    String newBeforeText = mCurrentKeyboard.overrideBackspace(beforeText);
                    if (newBeforeText != null) {
                        // Replace whole before text
                        connection.deleteSurroundingText(beforeText.length(), 0);
                        connection.commitText(newBeforeText, 1);
                        return;
                    }
                }
                // Remove the character before the cursor.
                connection.deleteSurroundingText(1, 0);
            }
        });
    }

    private void handleGlobeClick() {
        if (mLanguageSelectorView.getItems() == null || mLanguageSelectorView.getItems().size() == 0) {
            ArrayList<LanguageSelectorView.Item> items = new ArrayList<>();
            for (KeyboardInterface keyboard: mKeyboards) {
                items.add(new LanguageSelectorView.Item(keyboard.getKeyboardTitle().toUpperCase(), keyboard));
            }
            mLanguageSelectorView.setItems(items);
        }
        mLanguageSelectorView.setSelectedItem(mCurrentKeyboard);
        mLanguageSelectorView.setVisibility(View.VISIBLE);
        mPopupKeyboardLayer.setVisibility(View.VISIBLE);
    }

    private void handleEmojiInput() {
        final KeyboardInterface.CandidatesResult candidates = mCurrentKeyboard.getEmojiCandidates(mComposingText);
        setAutoCompletionVisible(candidates != null && candidates.words.size() > 0);
        mAutoCompletionView.setItems(candidates != null ? candidates.words : null);
    }

    private void handleLanguageChange(KeyboardInterface aKeyboard) {
        cleanComposingText();

        mCurrentKeyboard = aKeyboard;
        final int width = getKeyboardWidth(mCurrentKeyboard.getAlphabeticKeyboardWidth());
        if (width != mWidgetPlacement.width) {
            mWidgetPlacement.width = width;
            float defaultWorldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.keyboard_world_width);
            int defaultKeyboardWidth = getKeyboardWidth(mKeyboards.get(0).getAlphabeticKeyboardWidth());
            mWidgetPlacement.worldWidth = defaultWorldWidth * ((float) width / (float) defaultKeyboardWidth);
            mWidgetManager.updateWidget(this);
            ViewGroup.LayoutParams params = mKeyboardContainer.getLayoutParams();
            params.width = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getAlphabeticKeyboardWidth());
            mKeyboardContainer.setLayoutParams(params);
        }

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)mKeyboardLayout.getLayoutParams();
        params.topMargin = mCurrentKeyboard.supportsAutoCompletion() ? WidgetPlacement.pixelDimension(getContext(), R.dimen.keyboard_margin_top_without_autocompletion) : 0;
        mKeyboardLayout.setLayoutParams(params);

        SettingsStore.getInstance(getContext()).setSelectedKeyboard(aKeyboard.getLocale());
        mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
        handleShift(false);
        hideOverlays();
        updateCandidates();
    }

    private void handleSpace() {
        if (mCurrentKeyboard.usesComposingText() && mComposingText.length() == 0) {
            // Do not compose text when space is clicked on an empty composed text
            final InputConnection connection = mInputConnection;
            postInputCommand(() -> connection.commitText(" ", 1));
        } else {
            handleText(" ");
        }
    }

    private void handleDone() {
        if (mComposingDisplayText.length() > 0) {
            // Finish current composing
            mComposingText = "";
            postInputCommand(() -> {
                displayComposingText(StringUtils.removeSpaces(mComposingDisplayText), ComposingAction.FINISH);
                postUICommand(this::updateCandidates);
            });
            return;
        }
        final InputConnection connection = mInputConnection;
        final int action = mEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        postInputCommand(() -> connection.performEditorAction(action));

        boolean hide = (action & (EditorInfo.IME_ACTION_DONE | EditorInfo.IME_ACTION_GO |
                                 EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_ACTION_SEND)) != 0;
        if (hide && mFocusedView != null) {
            mFocusedView.clearFocus();
        }
    }


    private void handleModeChange() {
        Keyboard current = mKeyboardView.getKeyboard();
        Keyboard alphabetic = mCurrentKeyboard.getAlphabeticKeyboard();
        mKeyboardView.setKeyboard(current == alphabetic ? getSymbolsKeyboard() : alphabetic);
        mKeyboardView.setLayoutParams(mKeyboardView.getLayoutParams());
    }

    private void handleKey(int primaryCode, int[] keyCodes) {
        if (mFocusedView == null || mInputConnection == null || primaryCode <= 0) {
            return;
        }

        String str = String.valueOf((char) primaryCode);
        if (mKeyboardView.isShifted() && Character.isLowerCase(str.charAt(0))) {
            str = str.toUpperCase();
        }
        handleText(str);
    }

    private void handleText(final String aText) {
        if (mFocusedView == null || mInputConnection == null) {
            return;
        }

        if (mCurrentKeyboard.usesComposingText()) {
            CharSequence seq = mInputConnection.getSelectedText(0);
            String selected = seq != null ? seq.toString() : "";
            if (selected.length() > 0 && StringUtils.removeSpaces(selected).contains(mComposingText)) {
                // Clean composing text if the text is selected.
                mComposingText = "";
            }
            mComposingText += aText;
        } else if (mCurrentKeyboard.usesTextOverride()) {
            String beforeText = getTextBeforeCursor(mInputConnection);
            final String newBeforeText = mCurrentKeyboard.overrideAddText(beforeText, aText);
            final InputConnection connection = mInputConnection;
            postInputCommand(() -> {
                if (newBeforeText != null) {
                    connection.deleteSurroundingText(beforeText.length(), 0);
                    connection.commitText(newBeforeText, 1);
                } else {
                    connection.commitText(aText, 1);
                }
            });

        } else {
            final InputConnection connection = mInputConnection;
            postInputCommand(() -> connection.commitText(aText, 1));
        }
        updateCandidates();
    }

    private void handleVoiceInput() {
        if (mIsInVoiceInput) {
            return;
        }
        if (mVoiceSearchWidget == null) {
            mVoiceSearchWidget = createChild(VoiceSearchWidget.class, false);
            mVoiceSearchWidget.setPlacementForKeyboard(this.getHandle());
            mVoiceSearchWidget.setDelegate(this); // VoiceSearchDelegate
            mVoiceSearchWidget.setDelegate(() -> exitVoiceInputMode()); // DismissDelegate
        }
        mIsInVoiceInput = true;
        TelemetryWrapper.voiceInputEvent();
        mVoiceSearchWidget.show(CLEAR_FOCUS);
        mWidgetPlacement.visible = false;
        mWidgetManager.updateWidget(this);
    }

    private String getTextBeforeCursor(InputConnection aConnection) {
        if (aConnection == null) {
            return "";
        }

        String fullText = aConnection.getExtractedText(new ExtractedTextRequest(),0).text.toString();
        return aConnection.getTextBeforeCursor(fullText.length(),0).toString();
    }

    private void postInputCommand(Runnable aRunnable) {
        if (mInputConnection == null) {
            Log.e(LOGTAG, "InputConnection command not submitted, mInputConnection was null");
            return;
        }

        Handler handler = mInputConnection.getHandler();
        if (handler != null) {
            handler.post(aRunnable);
        } else {
            aRunnable.run();
        }
    }

    private void postUICommand(Runnable aRunnable) {
        ((Activity)getContext()).runOnUiThread(aRunnable);
    }

    private void updateCandidates() {
        if (mInputConnection == null || !mCurrentKeyboard.supportsAutoCompletion()) {
            setAutoCompletionVisible(false);
            updateSpecialKeyLabels();
            return;
        }

        if (mCurrentKeyboard.usesComposingText()) {
            final KeyboardInterface.CandidatesResult candidates = mCurrentKeyboard.getCandidates(mComposingText);
            setAutoCompletionVisible(candidates != null && candidates.words.size() > 0);
            mAutoCompletionView.setItems(candidates != null ? candidates.words : null);
            if (candidates != null && candidates.action == KeyboardInterface.CandidatesResult.Action.AUTO_COMPOSE) {
                onAutoCompletionItemClick(candidates.words.get(0));
            } else if (candidates != null) {
                postInputCommand(() -> displayComposingText(candidates.composing, ComposingAction.DO_NOT_FINISH));
            } else {
                mComposingText = "";

                postInputCommand(() -> {
                    displayComposingText("", ComposingAction.FINISH);
                });
            }
        } else {
            String fullText = mInputConnection.getExtractedText(new ExtractedTextRequest(),0).text.toString();
            String beforeText = mInputConnection.getTextBeforeCursor(fullText.length(),0).toString();
            final KeyboardInterface.CandidatesResult candidates = mCurrentKeyboard.getCandidates(beforeText);
            setAutoCompletionVisible(candidates != null && candidates.words.size() > 0);
            mAutoCompletionView.setItems(candidates != null ? candidates.words : null);
        }

        updateSpecialKeyLabels();
    }

    private void updateSpecialKeyLabels() {
        String spaceText = mCurrentKeyboard.getSpaceKeyText(mComposingText);
        String enterText = mCurrentKeyboard.getEnterKeyText(mEditorInfo.imeOptions, mComposingText);
        String modeChangeText = mCurrentKeyboard.getModeChangeKeyText();
        boolean changed = mCurrentKeyboard.getAlphabeticKeyboard().setSpaceKeyLabel(spaceText);
        changed |= mCurrentKeyboard.getAlphabeticKeyboard().setEnterKeyLabel(enterText);
        CustomKeyboard symbolsKeyboard = getSymbolsKeyboard();
        changed |= symbolsKeyboard.setModeChangeKeyLabel(modeChangeText);
        symbolsKeyboard.setSpaceKeyLabel(spaceText);
        symbolsKeyboard.setEnterKeyLabel(enterText);
        if (changed) {
            mKeyboardView.invalidateAllKeys();
        }
    }

    private void setAutoCompletionVisible(boolean aVisible) {
        mAutoCompletionView.setVisibility(aVisible ? View.VISIBLE : View.GONE);
    }

    // Must be called in the input thread, see postInputCommand.
    enum ComposingAction {
        FINISH,
        DO_NOT_FINISH
    }
    private void displayComposingText(String aText, ComposingAction aAction) {
        if (mInputConnection == null) {
            return;
        }
        boolean succeeded = mInputConnection.setComposingText(aText, 1);
        if (!succeeded) {
            // Fix for InlineAutocompleteEditText failed setComposingText() calls
            String fullText = mInputConnection.getExtractedText(new ExtractedTextRequest(),0).text.toString();
            String beforeText = mInputConnection.getTextBeforeCursor(fullText.length(),0).toString();
            if (beforeText.endsWith(mComposingDisplayText)) {
                mInternalDeleteHint = true;
                mInputConnection.deleteSurroundingText(mComposingDisplayText.length(), 0);
            }
            mInputConnection.setComposingText(aText, 1);
        }
        mComposingDisplayText = aText;
        if (aAction == ComposingAction.FINISH) {
            mInputConnection.finishComposingText();
        }
    }

    // GeckoSession.TextInputDelegate

    @Override
    public void restartInput(@NonNull GeckoSession session, int reason) {
        resetKeyboardLayout();
    }

    @Override
    public void showSoftInput(@NonNull GeckoSession session) {
        if (mFocusedView != mAttachedWindow || getVisibility() != View.VISIBLE) {
            updateFocusedView(mAttachedWindow);
        }
    }

    @Override
    public void hideSoftInput(@NonNull GeckoSession session) {
        if (mFocusedView == mAttachedWindow && getVisibility() == View.VISIBLE) {
            dismiss();
        }
    }

    @Override
    public void updateSelection(@NonNull GeckoSession session, final int selStart, final int selEnd, final int compositionStart, final int compositionEnd) {
        if (mFocusedView != mAttachedWindow || mInputConnection == null) {
            return;
        }

        final InputConnection connection = mInputConnection;
        postInputCommand(new Runnable() {
            @Override
            public void run() {
                if (compositionStart >= 0 && compositionEnd >= 0) {
                    connection.setComposingRegion(compositionStart, compositionEnd);
                }
                connection.setSelection(selStart, selEnd);
            }
        });
    }

    // FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        updateFocusedView(newFocus);
    }


    // VoiceSearch Delegate
    @Override
    public void OnVoiceSearchResult(String aTranscription, float confidance) {
        if (aTranscription != null && !aTranscription.isEmpty()) {
            handleText(aTranscription);
        }
        exitVoiceInputMode();
    }

    @Override
    public void OnVoiceSearchCanceled() {
        exitVoiceInputMode();
    }

    @Override
    public void OnVoiceSearchError() {
        exitVoiceInputMode();
    }

    private void exitVoiceInputMode() {
        if (mIsInVoiceInput && mVoiceSearchWidget != null) {
            mVoiceSearchWidget.hide(REMOVE_WIDGET);
            mWidgetPlacement.visible = true;
            mWidgetManager.updateWidget(this);
            mIsInVoiceInput = false;
        }
    }

    // AutoCompletionDelegate

    @Override
    public void onAutoCompletionItemClick(final KeyboardInterface.Words aItem) {
        if (mFocusedView == null || mInputConnection == null) {
            return;
        }
        if (mCurrentKeyboard.usesComposingText()) {
            String code = StringUtils.removeSpaces(aItem.code);
            mComposingText = mCurrentKeyboard.getComposingText(mComposingText, code).trim();

            postInputCommand(() -> {
                displayComposingText(aItem.value, ComposingAction.FINISH);
                postUICommand(KeyboardWidget.this::updateCandidates);
            });

        } else {
            handleText(aItem.value);
        }
    }

    @Override
    public void onAutoCompletionExtendedChanged() {
        mKeyboardView.setVisibility(mAutoCompletionView.isExtended() ? View.INVISIBLE : View.VISIBLE);
    }

    // TextWatcher
    private String mTextBefore = "";
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mTextBefore = s.toString();
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable aEditable) {
        if (mFocusedView == null || mInputConnection == null) {
            return;
        }

        if (!mInternalDeleteHint && mCurrentKeyboard.usesComposingText() && mComposingText.length() > 0 && mTextBefore.length() > 0 && aEditable.toString().length() == 0) {
            // Text has been cleared externally (e.g. URLBar text clear button)
            mComposingText = "";
            mCurrentKeyboard.clear();
            updateCandidates();
        }
        mInternalDeleteHint = false;
    }
}
