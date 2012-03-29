/*
 * Copyright (C) 2010 The Android-X86 Open Source Project
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
 * limitations under the License.
 *
 * Author: Yi Sun <beyounn@gmail.com>
 */

package android.net.ethernet;

import java.net.InetAddress;
import java.net.UnknownHostException;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothHeadset;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.ConnectivityManager;
import android.net.DhcpInfoInternal;
import android.net.NetworkStateTracker;
import android.net.NetworkUtils;
import android.net.LinkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.util.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Track the state of Ethernet connectivity. All event handling is done here,
 * and all changes in connectivity state are initiated here.
 *
 * @hide
 */

public class EthernetStateTracker extends Handler implements NetworkStateTracker {
    private static final String TAG                                 = "EthernetStateTracker";
    public static final int EVENT_DHCP_START                        = 0;
    public static final int EVENT_INTERFACE_CONFIGURATION_SUCCEEDED = 1;
    public static final int EVENT_INTERFACE_CONFIGURATION_FAILED    = 2;
    public static final int EVENT_HW_CONNECTED                      = 3;
    public static final int EVENT_HW_DISCONNECTED                   = 4;
    public static final int EVENT_HW_PHYCONNECTED                   = 5;
    private static final int NOTIFY_ID                              = 6;
    private static final boolean localLOGV = true;

    private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

    private EthernetManager mEM;
    private boolean mServiceStarted;
    private NetworkInfo mNetworkInfo;

    private boolean mStackConnected;
    private boolean mHWConnected;
    private boolean mInterfaceStopped;
    private DhcpHandler mDhcpTarget;
    private String mInterfaceName ;
    private DhcpInfoInternal mDhcpInfo;
    private EthernetMonitor mMonitor;
    private String[] sDnsPropNames;
    private boolean mStartingDhcp;
    private NotificationManager mNotificationManager;
    private Notification mNotification;
    private Handler mTrackerTarget;

    private LinkProperties mLinkProperties;

    private BroadcastReceiver mEthernetStateReceiver;

    /* For sending events to connectivity service handler */
    private Handler mCsHandler;
	private Context mContext;

    public EthernetStateTracker(Context context, Handler target) {
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_ETHERNET, 0, "ETH", "");
        mLinkProperties = new LinkProperties();
        if (localLOGV) Slog.v(TAG, "Starts...");

        if (EthernetNative.initEthernetNative() != 0) {
            Slog.e(TAG,"Can not init ethernet device layers");
            return;
        }

        if (localLOGV) Slog.v(TAG,"Successed");
        mServiceStarted = true;
        HandlerThread dhcpThread = new HandlerThread("DHCP Handler Thread");
        dhcpThread.start();
        mDhcpTarget = new DhcpHandler(dhcpThread.getLooper(), this);
        mMonitor = new EthernetMonitor(this);
        mDhcpInfo = new DhcpInfoInternal();
    }

    /**
     * Stop etherent interface
     * @param suspend {@code false} disable the interface {@code true} only reset the connection without disable the interface
     * @return true
     */
    public boolean stopInterface(boolean suspend) {
        if (mEM != null) {
            EthernetDevInfo info = mEM.getSavedConfig();
            if (info != null && mEM.isConfigured()) {
                synchronized (mDhcpTarget) {
                    mInterfaceStopped = true;
                    if (localLOGV) Slog.i(TAG, "stop dhcp and interface");
                    mDhcpTarget.removeMessages(EVENT_DHCP_START);
                    String ifname = info.getIfName();

                    if (!NetworkUtils.stopDhcp(ifname)) {
                        if (localLOGV) Slog.w(TAG, "Could not stop DHCP");
                    }
                    NetworkUtils.resetConnections(ifname, NetworkUtils.RESET_ALL_ADDRESSES);
                    if (!suspend)
                        NetworkUtils.disableInterface(ifname);

                    mLinkProperties.clear();
                }
            }
        }
        return true;
    }

    private boolean configureInterface(EthernetDevInfo info) throws UnknownHostException {
        mStackConnected = false;
        mHWConnected = false;
        mInterfaceStopped = false;
        mStartingDhcp = true;
        if (info.getConnectMode().equals(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP)) {
            if (localLOGV) Slog.i(TAG, "trigger dhcp for device " + info.getIfName());
            sDnsPropNames = new String[] {
                "dhcp." + mInterfaceName + ".dns1",
                "dhcp." + mInterfaceName + ".dns2"
             };

            mDhcpTarget.sendEmptyMessage(EVENT_DHCP_START);
        } else {
/* HFM
            int event;
            sDnsPropNames = new String[] {
                "net." + mInterfaceName + ".dns1",
                "net." + mInterfaceName + ".dns2"
             };
            mDhcpInfo.ipAddress = lookupHost(info.getIpAddress());
            mDhcpInfo.gateway = lookupHost(info.getRouteAddr());
            mDhcpInfo.netmask = lookupHost(info.getNetMask());
            mDhcpInfo.dns1 = lookupHost(info.getDnsAddr());
            mDhcpInfo.dns2 = 0;

            if (localLOGV) Slog.i(TAG, "set ip manually " + mDhcpInfo.toString());
            NetworkUtils.removeDefaultRoute(info.getIfName());
            if (NetworkUtils.configureInterface(info.getIfName(), mDhcpInfo)) {
                event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                SystemProperties.set("net.dns1", info.getDnsAddr());
		SystemProperties.set("net." + info.getIfName() + ".dns1", info.getDnsAddr());
		SystemProperties.set("net." + info.getIfName() + ".dns2", "0.0.0.0");
                if (localLOGV) Slog.v(TAG, "Static IP configuration succeeded");
            } else {
                event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                if (localLOGV) Slog.w(TAG, "Static IP configuration failed");
            }
            this.sendEmptyMessage(event);
*/
        }
        return true;
    }

    /**
     * reset ethernet interface
     * @return true
     * @throws UnknownHostException
     */
    public boolean resetInterface()  throws UnknownHostException{
        /*
         * This will guide us to enabled the enabled device
         */
        if (mEM != null) {
            EthernetDevInfo info = mEM.getSavedConfig();
            if (info != null && mEM.isConfigured()) {
                synchronized (this) {
                    mInterfaceName = info.getIfName();
                    if (localLOGV) Slog.i(TAG, "reset device " + mInterfaceName);
                    NetworkUtils.resetConnections(mInterfaceName, NetworkUtils.RESET_ALL_ADDRESSES);
                     // Stop DHCP
                    if (mDhcpTarget != null) {
                        mDhcpTarget.removeMessages(EVENT_DHCP_START);
                    }
                    if (!NetworkUtils.stopDhcp(mInterfaceName)) {
                        if (localLOGV) Slog.w(TAG, "Could not stop DHCP");
                    }
                    mLinkProperties.clear();
                    configureInterface(info);
                }
            }
        }
        return true;
    }

