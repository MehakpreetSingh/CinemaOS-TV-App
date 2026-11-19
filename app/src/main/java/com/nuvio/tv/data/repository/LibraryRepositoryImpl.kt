package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.R
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.LibrarySyncService
import com.nuvio.tv.core.tracking.TrackingListManager
import com.nuvio.tv.core.tracking.TrackingLibraryProviderRegistry
import com.nuvio.tv.core.tracking.TrackingMembershipApplyResult
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.core.tracking.LOCAL_LIBRARY_LIST_KEY
import com.nuvio.tv.core.tracking.TrackingMembershipMutationException
import com.nuvio.tv.core.tracking.dispatchTrackingMembershipChanges
import com.nuvio.tv.core.tracking.TrackingRefreshIntent
import com.nuvio.tv.core.tracking.effectiveLibrarySourceMode
import com.nuvio.tv.core.tracking.providerId
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.mediaserver.ServerItemRef
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.ListMembershipSnapshot
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.LibraryListPrivacy
import com.nuvio.tv.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val libraryPreferences: LibraryPreferences,
    private val traktSettingsDataStore: TraktSettingsDataStore,
    private val librarySyncService: LibrarySyncService,
    private val authManager: AuthManager,
    private val metaRepository: MetaRepository,
    private val trackingProviders: TrackingLibraryProviderRegistry,
    private val profileManager: ProfileManager,
    private val addonEventDispatcher: AddonEventDispatcher,
