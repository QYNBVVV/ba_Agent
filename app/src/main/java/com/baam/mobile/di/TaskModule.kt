package com.baam.mobile.di

import com.baam.mobile.domain.tasks.CafeTask
import com.baam.mobile.domain.tasks.HelloTask
import com.baam.mobile.engine.task.Task
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 任务注册模块。
 * 每新增一个任务（从 BAAS 移植），在此 @IntoSet 提供一次即可。
 */
@Module
@InstallIn(SingletonComponent::class)
object TaskModule {
    @Provides
    @IntoSet
    fun provideHelloTask(task: HelloTask): Task = task

    @Provides
    @IntoSet
    fun provideCafeTask(task: CafeTask): Task = task
}
