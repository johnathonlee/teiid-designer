/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */

package com.metamatrix.common.util;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class NetUtils {
    /**
     * Resolves the given host name into InetAddress; if host name can not be resolved then it will throw
     * {@link UnknownHostException}
     * 
     * @param hostName
     * @return
     * @throws UnknownHostException
     */
    public static InetAddress resolveHostByName( final String hostName ) throws UnknownHostException {
        if (hostName.equalsIgnoreCase("localhost")) { //$NON-NLS-1$
            try {
                return getInstance().getInetAddress();
            } catch (final UnknownHostException e) {
            }
        }
        return InetAddress.getByName(hostName);
    }

    private InetAddress inetAddress;

    private static NetUtils INSTANCE = new NetUtils();

    public static NetUtils getInstance() {
        return INSTANCE;
    }

    /**
     * Finds a InetAddress of the current host where the JVM is running, by querying NetworkInterfaces installed and filters them by
     * given preferences. It will return the first Address which UP and meets the criteria
     * 
     * @param preferIPv6
     * @param perferLoopback
     * @return null is returned if requested criteria is not met.
     * @throws UnknownHostException http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037 (Linux issue with resolving to
     *         loopback in the DHCP situations)
     */
    private InetAddress findAddress( final boolean preferIPv6,
                                     final boolean perferLoopback ) {
        try {
            final Enumeration<NetworkInterface> ne = NetworkInterface.getNetworkInterfaces();
            while (ne.hasMoreElements()) {
                final NetworkInterface ni = ne.nextElement();
                // ## JDBC4.0-begin ##
                if (ni.isUp()) {
                    // ## JDBC4.0-end ##
                    final Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        final InetAddress addr = addrs.nextElement();

                        final boolean isIPv6 = (addr instanceof Inet6Address);
                        if (preferIPv6 == isIPv6 && perferLoopback == addr.isLoopbackAddress()) {
                            return addr;
                        }
                    }
                    // ## JDBC4.0-begin ##
                }
                // ## JDBC4.0-end ##
            }
        } catch (final SocketException e) {
            // treat this as address not found and return null;
        }
        return null;
    }

    public InetAddress getInetAddress() throws UnknownHostException {
        resolveHostName();
        return this.inetAddress;
    }

    /**
     * Call to determine if a port is available to be opened. This is used to determine if a port is already opened by some other
     * process. If the port is available, then it's not in use.
     * 
     * @param host
     * @param port
     * @return true if the port is not opened.
     * @since 4.3
     */
    public boolean isPortAvailable( final String host,
                                    final int port ) throws UnknownHostException {

        try {
            // using Socket to try to connect to an existing opened socket
            final Socket ss = new Socket(host, port);

            try {
                ss.close();
            } catch (final Exception ce) {
                // it was open and considered available, then dont worry about the close error
            }
            return false;
        } catch (final UnknownHostException ce) {
            throw ce;
        } catch (final IOException e) {
            // ignore
        }
        return true;
    }

    /*
     * Dynamically resolving the host name should only be done when setupmm is being run
     * or when the vm initially starts up and the configuration Host has to be found based on that resolution.  
     * After that, the {@link VMNaming} class should be used to obtain the logical and physical host addresses. 
     */
    private synchronized void resolveHostName() throws UnknownHostException {
        UnknownHostException une = null;

        final boolean preferIPv6 = Boolean.getBoolean("java.net.preferIPv6Addresses");//$NON-NLS-1$

        // majority of the times we will find the address with this below call
        if (this.inetAddress == null) {
            try {
                final InetAddress addr = InetAddress.getLocalHost();
                if (!addr.isLoopbackAddress()) {
                    this.inetAddress = addr;
                }
            } catch (final UnknownHostException e) {
                une = e;
            }
        }

        // see if you can find a non-loopback address, based on the preference
        if (this.inetAddress == null) {
            this.inetAddress = findAddress(preferIPv6, false);
        }

        // if no-addresses found so far then resort to IPv4 loopback address
        if (this.inetAddress == null) {
            this.inetAddress = findAddress(false, true);
        }

        if (this.inetAddress == null) {
            if (une != null) throw une;
            throw new UnknownHostException("failed to resolve the address for localhost"); //$NON-NLS-1$
        }
    }
}
