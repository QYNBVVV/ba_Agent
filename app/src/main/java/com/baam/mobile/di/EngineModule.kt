package com.baam.mobile.di

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.baam.mobile.engine.cv.TemplateMatcher
import com.baam.mobile.engine.driver.DeviceDriver
import com.baam.mobile.engine.driver.impl.AccessibilityDeviceDriver
import com.baam.mobile.engine.screen.CoordinateMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    /**
     * 屏幕物理分辨率（实际像素）。用于建立参考坐标系映射。
     * 注意：取真实物理像素而非应用窗口像素，避免状态栏/导航栏偏移。
     */
    @Provides
    @Singleton
    fun provideDisplayMetrics(@ApplicationContext context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    @Provides
    @Singleton
    fun provideCoordinateMapper(metrics: DisplayMetrics): CoordinateMapper =
        CoordinateMapper(
            actualWidth = metrics.widthPixels,
            actualHeight = metrics.heightPixels,
        )

    @Provides
    @Singleton
    fun provideDeviceDriver(mapper: CoordinateMapper): DeviceDriver =
        AccessibilityDeviceDriver(mapper)

    @Provides
    @Singleton
    fun provideTemplateMatcher(@ApplicationContext context: Context): TemplateMatcher =
        TemplateMatcher(context)
}
