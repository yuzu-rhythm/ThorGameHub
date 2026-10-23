package dev.thorhub.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.thorhub.app.platform.contracts.FilenameMetadataProvider
import dev.thorhub.app.platform.contracts.GeneratedArtworkProvider
import dev.thorhub.app.platform.contracts.LocalOnlySyncTransport
import dev.thorhub.app.platform.contracts.StandardLidSignalProvider
import dev.thorhub.app.platform.contracts.StandardPrivilegeBridge
import dev.thorhub.core.model.ArtworkProvider
import dev.thorhub.core.model.LidSignalProvider
import dev.thorhub.core.model.MetadataProvider
import dev.thorhub.core.model.PrivilegeBridge
import dev.thorhub.core.model.SyncTransport
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContractBindingsModule {
    @Binds @Singleton abstract fun bindLidSignalProvider(implementation: StandardLidSignalProvider): LidSignalProvider
    @Binds @Singleton abstract fun bindPrivilegeBridge(implementation: StandardPrivilegeBridge): PrivilegeBridge
    @Binds @Singleton abstract fun bindSyncTransport(implementation: LocalOnlySyncTransport): SyncTransport
    @Binds @Singleton abstract fun bindMetadataProvider(implementation: FilenameMetadataProvider): MetadataProvider
    @Binds @Singleton abstract fun bindArtworkProvider(implementation: GeneratedArtworkProvider): ArtworkProvider
}
