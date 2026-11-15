package com.skypass.core.auth.di

import com.skypass.core.auth.CredentialManagerGoogleIdTokenProvider
import com.skypass.core.auth.GoogleIdTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthModule {
    @Binds
    abstract fun bindGoogleIdTokenProvider(impl: CredentialManagerGoogleIdTokenProvider): GoogleIdTokenProvider
}
