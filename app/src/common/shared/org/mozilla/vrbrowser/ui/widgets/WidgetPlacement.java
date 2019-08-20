/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;

public class WidgetPlacement {
    static final float WORLD_DPI_RATIO = 2.0f/720.0f;

    private WidgetPlacement() {}
    public WidgetPlacement(Context aContext) {
        density = aContext.getResources().getDisplayMetrics().density;
        // Default value
        cylinderMapRadius = Math.abs(WidgetPlacement.floatDimension(aContext, R.dimen.window_world_z));
    }

    public float density;
    public int width;
    public int height;
    public float worldWidth = -1.0f;
    public float anchorX = 0.5f;
    public float anchorY = 0.5f;
    public float translationX;
    public float translationY;
    public float translationZ;
    public float rotationAxisX;
    public float rotationAxisY;
    public float rotationAxisZ;
    public float rotation;
    public int parentHandle = -1;
    public float parentAnchorX = 0.5f;
    public float parentAnchorY = 0.5f;
    public boolean visible = true;
    public boolean opaque = false;
    public boolean showPointer = true;
    public boolean firstDraw = false;
    public boolean layer = true;
    public float textureScale = 0.7f;
    // Widget will be curved if enabled.
    public boolean cylinder = true;
    public int borderColor = 0;
    /*
     * Flat surface placements are automatically mapped to curved coordinates.
     * If a radius is set it's used for the automatic mapping of the yaw & angle when the
     * cylinder is not centered on the X axis.
     * See Widget::UpdateCylinderMatrix for more info.
     */
    public float cylinderMapRadius;

    public WidgetPlacement clone() {
        WidgetPlacement w = new WidgetPlacement();
        w.copyFrom(this);
        return w;
    }

    public void copyFrom(WidgetPlacement w) {
        this.density = w.density;
        this.width = w.width;
        this.height = w.height;
        this.worldWidth = w.worldWidth;
        this.anchorX = w.anchorX;
        this.anchorY = w.anchorY;
        this.translationX = w.translationX;
        this.translationY = w.translationY;
        this.translationZ = w.translationZ;
        this.rotationAxisX = w.rotationAxisX;
        this.rotationAxisY = w.rotationAxisY;
        this.rotationAxisZ = w.rotationAxisZ;
        this.rotation = w.rotation;
        this.parentHandle = w.parentHandle;
        this.parentAnchorX = w.parentAnchorX;
        this.parentAnchorY = w.parentAnchorY;
        this.visible = w.visible;
        this.opaque = w.opaque;
        this.showPointer = w.showPointer;
        this.firstDraw = w.firstDraw;
        this.layer = w.layer;
        this.textureScale = w.textureScale;
        this.cylinder = w.cylinder;
        this.cylinderMapRadius = w.cylinderMapRadius;
    }

    public int textureWidth() {
        return (int) Math.ceil(width * density * textureScale);
    }

    public int textureHeight() {
        return (int) Math.ceil(height * density * textureScale);
    }

    public int viewWidth() {
        return (int) Math.ceil(width * density);
    }

    public int viewHeight() {
        return (int) Math.ceil(height * density);
    }

    public static int pixelDimension(Context aContext, int aDimensionID) {
        return aContext.getResources().getDimensionPixelSize(aDimensionID);
    }

    public static int dpDimension(Context aContext, int aDimensionID) {
        return (int) (aContext.getResources().getDimension(aDimensionID) / aContext.getResources().getDisplayMetrics().density);
    }

    public static float floatDimension(Context aContext, int aDimensionID) {
        TypedValue outValue = new TypedValue();
        aContext.getResources().getValue(aDimensionID, outValue, true);
        return outValue.getFloat();
    }

    public static float unitFromMeters(float aMeters) {
        return aMeters / WORLD_DPI_RATIO;
    }

    public static float dpToMeters(int aDensityPixels) {
        return aDensityPixels * WORLD_DPI_RATIO;
    }

    public static float unitFromMeters(Context aContext, int aDimensionId) {
        return unitFromMeters(floatDimension(aContext, aDimensionId));
    }

    public static int convertDpToPixel(Context aContext, float dp){
        Resources resources = aContext.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return (int) Math.ceil(dp * ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static float convertPixelsToDp(Context aContext, float px){
        return px / ((float) aContext.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static float worldToWidgetRatio(@NonNull UIWidget widget) {
        float widgetWorldWidth = widget.mWidgetPlacement.worldWidth;
        return ((widgetWorldWidth/widget.mWidgetPlacement.width)/WORLD_DPI_RATIO);
    }

    public static float worldToWindowRatio(Context aContext){
        return (WidgetPlacement.floatDimension(aContext, R.dimen.window_world_width) / SettingsStore.WINDOW_WIDTH_DEFAULT)/ WORLD_DPI_RATIO;
    }

}
