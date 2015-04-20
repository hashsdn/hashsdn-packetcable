package org.umu.cops.ospdp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umu.cops.stack.*;
import org.umu.cops.stack.COPSError.ErrorTypes;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core PDP agent for outsourcing.
 */
public class COPSPdpOSAgent extends Thread {

    public final static Logger logger = LoggerFactory.getLogger(COPSPdpOSAgent.class);

    /** Well-known port for COPS */
    public static final int WELL_KNOWN_PDP_PORT = 3288;
    /** Default keep-alive timer value (secs) */
    public static final short KA_TIMER_VALUE = 30;
    /** Default accounting timer value (secs) */
    public static final short ACCT_TIMER_VALUE = 0;

    /**
        PDP host port
     */
    private int _serverPort;

    /**
        Client-type of connecting PEP
     */
    private short _clientType;

    /**
        Accounting timer (secs)
     */
    private short _acctTimer;

    /**
        Keep-alive timer (secs)
     */
    private short _kaTimer;

    /**
        Maps a PEP-ID to a connection
     */
    private final Map<String, COPSPdpOSConnection> _connectionMap;
    // map < String(PEPID), COPSPdpOSConnection > ConnectionMap;

    /**
     *  Policy data processing object
     */
    private COPSPdpOSDataProcess _process;

    /**
     * Creates a PDP Agent
     *
     * @param clientType    COPS Client-type
     * @param process       Object to perform policy data processing
     */
    public COPSPdpOSAgent(short clientType, COPSPdpOSDataProcess process) {
        _serverPort = WELL_KNOWN_PDP_PORT;
        _kaTimer = KA_TIMER_VALUE;
        _acctTimer = ACCT_TIMER_VALUE;

        _clientType = clientType;
        _connectionMap = new ConcurrentHashMap<>();
        _process = process;
    }

    /**
     * Creates a PDP Agent
     *
     * @param port  Port to listen to
     * @param clientType    COPS Client-type
     * @param process   Object to perform policy data processing
     */
    public COPSPdpOSAgent(int port, short clientType, COPSPdpOSDataProcess process) {
        _serverPort = port;

        _kaTimer = KA_TIMER_VALUE;
        _acctTimer = ACCT_TIMER_VALUE;

        _clientType = clientType;
        _connectionMap = new ConcurrentHashMap<>();
        _process = process;
    }

    /**
     * Sets the keep-alive timer value
     * @param    kaTimer    Keep alive timer value (secs)
     */
    public void setKaTimer (short kaTimer) {
        _kaTimer = kaTimer;
    }

    /**
     * Sets the accounting timer value
     * @param    acctTimer  Accounting timer value (secs)
     */
    public void setAcctTimer (short acctTimer) {
        _acctTimer = acctTimer;
    }

    /**
     * Gets the value of the keep-alive timer
     * @return   Keep-alive timer value (secs)
     */
    public short getKaTimer () {
        return _kaTimer;
    }

    /**
     * Gets the accounting timer value
     * @return   Accounting timer value (secs)
     */
    public short getAcctTimer () {
        return _acctTimer;
    }

    /**
     * Gets the client-type
     * @return   The client-type
     */
    public short getClientType() {
        return _clientType;
    }

    /**
     * Disconnects a PEP
     * @param pepID PEP-ID of the PEP to be disconnected
     * @param error COPS Error to be reported as a reason
     * @throws COPSException
     * @throws IOException
     */
    public void disconnect (String pepID, COPSError error) throws COPSException, IOException {
        COPSPdpOSConnection pdpConn = _connectionMap.get(pepID);

        COPSHeader cHdr = new COPSHeader(COPSHeader.COPS_OP_CC, _clientType);
        COPSClientCloseMsg closeMsg = new COPSClientCloseMsg();
        closeMsg.add(cHdr);
        if (error != null)
            closeMsg.add(error);

        closeMsg.writeData(pdpConn.getSocket());
        pdpConn.close();
    }

    /**
     * Requests a COPS sync for a PEP
     * @param pepID PEP-ID of the PEP to be synced
     * @throws COPSException
     * @throws COPSPdpException
     */
    public void sync(String pepID) throws COPSException, COPSPdpException {
        COPSPdpOSConnection pdpConn = _connectionMap.get(pepID);
        pdpConn.syncAllRequestState();
    }

    /**
     * Removes a PEP from the connection map
     * @param pepID PEP-ID of the PEP to be removed
     */
    public void delete (String pepID) {
        _connectionMap.remove(pepID);
    }

