package com.androiddev.social.timeline.ui

import androidx.paging.*
import com.androiddev.social.AuthRequiredScope
import com.androiddev.social.SingleIn
import com.androiddev.social.auth.data.OauthRepository
import com.androiddev.social.shared.UserApi
import com.androiddev.social.timeline.data.*
import com.androiddev.social.ui.util.Presenter
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ExperimentalPagingApi
@ContributesBinding(AuthRequiredScope::class, boundType = TimelinePresenter::class)
@SingleIn(AuthRequiredScope::class)
class RealTimelinePresenter @Inject constructor(
    val timelineRemoteMediators: @JvmSuppressWildcards Set<TimelineRemoteMediator>,
    val statusDao: StatusDao,
    val api: UserApi,
    val oauthRepository: OauthRepository
) : TimelinePresenter() {

    private val pagingConfig = PagingConfig(
        pageSize = 20,
        initialLoadSize = 30,
        prefetchDistance = 10
    )

    override suspend fun eventHandler(event: HomeEvent, scope: CoroutineScope) {
        when (event) {
            is Load -> {
                when (event.feedType) {
                    FeedType.Home -> {
                        val remoteMediator =
                            timelineRemoteMediators
                                .filterIsInstance<HomeTimelineRemoteMediator>()
                                .single()
                        val flow = Pager(
                            config = pagingConfig,
                            remoteMediator = remoteMediator
                        )
                        { statusDao.getTimeline(FeedType.Home.type) }
                            .flow
                        model = model.copy(homeStatuses = flow.cachedIn(scope))
                    }

                    FeedType.Local -> {
                        val remoteMediator =
                            timelineRemoteMediators.filterIsInstance<LocalTimelineRemoteMediator>()
                                .single()
                        val flow = Pager(
                            config = pagingConfig,
                            remoteMediator = remoteMediator
                        ) {
                            statusDao.getTimeline(FeedType.Local.type)
                        }.flow

                        model = model.copy(
                            localStatuses = flow.cachedIn(scope)
                        )
                    }

                    FeedType.Federated -> {
                        val remoteMediator =
                            timelineRemoteMediators.filterIsInstance<FederatedTimelineRemoteMediator>()
                                .single()
                        val flow = Pager(
                            config = pagingConfig,
                            remoteMediator = remoteMediator
                        ) {
                            statusDao.getTimeline(FeedType.Federated.type)
                        }.flow

                        model = model.copy(
                            federatedStatuses = flow.cachedIn(scope)
                        )
                    }
                    FeedType.Trending -> {
                        val remoteMediator =
                            timelineRemoteMediators.filterIsInstance<TrendingRemoteMediator>()
                                .single()
                        val flow = Pager(
                            config = pagingConfig,
                            remoteMediator = remoteMediator
                        ) {
                            statusDao.getTimeline(FeedType.Trending.type)
                        }.flow

                        model = model.copy(
                            trendingStatuses = flow.cachedIn(scope)
                        )
                    }
                }
            }

            is PostMessage -> {
                val result = kotlin.runCatching {
                    api.newStatus(
                        authHeader = " Bearer ${oauthRepository.getCurrent()}",
                        content = event.content
                    )
                }
                when {
                    result.isSuccess -> {
                        withContext(Dispatchers.IO) {
                            statusDao.insertAll(
                                listOf(
                                    result.getOrThrow().toStatusDb(FeedType.Home)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}


abstract class TimelinePresenter :
    Presenter<TimelinePresenter.HomeEvent, TimelinePresenter.HomeModel, TimelinePresenter.HomeEffect>(
        HomeModel(true)
    ) {
    sealed interface HomeEvent
    data class Load(val feedType: FeedType) : HomeEvent
    data class PostMessage(val content: String) : HomeEvent

    data class HomeModel(
        val loading: Boolean,
        val homeStatuses: Flow<PagingData<StatusDB>>? = null,
        val federatedStatuses: Flow<PagingData<StatusDB>>? = null,
        val trendingStatuses: Flow<PagingData<StatusDB>>? = null,
        val localStatuses: Flow<PagingData<StatusDB>>? = null
    )

    sealed interface HomeEffect
}