package com.marginallyclever.gcodesender;

import com.marginallyclever.gcodesender.SerialConnection;

public interface SerialConnectionReadyListener {
	/**
	 * gcode line number error or transmission error
	 * @param arg0
	 * @param lineNumber
	 */
	public void lineError(SerialConnection arg0,int lineNumber);
	/**
	 * connection ready to transmit more data
	 * @param arg0
	 */
	public void connectionReady(SerialConnection arg0);
	/**
	 * data has arrived via this connection
	 * @param arg0 where data came from
	 * @param data the newly arrived data
	 */
	public void dataAvailable(SerialConnection arg0,String data);
}