    /**
     * Runs the PDP process
     */
    public void run() {
        try {
            final ServerSocket serverSocket = new ServerSocket (_serverPort);

            //Loop through for Incoming messages

            // server infinite loop
            while (true) {
                // Wait for an incoming connection from a PEP
                Socket socket = serverSocket.accept();

                // COPSDebug.out(getClass().getName(),"New connection accepted " +
                //           socket.getInetAddress() +
                //           ":" + socket.getPort());

                // We're waiting for an OPN message
                try {
                    COPSMsg msg = COPSTransceiver.receiveMsg(socket);
                    if (msg.getHeader().isAClientOpen()) {
                        handleClientOpenMsg(socket, msg);
                    } else {
                        // COPSDebug.err(getClass().getName(), COPSDebug.ERROR_NOEXPECTEDMSG);
                        try {
                            socket.close();
                        } catch (Exception ex) {
                            logger.error("Error closing socket", ex);
                        }
                    }
                } catch (Exception e) { // COPSException, IOException
                    // COPSDebug.err(getClass().getName(), COPSDebug.ERROR_EXCEPTION,
                    //    "(" + socket.getInetAddress() + ":" + socket.getPort() + ")", e);
                    try {
                        socket.close();
                    } catch (Exception ex) {
                        logger.error("Error closing socket", ex);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error processing socket messages", e);
        }
    }

    /**
      * Handles a COPS client-open message
      * @param    conn Socket to the PEP
      * @param    msg <tt>COPSMsg</tt> holding the client-open message
      * @throws COPSException
      * @throws IOException
      */
    private void handleClientOpenMsg(Socket conn, COPSMsg msg) throws COPSException, IOException {
        COPSClientOpenMsg cMsg = (COPSClientOpenMsg) msg;
        COPSPepId pepId = cMsg.getPepId();

        // Validate Client Type
        if (msg.getHeader().getClientType() != _clientType) {
            // Unsupported client type
            COPSHeader cHdr = new COPSHeader(COPSHeader.COPS_OP_CC, msg.getHeader().getClientType());
            COPSError err = new COPSError(ErrorTypes.UNSUPPORTED_CLIENT_TYPE, ErrorTypes.NA);
            COPSClientCloseMsg closeMsg = new COPSClientCloseMsg();
            closeMsg.add(cHdr);
            closeMsg.add(err);
            try {
                closeMsg.writeData(conn);
            } catch (IOException unae) {
                logger.error("Error writing data", unae);
            }

            throw new COPSException("Unsupported client type");
        }

        // PEPId is mandatory
        if (pepId == null) {
            // Mandatory COPS object missing
            COPSHeader cHdr = new COPSHeader(COPSHeader.COPS_OP_CC, msg.getHeader().getClientType());
            COPSError err = new COPSError(ErrorTypes.MANDATORY_OBJECT_MISSING, ErrorTypes.NA);
            COPSClientCloseMsg closeMsg = new COPSClientCloseMsg();
            closeMsg.add(cHdr);
            closeMsg.add(err);
            try {
                closeMsg.writeData(conn);
            } catch (IOException unae) {
                logger.error("Error writing data", unae);
            }

            throw new COPSException("Mandatory COPS object missing (PEPId)");
        }

        // Support
        if ( (cMsg.getClientSI() != null) ||
                (cMsg.getPdpAddress() != null) ||
                (cMsg.getIntegrity() != null)) {

            // Unsupported objects
            COPSHeader cHdr = new COPSHeader(COPSHeader.COPS_OP_CC, msg.getHeader().getClientType());
            COPSError err = new COPSError(ErrorTypes.UNKNOWN_OBJECT, ErrorTypes.NA);
            COPSClientCloseMsg closeMsg = new COPSClientCloseMsg();
            closeMsg.add(cHdr);
            closeMsg.add(err);
            try {
                closeMsg.writeData(conn);
            } catch (IOException unae) {
                logger.error("Error writing data", unae);
            }

            throw new COPSException("Unsupported objects (ClientSI, PdpAddress, Integrity)");
        }

        // Connection accepted
        COPSHeader ahdr = new COPSHeader(COPSHeader.COPS_OP_CAT, msg.getHeader().getClientType());
        COPSKATimer katimer = new COPSKATimer(_kaTimer);
        COPSAcctTimer acctTimer = new COPSAcctTimer(_acctTimer);
        COPSClientAcceptMsg acceptMsg = new COPSClientAcceptMsg();
        acceptMsg.add(ahdr);
        acceptMsg.add(katimer) ;
        if (_acctTimer != 0) acceptMsg.add(acctTimer);
        acceptMsg.writeData(conn);

        COPSPdpOSConnection pdpConn = new COPSPdpOSConnection(pepId, conn, _process);
        pdpConn.setKaTimer(_kaTimer);
        if (_acctTimer != 0) pdpConn.setAccTimer(_acctTimer);
        new Thread(pdpConn).start();
        _connectionMap.put(pepId.getData().str(),pdpConn);
    }
}
