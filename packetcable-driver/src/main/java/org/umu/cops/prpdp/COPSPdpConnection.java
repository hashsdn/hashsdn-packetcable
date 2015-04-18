/*
 * Copyright (c) 2004 University of Murcia.  All rights reserved.
 * --------------------------------------------------------------
 * For more information, please see <http://www.umu.euro6ix.org/>.
 */

package org.umu.cops.prpdp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umu.cops.stack.*;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class for managing an provisioning connection at the PDP side.
 */
public class COPSPdpConnection implements Runnable {

    public final static Logger logger = LoggerFactory.getLogger(COPSPdpConnection.class);

    /**
        Socket connected to PEP
     */
    private Socket _sock;

    /**
        PEP identifier
    */
    private COPSPepId _pepId;

    /**
        Time of the latest keep-alive sent
     */
    private Date _lastKa;

    /**
        Opcode of the latest message sent
    */
    private byte _lastmessage;

    /**
     *  Time of the latest keep-alive received
     */
    protected Date _lastRecKa;

    /**
        Maps a Client Handle to a Handler
     */
    protected final Map<String, COPSPdpReqStateMan> _managerMap;
    // map < String(COPSHandle), COPSPdpHandler> HandlerMap;

    /**
     *  PDP policy data processor class
     */
    protected COPSPdpDataProcess _process;

    /**
        Accounting timer value (secs)
     */
    protected short _acctTimer;

    /**
        Keep-alive timer value (secs)
     */
    protected short _kaTimer;

    /**
        COPS error returned by PEP
     */
    protected COPSError _error;

    /**
     * Creates a new PDP connection
     *
     * @param pepId PEP-ID of the connected PEP
     * @param sock  Socket connected to PEP
     * @param process   Object for processing policy data
     */
    public COPSPdpConnection(COPSPepId pepId, Socket sock, COPSPdpDataProcess process) {
        _sock = sock;
        _pepId = pepId;

        _lastKa = new Date();
        _lastmessage = COPSHeader.COPS_OP_OPN;
        _managerMap = new ConcurrentHashMap<>();

        _kaTimer = 0;
        _process = process;
    }

    /**
     * Gets the time of that latest keep-alive sent
     * @return Time of that latest keep-alive sent
     */
    public Date getLastKAlive() {
        return _lastKa;
    }

    /**
     * Sets the keep-alive timer value
     * @param kaTimer Keep-alive timer value (secs)
     */
    public void setKaTimer(short kaTimer) {
        _kaTimer = kaTimer;
    }

    /**
     * Gets the keep-alive timer value
     * @return Keep-alive timer value (secs)
     */
    public short getKaTimer() {
        return _kaTimer;
    }

    /**
     * Sets the accounting timer value
     * @param acctTimer Accounting timer value (secs)
     */
    public void setAccTimer(short acctTimer) {
        _acctTimer = acctTimer;
    }

    /**
     * Gets the accounting timer value
     * @return Accounting timer value (secs)
     */
    public short getAcctTimer() {
        return _acctTimer;
    }

    /**
     * Gets the latest COPS message
     * @return   Code of the latest message sent
     */
    public byte getLastMessage() {
        return _lastmessage;
    }

    /**
     * Gets the PEP-ID
     * @return   The ID of the PEP, as a <tt>String</tt>
     */
    public String getPepId() {
        return _pepId.getData().str();
    }

    /**
     * Checks whether the socket to the PEP is closed or not
     * @return   <tt>true</tt> if closed, <tt>false</tt> otherwise
     */
    public boolean isClosed() {
        return _sock.isClosed();
    }

    /**
     * Closes the socket to the PEP
     * @throws IOException
     */
    protected void close()
    throws IOException {
        _sock.close();
    }

    /**
     * Gets the socket to the PEP
     * @return   Socket connected to the PEP
     */
    public Socket getSocket() {
        return _sock;
    }

    /**
     * Main loop
     */
    public void run () {
        Date _lastSendKa = new Date();
        _lastRecKa = new Date();
        try {
            while (!_sock.isClosed()) {
                if (_sock.getInputStream().available() != 0) {
                    _lastmessage = processMessage(_sock);
                    _lastRecKa = new Date();
                }

                // Keep Alive
                if (_kaTimer > 0) {
                    // Timeout at PDP
                    int _startTime = (int) (_lastRecKa.getTime());
                    int cTime = (int) (new Date().getTime());

                    if ((cTime - _startTime) > _kaTimer * 1000) {
                        _sock.close();
                        // Notify all Request State Managers
                        notifyNoKAAllReqStateMan();
                    }

                    // Send to PEP
                    _startTime = (int) (_lastSendKa.getTime());
                    cTime = (int) (new Date().getTime());

                    if ((cTime - _startTime) > ((_kaTimer * 3/4) * 1000)) {
                        COPSHeader hdr = new COPSHeader(COPSHeader.COPS_OP_KA);
                        COPSKAMsg msg = new COPSKAMsg();

                        msg.add(hdr);

                        COPSTransceiver.sendMsg(msg, _sock);
                        _lastSendKa = new Date();
                    }
                }

                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    logger.error("Exception thrown while sleeping", e);
                }

            }
        } catch (Exception e) {
            logger.error("Error while processing socket messages", e);
        }

