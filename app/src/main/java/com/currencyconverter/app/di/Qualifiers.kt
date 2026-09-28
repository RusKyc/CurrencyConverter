package com.currencyconverter.app.di

import javax.inject.Qualifier

/** Long-lived scope tied to the process, for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
