/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.systemui.qs.external;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.quicksettings.IQSTileService;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableLooper.RunWithLooper;

import com.android.internal.logging.UiEventLogger;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSFactoryImpl;
import com.android.systemui.settings.UserFileManager;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoTileManager;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.settings.SecureSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Optional;

import javax.inject.Provider;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public class TileServicesTest extends SysuiTestCase {
    private static int NUM_FAKES = TileServices.DEFAULT_MAX_BOUND * 2;

    private static final ComponentName TEST_COMPONENT =
            ComponentName.unflattenFromString("pkg/.cls");

    private TileServices mTileService;
    private TestableLooper mTestableLooper;
    private ArrayList<TileServiceManager> mManagers;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private CommandQueue mCommandQueue;
    @Mock
    private StatusBarIconController mStatusBarIconController;
    @Mock
    private QSFactoryImpl mQSFactory;
    @Mock
    private PluginManager mPluginManager;
    @Mock
    private  TunerService mTunerService;
    @Mock
    private AutoTileManager mAutoTileManager;
    @Mock
    private DumpManager mDumpManager;
    @Mock
    private CentralSurfaces mCentralSurfaces;
    @Mock
    private QSLogger mQSLogger;
    @Mock
    private UiEventLogger mUiEventLogger;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private SecureSettings  mSecureSettings;
    @Mock
    private TileServiceRequestController.Builder mTileServiceRequestControllerBuilder;
    @Mock
    private TileServiceRequestController mTileServiceRequestController;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private TileLifecycleManager.Factory mTileLifecycleManagerFactory;
    @Mock
    private TileLifecycleManager mTileLifecycleManager;
    @Mock
    private UserFileManager mUserFileManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDependency.injectMockDependency(BluetoothController.class);
        mManagers = new ArrayList<>();
        mTestableLooper = TestableLooper.get(this);

        when(mTileServiceRequestControllerBuilder.create(any()))
                .thenReturn(mTileServiceRequestController);
        when(mTileLifecycleManagerFactory.create(any(Intent.class), any(UserHandle.class)))
                .thenReturn(mTileLifecycleManager);

        Provider<Handler> provider = () -> new Handler(mTestableLooper.getLooper());

        QSTileHost host = new QSTileHost(mContext,
                mStatusBarIconController,
                mQSFactory,
                provider.get(),
                mTestableLooper.getLooper(),
                mPluginManager,
                mTunerService,
                () -> mAutoTileManager,
                mDumpManager,
                mock(BroadcastDispatcher.class),
                Optional.of(mCentralSurfaces),
                mQSLogger,
                mUiEventLogger,
                mUserTracker,
                mSecureSettings,
                mock(CustomTileStatePersister.class),
                mTileServiceRequestControllerBuilder,
                mTileLifecycleManagerFactory,
                mUserFileManager);
        mTileService = new TestTileServices(host, provider, mBroadcastDispatcher,
                mUserTracker, mKeyguardStateController, mCommandQueue);
    }

    @After
    public void tearDown() throws Exception {
        mTileService.getHost().destroy();
        mTileService.destroy();
        TestableLooper.get(this).processAllMessages();
    }

    @Test
    public void testRecalculateBindAllowance() {
        // Add some fake tiles.
        for (int i = 0; i < NUM_FAKES; i++) {
            mTileService.getTileWrapper(mock(CustomTile.class));
        }
        assertEquals(NUM_FAKES, mManagers.size());

        for (int i = 0; i < NUM_FAKES; i++) {
            when(mManagers.get(i).getBindPriority()).thenReturn(i);
        }
        mTileService.recalculateBindAllowance();
        for (int i = 0; i < NUM_FAKES; i++) {
            verify(mManagers.get(i), times(1)).calculateBindPriority(anyLong());
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
            verify(mManagers.get(i), times(1)).setBindAllowed(captor.capture());

            assertEquals("" + i + "th service", i >= (NUM_FAKES - TileServices.DEFAULT_MAX_BOUND),
                    (boolean) captor.getValue());
        }
    }

    @Test
    public void testSetMemoryPressure() {
        testRecalculateBindAllowance();
        mTileService.setMemoryPressure(true);

        for (int i = 0; i < NUM_FAKES; i++) {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
            verify(mManagers.get(i), times(2)).setBindAllowed(captor.capture());

            assertEquals("" + i + "th service", i >= (NUM_FAKES - TileServices.REDUCED_MAX_BOUND),
                    (boolean) captor.getValue());
        }
    }

    @Test
    public void testCalcFew() {
        for (int i = 0; i < TileServices.DEFAULT_MAX_BOUND - 1; i++) {
            mTileService.getTileWrapper(mock(CustomTile.class));
        }
        mTileService.recalculateBindAllowance();

        for (int i = 0; i < TileServices.DEFAULT_MAX_BOUND - 1; i++) {
            // Shouldn't get bind prioirities calculated when there are less than the max services.
            verify(mManagers.get(i), never()).calculateBindPriority(
                    anyLong());

            // All should be bound since there are less than the max services.
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
            verify(mManagers.get(i), times(1)).setBindAllowed(captor.capture());

            assertTrue(captor.getValue());
        }
    }

    @Test
    public void testRegisterCommand() {
        verify(mCommandQueue).addCallback(any());
    }

    @Test
    public void testRequestListeningStatusCommand() throws RemoteException {
        ArgumentCaptor<CommandQueue.Callbacks> captor =
                ArgumentCaptor.forClass(CommandQueue.Callbacks.class);
        verify(mCommandQueue).addCallback(captor.capture());

        CustomTile mockTile = mock(CustomTile.class);
        when(mockTile.getComponent()).thenReturn(TEST_COMPONENT);

        TileServiceManager manager = mTileService.getTileWrapper(mockTile);
        when(manager.isActiveTile()).thenReturn(true);
        when(manager.getTileService()).thenReturn(mock(IQSTileService.class));

        captor.getValue().requestTileServiceListeningState(TEST_COMPONENT);
        mTestableLooper.processAllMessages();
        verify(manager).setBindRequested(true);
        verify(manager.getTileService()).onStartListening();
    }

    private class TestTileServices extends TileServices {
        TestTileServices(QSTileHost host, Provider<Handler> handlerProvider,
                BroadcastDispatcher broadcastDispatcher, UserTracker userTracker,
                KeyguardStateController keyguardStateController, CommandQueue commandQueue) {
            super(host, handlerProvider, broadcastDispatcher, userTracker, keyguardStateController,
                    commandQueue);
        }

        @Override
        protected TileServiceManager onCreateTileService(
                ComponentName component, BroadcastDispatcher broadcastDispatcher) {
            TileServiceManager manager = mock(TileServiceManager.class);
            mManagers.add(manager);
            when(manager.isLifecycleStarted()).thenReturn(true);
            return manager;
        }
    }
}
