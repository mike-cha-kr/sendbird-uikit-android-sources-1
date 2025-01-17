package com.sendbird.uikit.vm;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.OnLifecycleEvent;

import com.sendbird.android.BaseChannel;
import com.sendbird.android.BaseMessage;
import com.sendbird.android.SendBird;
import com.sendbird.android.User;
import com.sendbird.uikit.interfaces.CustomMemberListQueryHandler;
import com.sendbird.uikit.log.Logger;
import com.sendbird.uikit.widgets.PagerRecyclerView;
import com.sendbird.uikit.widgets.StatusFrameView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class UserTypeListViewModel extends BaseViewModel implements LifecycleObserver, PagerRecyclerView.Pageable<List<User>> {

    private final String CHANNEL_HANDLER_MEMBER_LIST = "CHANNEL_HANDLER_MEMBER_LIST" + System.currentTimeMillis();
    private final MutableLiveData<StatusFrameView.Status> statusFrame = new MutableLiveData<>();
    private final MutableLiveData<List<User>> memberList = new MutableLiveData<>();
    private final CustomMemberListQueryHandler<User> queryHandler;
    protected BaseChannel channel;

    private void onResult(List<User> memberList, Exception e) {
        if (e != null) {
            Logger.e(e);
            changeAlertStatus(StatusFrameView.Status.ERROR);
            notifyDataSetChanged(this.memberList.getValue());
        } else {
            List<User> newUsers = new ArrayList<>(memberList);
            List<User> origin = this.memberList.getValue();
            if (origin != null) {
                newUsers.addAll(0, origin);
            }
            applyUserList(newUsers);
        }
    }

    UserTypeListViewModel(BaseChannel channel, CustomMemberListQueryHandler<User> customQuery) {
        super();
        this.channel = channel;
        this.queryHandler = customQuery;
        //queryHandler = createQueryHandler(channel, type);
    }

    private boolean isCurrentChannel(@NonNull String channelUrl) {
        return channelUrl.equals(channel.getUrl());
    }

    private void updateChannel(@NonNull BaseChannel channel) {
        if (isCurrentChannel(channel.getUrl())) {
            Logger.i(">> MemberListViewModel::updateChannel()");
            UserTypeListViewModel.this.channel = channel;
            loadInitial();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private void onResume() {
        Logger.i(">> MemberListViewModel::onResume()");
        SendBird.addChannelHandler(CHANNEL_HANDLER_MEMBER_LIST, new SendBird.ChannelHandler() {
            @Override
            public void onMessageReceived(BaseChannel channel, BaseMessage message) {
            }

            @Override
            public void onOperatorUpdated(BaseChannel channel) {
                updateChannel(channel);
            }

            @Override
            public void onUserMuted(BaseChannel channel, User user) {
                updateChannel(channel);
            }

            @Override
            public void onUserUnmuted(BaseChannel channel, User user) {
                updateChannel(channel);
            }

            @Override
            public void onUserBanned(BaseChannel channel, User user) {
                updateChannel(channel);
            }

            @Override
            public void onUserUnbanned(BaseChannel channel, User user) {
                updateChannel(channel);
            }

            @Override
            public void onChannelChanged(BaseChannel channel) {
                updateChannel(channel);
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private void onDestroy() {
        Logger.i(">> MemberListViewModel::onDestroy()");
        SendBird.removeChannelHandler(CHANNEL_HANDLER_MEMBER_LIST);
    }

    public LiveData<StatusFrameView.Status> getStatusFrame() {
        return statusFrame;
    }

    protected void changeAlertStatus(StatusFrameView.Status status) {
        if (!hasData() || status == StatusFrameView.Status.NONE) {
            statusFrame.postValue(status);
        }
    }

    protected boolean hasData() {
        List<? extends User> origin = memberList.getValue();
        return origin != null && origin.size() > 0;
    }

    public LiveData<? extends Collection<?>> getMemberList() {
        return memberList;
    }

    private void applyUserList(List<User> newUserList) {
        changeAlertStatus(newUserList.size() == 0 ? StatusFrameView.Status.EMPTY : StatusFrameView.Status.NONE);
        notifyDataSetChanged(newUserList);
    }

    @SuppressWarnings("unchecked")
    protected void notifyDataSetChanged(Collection<User> list) {
        memberList.postValue(list == null ? new ArrayList<>() : (List<User>)list);
    }

    public void loadInitial() {
        Logger.d(">> MemberListViewModel::loadInitial()");
        List<? extends User> origin = this.memberList.getValue();
        if (origin != null) {
            origin.clear();
        }
        queryHandler.loadInitial(UserTypeListViewModel.this::onResult);
    }

    @Override
    public List<User> loadPrevious() {
        return Collections.emptyList();
    }

    @Override
    public List<User> loadNext() throws InterruptedException {
        if (queryHandler.hasMore()) {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<List<User>> result = new AtomicReference<>();
            final AtomicReference<Exception> error = new AtomicReference<>();
            try {
                queryHandler.load((userList, e) -> {
                    try {
                        if (e != null) {
                            error.set(e);
                            return;
                        }
                        result.set(userList);
                    } finally {
                        latch.countDown();
                    }
                });
                latch.await();
            } catch (Exception e) {
                error.set(e);
                throw e;
            } finally {
                onResult(result.get(), error.get());
            }
            return result.get();
        }
        return Collections.emptyList();
    }
}