/* HFM
    @Override
    public String[] getNameServers() {
        return getNameServerList(sDnsPropNames);
    }
*/
    @Override
    public String getTcpBufferSizesPropName() {
        return "net.tcp.buffersize.default";
    }

    public void StartPolling() {
        mMonitor.startMonitoring();
    }
    @Override
    public boolean isAvailable() {
        // Only say available if we have interfaces and user did not disable us.
        return ((mEM.getTotalInterface() != 0) && (mEM.getState() != EthernetManager.ETHERNET_STATE_DISABLED));
    }

    @Override
    public boolean reconnect() {
        try {
            synchronized (this) {
                if (mHWConnected && mStackConnected)
                    return true;
            }
            if (mEM.getState() != EthernetManager.ETHERNET_STATE_DISABLED) {
                // maybe this is the first time we run, so set it to enabled
                mEM.setEnabled(true);
                if (!mEM.isConfigured()) {
                    mEM.setDefaultConf();
                }
                return resetInterface();
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return false;

    }

    @Override
    public boolean setRadio(boolean turnOn) {
        return false;
    }

    @Override
    public void startMonitoring(Context context, Handler target) {
        if (localLOGV) Slog.v(TAG,"start to monitor the ethernet devices");
        if (mServiceStarted) {
            mEM = (EthernetManager)context.getSystemService(Context.ETHERNET_SERVICE);
			mContext = context;
	        mCsHandler = target;

///		    IntentFilter filter = new IntentFilter();
///		    filter.addAction(EthernetManager.NETWORK_STATE_CHANGED_ACTION);

///		    mEthernetStateReceiver = new EthernetStateReceiver();
///		    mContext.registerReceiver(mEthernetStateReceiver, filter);
            int state = mEM.getState();
            if (state != mEM.ETHERNET_STATE_DISABLED) {
                if (state == mEM.ETHERNET_STATE_UNKNOWN) {
                    // maybe this is the first time we run, so set it to enabled
                    mEM.setEnabled(mEM.getDeviceNameList() != null);
                } else {
                    try {
                        resetInterface();
                    } catch (UnknownHostException e) {
                        Slog.e(TAG, "Wrong ethernet configuration");
                    }
                }
            }
        }
    }

/* HFM
    @Override
    public int startUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return 0;
    }

    @Override
    public int stopUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return 0;
    }
*/
    @Override
    public boolean teardown() {
        return (mEM != null) ? stopInterface(false) : false;
    }

    private void postNotification(int event) {
        Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, new NetworkInfo(mNetworkInfo));
        msg.sendToTarget();
    }

    private void setState(boolean state, int event) {
        if (mNetworkInfo.isConnected() != state) {
            if (state) {
                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);
            } else {
                mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
                stopInterface(true);
            }
            mNetworkInfo.setIsAvailable(state);
            postNotification(event);
        }
    }

    public void handleMessage(Message msg) {

        synchronized (this) {
            switch (msg.what) {
            case EVENT_INTERFACE_CONFIGURATION_SUCCEEDED:
                if (localLOGV) Slog.i(TAG, "received configured succeeded, stack=" + mStackConnected + " HW=" + mHWConnected);
                mStackConnected = true;
                if (mHWConnected)
                    setState(true, msg.what);
                break;
            case EVENT_INTERFACE_CONFIGURATION_FAILED:
                mStackConnected = false;
                //start to retry ?
                break;
            case EVENT_HW_CONNECTED:
                if (localLOGV) Slog.i(TAG, "received HW connected, stack=" + mStackConnected + " HW=" + mHWConnected);
                mHWConnected = true;
                if (mStackConnected)
                    setState(true, msg.what);
                break;
            case EVENT_HW_DISCONNECTED:
                if (localLOGV) Slog.i(TAG, "received disconnected events, stack=" + mStackConnected + " HW=" + mHWConnected);
                setState(mHWConnected = false, msg.what);
                break;
            case EVENT_HW_PHYCONNECTED:
                if (localLOGV) Slog.i(TAG, "interface up event, kick off connection request");
                if (!mStartingDhcp) {
                    int state = mEM.getState();
                    if (state != mEM.ETHERNET_STATE_DISABLED) {
                        EthernetDevInfo info = mEM.getSavedConfig();
                        if (info != null && mEM.isConfigured()) {
                            try {
                                configureInterface(info);
                            } catch (UnknownHostException e) {
                                 // TODO Auto-generated catch block
                                 //e.printStackTrace();
                                 Slog.e(TAG, "Cannot configure interface");
                            }
                        }
                    }
                }
                break;
            }
        }
    }
    private class DhcpHandler extends Handler {
         public DhcpHandler(Looper looper, Handler target) {
             super(looper);
             mTrackerTarget = target;
         }

         public void handleMessage(Message msg) {
             int event;

             switch (msg.what) {
                 case EVENT_DHCP_START:
                     synchronized (mDhcpTarget) {
                         if (!mInterfaceStopped) {
                             if (localLOGV) Slog.d(TAG, "DhcpHandler: DHCP request started");
                             if (NetworkUtils.runDhcp(mInterfaceName, mDhcpInfo)) {
                                 event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                                 if (localLOGV) Slog.d(TAG, "DhcpHandler: DHCP request succeeded: " + mDhcpInfo.toString());
                                 mLinkProperties = mDhcpInfo.makeLinkProperties();
                                 mLinkProperties.setInterfaceName(mInterfaceName);
                             } else {
                                 event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                                 Slog.e(TAG, "DhcpHandler: DHCP request failed: " + NetworkUtils.getDhcpError());
                             }
                             mTrackerTarget.sendEmptyMessage(event);
                         } else {
                             mInterfaceStopped = false;
                         }
                         mStartingDhcp = false;
                     }
                     break;
             }
         }
    }

    public void notifyPhyConnected(String ifname) {
        if (localLOGV) Slog.v(TAG, "report interface is up for " + ifname);
        synchronized(this) {
            this.sendEmptyMessage(EVENT_HW_PHYCONNECTED);
        }
    }

    public void notifyStateChange(String ifname,DetailedState state) {
        if (localLOGV) Slog.i(TAG, "report new state " + state.toString() + " on dev " + ifname);
        if (ifname.equals(mInterfaceName)) {
            if (localLOGV) Slog.v(TAG, "update network state tracker");
            synchronized(this) {
                this.sendEmptyMessage(state.equals(DetailedState.CONNECTED)
                    ? EVENT_HW_CONNECTED : EVENT_HW_DISCONNECTED);
            }
        }
    }

    private static int lookupHost(String hostname) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            return -1;
        }
        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8)
                |  (addrBytes[0] & 0xff);
        return addr;
    }

    public void setDependencyMet(boolean met) {
        // not supported on this network
    }
    /*HFM stubs */
    public void setUserDataEnable(boolean enabled) {
       Slog.w(TAG, "ignoring setUserDataEnable(" + enabled + ")");
    }

    public void setDataEnable(boolean enabled) {
    }
    public void setPolicyDataEnable(boolean enabled) {
    }
    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested.set(isRequested);
    }

    public boolean isTeardownRequested() {
        return mTeardownRequested.get();
    }
    /**
     * Check if private DNS route is set for the network
     */
    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet.get();
    }

    /**
     * Set a flag indicating private DNS route is set
     */
    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet.set(enabled);
    }

    /**
     * Fetch NetworkInfo for the network
     */
    public NetworkInfo getNetworkInfo() {
        return new NetworkInfo(mNetworkInfo);
    }

    /**
     * Fetch LinkProperties for the network
     */
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    /**
     * A capability is an Integer/String pair, the capabilities
     * are defined in the class LinkSocket#Key.
     *
     * @return a copy of this connections capabilities, may be empty but never null.
     */
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities();
    }

    /**
     * Check if default route is set
     */
    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet.get();
    }

    /**
     * Set a flag indicating default route is set for the network
     */
    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet.set(enabled);
    }
}