        // connection closed by server
        // COPSDebug.out(getClass().getName(),"Connection closed by client");
        try {
            _sock.close();
        } catch (IOException e) {
            logger.error("Error closing socket", e);
        }

        // Notify all Request State Managers
        try {
            notifyCloseAllReqStateMan();
        } catch (COPSPdpException e) {
            logger.error("Error closing state managers");
        }
    }

    /**
     * Gets a COPS message from the socket and processes it
     * @param    conn Socket connected to the PEP
     * @return Type of COPS message
     */
    private byte processMessage(Socket conn)
    throws COPSPdpException, COPSException, IOException {
        COPSMsg msg = COPSTransceiver.receiveMsg(conn);

        if (msg.getHeader().isAClientClose()) {
            handleClientCloseMsg(conn, msg);
            return COPSHeader.COPS_OP_CC;
        } else if (msg.getHeader().isAKeepAlive()) {
            handleKeepAliveMsg(conn, msg);
            return COPSHeader.COPS_OP_KA;
        } else if (msg.getHeader().isARequest()) {
            handleRequestMsg(conn, msg);
            return COPSHeader.COPS_OP_REQ;
        } else if (msg.getHeader().isAReport()) {
            handleReportMsg(conn, msg);
            return COPSHeader.COPS_OP_RPT;
        } else if (msg.getHeader().isADeleteReq()) {
            handleDeleteRequestMsg(conn, msg);
            return COPSHeader.COPS_OP_DRQ;
        } else if (msg.getHeader().isASyncComplete()) {
            handleSyncComplete(conn, msg);
            return COPSHeader.COPS_OP_SSC;
        } else {
            throw new COPSPdpException("Message not expected (" + msg.getHeader().getOpCode() + ").");
        }
    }

    /**
     * Handle Client Close Message, close the passed connection
     *
     * @param    conn                a  Socket
     * @param    msg                 a  COPSMsg
     *
     *
     * <Client-Close> ::= <Common Header>
     *                      <Error>
     *                      [<Integrity>]
     *
     * Not support [<Integrity>]
     *
     */
    private void handleClientCloseMsg(Socket conn, COPSMsg msg) {
        COPSClientCloseMsg cMsg = (COPSClientCloseMsg) msg;
        _error = cMsg.getError();

        // COPSDebug.out(getClass().getName(),"Got close request, closing connection " +
        //  conn.getInetAddress() + ":" + conn.getPort() + ":[Error " + _error.getDescription() + "]");

        try {
            // Support
            if (cMsg.getIntegrity() != null) {
                logger.warn("Unsupported objects (Integrity) to connection " + conn.getInetAddress());
            }

            conn.close();
        } catch (Exception unae) {
            logger.error("Unexpected exception closing connection", unae);
        }
    }

    /**
     * Gets the occurred COPS Error
     * @return   <tt>COPSError</tt> object
     */
    protected COPSError getError()  {
        return _error;
    }

    /**
     * Handle Keep Alive Message
     *
     * <Keep-Alive> ::= <Common Header>
     *                  [<Integrity>]
     *
     * Not support [<Integrity>]
     *
     * @param    conn                a  Socket
     * @param    msg                 a  COPSMsg
     *
     */
    private void handleKeepAliveMsg(Socket conn, COPSMsg msg) {
        COPSKAMsg cMsg = (COPSKAMsg) msg;

        COPSKAMsg kaMsg = (COPSKAMsg) msg;
        try {
            // Support
            if (cMsg.getIntegrity() != null) {
                logger.warn("Unsupported objects (Integrity) to connection " + conn.getInetAddress());
            }
            kaMsg.writeData(conn);
        } catch (Exception unae) {
            logger.error("Unexpected exception while writing COPS data", unae);
        }
    }

    /**
     * Handle Delete Request Message
     *
     * <Delete Request> ::= <Common Header>
     *                      <Client Handle>
     *                      <Reason>
     *                      [<Integrity>]
     *
     * Not support [<Integrity>]
     *
     * @param    conn                a  Socket
     * @param    msg                 a  COPSMsg
     *
     */
    private void handleDeleteRequestMsg(Socket conn, COPSMsg msg)
    throws COPSPdpException {
        COPSDeleteMsg cMsg = (COPSDeleteMsg) msg;
        // COPSDebug.out(getClass().getName(),"Removing ClientHandle for " +
        //  conn.getInetAddress() + ":" + conn.getPort() + ":[Reason " + cMsg.getReason().getDescription() + "]");

        // Support
        if (cMsg.getIntegrity() != null) {
            logger.warn("Unsupported objects (Integrity) to connection " + conn.getInetAddress());
        }

        // Delete clientHandler
        if (_managerMap.remove(cMsg.getClientHandle().getId().str()) == null) {
            // COPSDebug.out(getClass().getName(),"Missing for ClientHandle " +
            //  cMsg.getClientHandle().getId().getData());
        }

        COPSPdpReqStateMan man = _managerMap.get(cMsg.getClientHandle().getId().str());
        if (man == null) {
            logger.warn("No state manager found with ID - " + cMsg.getClientHandle().getId().str());
        } else {
            man.processDeleteRequestState(cMsg);
        }

    }

    /**
     * Handle Request Message
     *
     * <Request> ::= <Common Header>
     *                  <Client Handle>
     *                  <Context>
     *                  *(<Named ClientSI>)
     *                  [<Integrity>]
     * <Named ClientSI> ::= <*(<PRID> <EPD>)>
     *
     * Not support [<Integrity>]
     *
     * @param    conn                a  Socket
     * @param    msg                 a  COPSMsg
     *
     */
    private void handleRequestMsg(Socket conn, COPSMsg msg)
    throws COPSPdpException {

        COPSReqMsg reqMsg = (COPSReqMsg) msg;
        COPSContext cntxt = reqMsg.getContext();
        COPSHeader header = reqMsg.getHeader();
        //short reqType = cntxt.getRequestType();
        short cType   = header.getClientType();

        // Support
        if (reqMsg.getIntegrity() != null) {
            logger.warn("Unsupported objects (Integrity) to connection " + conn.getInetAddress());
        }

        COPSPdpReqStateMan man;
        man = (COPSPdpReqStateMan) _managerMap.get(reqMsg.getClientHandle().getId().str());
        if (man == null) {

            man = new COPSPdpReqStateMan(cType, reqMsg.getClientHandle().getId().str());
            _managerMap.put(reqMsg.getClientHandle().getId().str(),man);
            man.setDataProcess(_process);
            man.initRequestState(_sock);

            // COPSDebug.out(getClass().getName(),"createHandler called, clientType=" +
            //    header.getClientType() + " msgType=" +
            //    cntxt.getMessageType() + ", connId=" + conn.toString());
        }

        man.processRequest(reqMsg);
    }

    /**
     * Handle Report Message
     *
     * <Report State> ::= <Common Header>
     *                      <Client Handle>
     *                      <Report Type>
     *                      *(<Named ClientSI>)
     *                      [<Integrity>]
     *
     * Not support [<Integrity>]
     *
     * @param    conn                a  Socket
     * @param    msg                 a  COPSMsg
     *
     */
    private void handleReportMsg(Socket conn, COPSMsg msg)
    throws COPSPdpException {
        COPSReportMsg repMsg = (COPSReportMsg) msg;
        // COPSHandle handle = repMsg.getClientHandle();
        // COPSHeader header = repMsg.getHeader();

        // Support
        if (repMsg.getIntegrity() != null) {
            logger.warn("Unsupported objects (Integrity) to connection " + conn.getInetAddress());
        }

        COPSPdpReqStateMan man = (COPSPdpReqStateMan) _managerMap.get(repMsg.getClientHandle().getId().str());
        if (man == null) {
            logger.warn("No state manager found with ID - " + repMsg.getClientHandle().getId().str());
        } else {
            man.processReport(repMsg);
        }
    }

    /**
     * Method handleSyncComplete
     *
     * @param    conn                a  Socket
     * @param    msg                 a  COPSMsg
     *
     */
    private void handleSyncComplete(Socket conn, COPSMsg msg)
    throws COPSPdpException {
        COPSSyncStateMsg cMsg = (COPSSyncStateMsg) msg;
        // COPSHandle handle = cMsg.getClientHandle();
        // COPSHeader header = cMsg.getHeader();

        // Support
        if (cMsg.getIntegrity() != null) {
            logger.warn("Unsupported objects (Integrity) to connection " + conn.getInetAddress());
        }

        COPSPdpReqStateMan man = (COPSPdpReqStateMan) _managerMap.get(cMsg.getClientHandle().getId().str());
        if (man == null) {
            logger.warn("No state manager found with ID - " + cMsg.getClientHandle().getId().str());
        } else {
            man.processSyncComplete(cMsg);
        }
    }

    /**
     * Requests a COPS sync from the PEP
     * @throws COPSException
     * @throws COPSPdpException
     */
    protected void syncAllRequestState() throws COPSException, COPSPdpException {
        for (final COPSPdpReqStateMan man : _managerMap.values()) {
            man.syncRequestState();
        }
    }

    private void notifyCloseAllReqStateMan() throws COPSPdpException {
        for (final COPSPdpReqStateMan man : _managerMap.values()) {
            man.processClosedConnection(_error);
        }
    }

    private void notifyNoKAAllReqStateMan() throws COPSPdpException {
        for (final COPSPdpReqStateMan man : _managerMap.values()) {
            man.processNoKAConnection();
        }
    }

}

