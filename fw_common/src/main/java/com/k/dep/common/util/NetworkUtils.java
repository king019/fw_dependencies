package com.k.dep.common.util;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtils {


    public static List<String> getHostIP() {
        List<String> ips = new ArrayList<>();
        Enumeration<NetworkInterface> allNetInterfaces = null;
        String resultIP = null;
        try {
            allNetInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        InetAddress ip = null;
        while (allNetInterfaces.hasMoreElements()) {
            NetworkInterface netInterface = allNetInterfaces.nextElement();
            Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                ip = addresses.nextElement();
                if (ip != null && ip instanceof Inet4Address) {
                    if (resultIP == null) {
                        resultIP = ip.getHostAddress();
                    }
                    ips.add(ip.getHostAddress());
                }
            }
        }
        return ips;

    }
}
