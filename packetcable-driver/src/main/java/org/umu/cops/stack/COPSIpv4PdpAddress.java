/*
 * Copyright (c) 2003 University of Murcia.  All rights reserved.
 * --------------------------------------------------------------
 * For more information, please see <http://www.umu.euro6ix.org/>.
 */

package org.umu.cops.stack;

import org.umu.cops.stack.COPSObjHeader.CType;

import java.net.UnknownHostException;

/**
 * Super for IPv4 PDP Addresses
 */
abstract public class COPSIpv4PdpAddress extends COPSPdpAddress {

    /**
     * Constructor generally used for sending messages
     * @param host - the host name
     * @param tcpPort - the associated port
     * @param reserved - not in use
     * @throws java.lang.IllegalArgumentException
     */
    protected COPSIpv4PdpAddress(final COPSObjHeader objHdr, final String host, final int tcpPort, final short reserved)
            throws UnknownHostException {
        super(objHdr, new COPSIpv4Address(host), tcpPort, reserved);
        if (!objHdr.getCType().equals(CType.DEF))
            throw new IllegalArgumentException("Must have a CType value of " + CType.DEF);
    }

    /**
     * Constructor generally used when parsing the bytes of an inbound COPS message but can also be used when the
     * COPSObjHeader information is known
     * @param objHdr - the object header
     * @param addr - the byte array representation of a host
     * @param tcpPort - the associated port
     * @param reserved - not in use
     * @throws java.lang.IllegalArgumentException
     */
    protected COPSIpv4PdpAddress(final COPSObjHeader objHdr, final byte[] addr, final int tcpPort,
                                 final short reserved) {
        super(objHdr, new COPSIpv4Address(addr), tcpPort, reserved);
        if (!objHdr.getCType().equals(CType.DEF))
            throw new IllegalArgumentException("Must have a CType value of " + CType.DEF);
    }


}

